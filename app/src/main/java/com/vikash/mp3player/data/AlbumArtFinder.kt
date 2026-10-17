package com.vikash.mp3player.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.vikash.mp3player.BuildConfig
import com.vikash.mp3player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * "Find missing album art": looks an album up on MusicBrainz and downloads its front cover from
 * the Cover Art Archive, only when the user taps it (never automatically).
 *
 * Only the album and artist names leave the phone. Found covers are saved in app storage
 * (filesDir/albumart/<albumId>.jpg), never written into the user's music files, and are shown
 * only for albums that have no art of their own (see [SavedAlbumArtInterceptor]).
 */
object AlbumArtFinder {

    enum class Outcome { Found, NotFound, Failed }

    /** Progress of the Settings walk over every album with no art. */
    data class Batch(val done: Int, val total: Int, val found: Int, val running: Boolean)

    private const val DIR = "albumart"
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MIN_SCORE = 90
    private const val MB_INTERVAL_MS = 1_100L
    private const val PREFS = "ampli_prefs"
    private const val KEY_CONSENT = "album_art_consent"
    private const val ALBUM_ART_PREFIX = "content://media/external/audio/albumart/"

    private val userAgent get() = "AmpliMP3/${BuildConfig.VERSION_NAME} ( support@ampliapps.in )"
    private val json = Json { ignoreUnknownKeys = true }

    /** MusicBrainz allows one request per second per app; every lookup goes through this gate. */
    private val mbGate = Mutex()
    private var lastMbRequestMs = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batchJob: Job? = null

    private val _batch = MutableStateFlow<Batch?>(null)
    val batch: StateFlow<Batch?> = _batch.asStateFlow()

    /** Bumped whenever a cover is saved so images on screen load again and pick it up. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun hasConsent(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CONSENT, false)

    fun setConsent(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CONSENT, true).apply()
    }

    /** MediaStore album id from a local song's art uri (content://media/external/audio/albumart/<id>). */
    fun albumIdOf(song: Song): Long? = albumIdOf(song.artworkUri)

    private fun albumIdOf(uri: Uri?): Long? {
        val s = uri?.toString() ?: return null
        if (!s.startsWith(ALBUM_ART_PREFIX)) return null
        return s.removePrefix(ALBUM_ART_PREFIX).toLongOrNull()
    }

    fun savedFile(context: Context, albumId: Long): File = File(File(context.filesDir, DIR), "$albumId.jpg")

    /** The saved cover for an album art uri, if this app found one; null for anything else. */
    fun fallbackFor(context: Context, data: Any?): File? {
        val albumId = albumIdOf(data as? Uri ?: (data as? String)?.let(Uri::parse)) ?: return null
        return savedFile(context, albumId).takeIf { it.isFile && it.length() > 0 }
    }

    /** True when Android has real (embedded or MediaStore) art for the album. Call off the main thread. */
    fun hasRealArt(context: Context, song: Song): Boolean {
        val uri = song.artworkUri ?: return false
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize != 0L } ?: false
        }.getOrDefault(false)
    }

    /** One album: tries MusicBrainz, then the Cover Art Archive. Quiet on errors and no result. */
    suspend fun find(context: Context, albumId: Long, album: String, artist: String): Outcome = withContext(Dispatchers.IO) {
        if (album.isBlank() || album == UNKNOWN_ALBUM) return@withContext Outcome.NotFound
        // Songs with no artist tag carry the translated "Unknown artist" label; search by album alone then.
        val realArtist = artist.takeUnless { it == context.getString(R.string.library_unknown_artist) }.orEmpty()
        val releases = searchReleases(album, realArtist) ?: return@withContext Outcome.Failed
        if (releases.isEmpty()) return@withContext Outcome.NotFound
        for (mbid in releases.take(3)) {
            val bytes = download("https://coverartarchive.org/release/$mbid/front-500") ?: continue
            if (save(context, albumId, bytes)) {
                _version.value += 1
                return@withContext Outcome.Found
            }
        }
        Outcome.NotFound
    }

    /**
     * Settings walk: every local album without real or saved art, one MusicBrainz request a
     * second at most. Runs on its own scope so leaving the screen doesn't stop it; [cancelBatch] does.
     */
    fun startBatch(context: Context, songs: List<Song>) {
        if (batchJob?.isActive == true) return
        val app = context.applicationContext
        _batch.value = Batch(0, 0, 0, running = true)
        batchJob = scope.launch {
            val albums = songs.asSequence()
                .filter { !it.isOnline && it.album.isNotBlank() && it.album != UNKNOWN_ALBUM }
                .groupBy { albumIdOf(it) }
                .mapNotNull { (id, list) -> if (id == null) null else id to list }
                .filter { (id, list) -> !savedFile(app, id).isFile && !hasRealArt(app, list.first()) }
            var found = 0
            _batch.value = Batch(0, albums.size, 0, running = true)
            albums.forEachIndexed { i, (id, list) ->
                if (!isActive) return@launch
                val artist = list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
                if (find(app, id, list.first().album, artist) == Outcome.Found) found++
                _batch.value = Batch(i + 1, albums.size, found, running = true)
            }
            _batch.value = Batch(albums.size, albums.size, found, running = false)
        }.also { job ->
            job.invokeOnCompletion { cause ->
                if (cause != null) _batch.value = _batch.value?.copy(running = false)
            }
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
    }

    /** Release MBIDs scoring at least [MIN_SCORE], best first; null when MusicBrainz could not be reached. */
    private suspend fun searchReleases(album: String, artist: String): List<String>? {
        val query = buildString {
            append("release:\"").append(escape(album)).append('"')
            if (artist.isNotBlank()) append(" AND artist:\"").append(escape(artist)).append('"')
        }
        val url = "https://musicbrainz.org/ws/2/release/?query=${URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=5"
        val body = mbGate.withLock {
            val wait = lastMbRequestMs + MB_INTERVAL_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                getText(url)
            } finally {
                lastMbRequestMs = System.currentTimeMillis()
            }
        } ?: return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject["releases"]?.jsonArray.orEmpty()
                .mapNotNull { el ->
                    val o = el.jsonObject
                    val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val score = o["score"]?.jsonPrimitive?.intOrNull ?: 0
                    if (score >= MIN_SCORE) id to score else null
                }
                .sortedByDescending { it.second }
                .map { it.first }
        }.getOrNull()
    }

    /** Lucene query syntax: quotes and backslashes inside a phrase must be escaped. */
    private fun escape(s: String) = s.trim().replace("\\", "\\\\").replace("\"", "\\\"")

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json, image/*")
        }

    /** Body of a 2xx response, or null on 503 (rate limited), any other error or no network. */
    private fun getText(url: String): String? {
        val conn = runCatching { open(url) }.getOrNull() ?: return null
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Image bytes, or null on 404 (no cover), errors, or anything larger than 2 MB. */
    private fun download(url: String): ByteArray? {
        val conn = runCatching { open(url) }.getOrNull() ?: return null
        return try {
            if (conn.responseCode !in 200..299) return null
            val declared = conn.contentLengthLong
            if (declared > MAX_BYTES) return null
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return null
                }
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Keeps only real images; writes via a temp file so a half-written cover is never shown. */
    private fun save(context: Context, albumId: Long, bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        return runCatching {
            val target = savedFile(context, albumId)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "$albumId.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        }.getOrDefault(false)
    }
}
