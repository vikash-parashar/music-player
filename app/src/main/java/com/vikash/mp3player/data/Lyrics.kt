package com.vikash.mp3player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
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
 * Lyrics come only from the user: a .lrc or text file they pick or paste, or lyrics already
 * embedded in their MP3. Ampli never downloads lyrics (song lyrics are copyrighted).
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
                val bytes = input.readBytes().takeIf { it.size < 512 * 1024 } ?: return@use null
                bytes.decodeToString().removePrefix("﻿")
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
