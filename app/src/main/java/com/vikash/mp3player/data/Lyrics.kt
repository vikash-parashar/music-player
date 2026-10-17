package com.vikash.mp3player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One lyrics line. [timeMs] is null for lyrics without timestamps. */
data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val fromUser: Boolean) {
    val synced get() = lines.any { it.timeMs != null }
}

/**
 * Lyrics come only from the user: a .lrc or text file they pick or paste, a .lrc with the same
 * name as the song (next to it, or in a lyrics folder they chose), or lyrics already embedded in
 * their MP3. Ampli never downloads lyrics (song lyrics are copyrighted).
 */
object LyricsRepository {

    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val META = Regex("""^\[(ar|ti|al|by|au|length|re|ve|offset|#):.*]$""", RegexOption.IGNORE_CASE)

    suspend fun load(context: Context, songId: String): Lyrics? = withContext(Dispatchers.IO) {
        userFile(context, songId).takeIf { it.exists() }?.let { f ->
            parse(f.readText()).takeIf { it.isNotEmpty() }?.let { return@withContext Lyrics(it, fromUser = true) }
        }
        val uri = localUri(songId) ?: return@withContext null
        sidecar(context, uri)?.let { parse(it) }?.takeIf { it.isNotEmpty() }?.let { return@withContext Lyrics(it, fromUser = false) }
        val embedded = runCatching {
            context.contentResolver.openInputStream(uri)?.use { Id3Editor.readLyrics(it.buffered()) }
        }.getOrNull() ?: return@withContext null
        parse(embedded).takeIf { it.isNotEmpty() }?.let { Lyrics(it, fromUser = false) }
    }

    /** Saves lyrics the user picked or pasted for this song. Returns false if nothing usable was given. */
    suspend fun save(context: Context, songId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        if (parse(text).isEmpty()) return@withContext false
        userFile(context, songId).apply { parentFile?.mkdirs() }.writeText(text)
        true
    }

    suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // Lyrics files are small; refuse anything big that's clearly not text.
                readCapped(input)?.let(::decode)
            }
        }.getOrNull()
    }

    suspend fun remove(context: Context, songId: String) = withContext(Dispatchers.IO) {
        userFile(context, songId).delete()
    }

    /** Parses LRC ("[01:02.50] words", several tags per line allowed) or plain text. */
    fun parse(text: String): List<LyricLine> {
        val offset = OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        text.lineSequence().map { it.trim() }.forEach { line ->
            if (line.isEmpty() || META.matches(line)) return@forEach
            val stamps = TIME.findAll(line).toList()
            if (stamps.isEmpty()) {
                plain += LyricLine(null, line)
            } else {
                val words = line.substring(stamps.last().range.last + 1).trim()
                stamps.forEach { m ->
                    val (min, sec, frac) = m.destructured
                    val fracMs = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                    val ms = min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs - offset
                    timed += LyricLine(ms.coerceAtLeast(0), words)
                }
            }
        }
        return if (timed.isNotEmpty()) timed.sortedBy { it.timeMs } else plain
    }

    // ------------------------------------------------------------------ .lrc next to the song

    private const val PREFS = "lyrics"
    private const val KEY_TREE = "folder_tree"
    private const val MAX_INDEX = 5000

    /** Lowercase "song name.lrc" -> document uri, for the chosen lyrics folder. Built once, on first use. */
    @Volatile private var index: Map<String, Uri>? = null
    @Volatile private var indexAt = 0L

    /**
     * Android 11+ (scoped storage) lets a music app read songs but not the .lrc files beside them, so there the user
     * picks a lyrics folder once (usually their Music folder) and Ampli looks inside it by name.
     */
    val folderNeeded: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun lyricsFolder(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TREE, null)?.let(Uri::parse)
            ?.takeIf { tree -> context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission } }

    fun setLyricsFolder(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        runCatching { resolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        // Only one folder is kept; let go of the old one.
        lyricsFolder(context)?.takeIf { it != tree }?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(old, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TREE, tree.toString()).apply()
        index = null
        indexAt = 0L
    }

    /** The text of "<song file name>.lrc", from beside the song (Android 12 and older) or from the lyrics folder. */
    private fun sidecar(context: Context, song: Uri): String? {
        val (path, displayName) = runCatching {
            context.contentResolver.query(song, arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
            }
        }.getOrNull() ?: (null to null)
        val name = displayName ?: path?.substringAfterLast('/') ?: return null
        val base = name.substringBeforeLast('.', name)

        if (path != null) {
            val dir = File(path).parentFile
            for (ext in listOf(".lrc", ".LRC")) {
                val f = File(dir, base + ext)
                val text = runCatching { f.takeIf { it.canRead() && it.length() in 1 until 512 * 1024 }?.readBytes()?.let(::decode) }.getOrNull()
                if (text != null) return text
            }
        }
        val tree = lyricsFolder(context) ?: return null
        val key = "${base.lowercase()}.lrc"
        var found = index?.get(key)
        // Not found: the user may have added files since; look again, at most once a minute.
        if (found == null && System.currentTimeMillis() - indexAt > 60_000) {
            index = buildIndex(context, tree)
            indexAt = System.currentTimeMillis()
            found = index?.get(key)
        }
        val doc = found ?: return null
        return runCatching {
            context.contentResolver.openInputStream(doc)?.use { input ->
                readCapped(input)?.let(::decode)
            }
        }.getOrNull()
    }

    /** Walks the lyrics folder (and its subfolders) once, keeping the .lrc files by name. */
    private fun buildIndex(context: Context, tree: Uri): Map<String, Uri> {
        val out = HashMap<String, Uri>()
        val resolver = context.contentResolver
        val pending = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(tree)) }
        var seen = 0
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (pending.isNotEmpty() && seen < MAX_INDEX) {
            val parent = pending.removeFirst()
            runCatching {
                resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent), cols, null, null, null)?.use { c ->
                    // Only folders and .lrc files count toward the cap; a big Music folder's songs don't.
                    while (c.moveToNext() && seen < MAX_INDEX) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".")) { pending.add(id); seen++ }
                        } else if (name.endsWith(".lrc", ignoreCase = true)) {
                            seen++
                            out.putIfAbsent(name.lowercase(), DocumentsContract.buildDocumentUriUsingTree(tree, id))
                        }
                    }
                }
            }
        }
        return out
    }

    /** Lyrics files are small: reads at most 512 KB + 1 byte, and gives up on anything bigger (clearly not lyrics). */
    private fun readCapped(input: java.io.InputStream): ByteArray? {
        val limit = 512 * 1024
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (buf.size() <= limit) {
            val n = input.read(chunk, 0, minOf(chunk.size, limit + 1 - buf.size()))
            if (n < 0) break
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray().takeIf { it.size <= limit }
    }

    private fun decode(bytes: ByteArray) = bytes.decodeToString().removePrefix("\uFEFF")

    private fun userFile(context: Context, songId: String) =
        File(File(context.filesDir, "lyrics"), songId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".lrc")

    private fun localUri(songId: String): Uri? {
        val id = songId.removePrefix("local:").takeIf { songId.startsWith("local:") }?.toLongOrNull() ?: return null
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(collection, id)
    }
}
