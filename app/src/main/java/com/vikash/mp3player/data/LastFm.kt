package com.vikash.mp3player.data

import android.content.Context
import com.vikash.mp3player.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Last.fm scrobbling (free). The person signs in once with their Last.fm name and password;
 * only the session key Last.fm sends back is kept, never the password.
 *
 * Rules follow Last.fm's: a song counts when it is longer than 30 seconds and was heard for half
 * its length or 4 minutes, whichever comes first. Plays made offline wait in a small queue file
 * and are sent (up to 50 at a time) the next time a send works.
 *
 * Needs the LASTFM_API_KEY and LASTFM_API_SECRET build values (GitHub secrets). Without them
 * [configured] is false and the Settings card says it is coming soon.
 */
object LastFm {
    data class State(
        val configured: Boolean = false,
        val user: String? = null,
        val enabled: Boolean = true,
        val pending: Int = 0,
        val sentToday: Int = 0,
    )

    private const val API = "https://ws.audioscrobbler.com/2.0/"
    private const val PREFS = "lastfm"
    private const val QUEUE_FILE = "lastfm_queue.json"
    private const val MAX_QUEUE = 2000

    val configured: Boolean get() = BuildConfig.LASTFM_API_KEY.isNotBlank() && BuildConfig.LASTFM_API_SECRET.isNotBlank()

    private val _state = MutableStateFlow(State(configured = configured))
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueLock = Mutex()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _state.value = State(
            configured = configured,
            user = p.getString("user", null),
            enabled = p.getBoolean("enabled", true),
            pending = readQueue(context).length(),
            sentToday = sentToday(p),
        )
    }

    /** Signs in with the Last.fm name and password. Returns null on success, or Last.fm's error text. */
    suspend fun signIn(context: Context, user: String, password: String): String? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext "Last.fm is not set up in this build."
        val result = runCatching {
            call(mapOf("method" to "auth.getMobileSession", "username" to user.trim(), "password" to password))
        }.getOrElse { return@withContext it.message ?: "No connection" }
        val error = result.optString("message").takeIf { result.has("error") }
        if (error != null) return@withContext error
        val session = result.optJSONObject("session") ?: return@withContext "Unexpected answer from Last.fm"
        prefs(context).edit()
            .putString("key", session.getString("key"))
            .putString("user", session.optString("name", user.trim()))
            .apply()
        init(context)
        _state.value = _state.value.copy(user = session.optString("name", user.trim()))
        flush(context)
        null
    }

    fun signOut(context: Context) {
        prefs(context).edit().remove("key").remove("user").apply()
        _state.value = _state.value.copy(user = null)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
        _state.value = _state.value.copy(enabled = enabled)
    }

    private fun active(context: Context): String? {
        if (!configured || !_state.value.enabled) return null
        return prefs(context).getString("key", null)
    }

    /** Shows the song as "now playing" on the person's profile. Fire and forget. */
    fun nowPlaying(context: Context, title: String, artist: String, album: String?, durationMs: Long) {
        init(context)
        val key = active(context) ?: return
        if (title.isBlank() || !isRealArtist(artist)) return
        scope.launch {
            runCatching {
                call(
                    buildMap {
                        put("method", "track.updateNowPlaying")
                        put("track", title); put("artist", artist); put("sk", key)
                        if (!album.isNullOrBlank()) put("album", album)
                        if (durationMs > 0) put("duration", (durationMs / 1000).toString())
                    },
                )
            }
        }
    }

    /** Called when a song stops being the current one, with how long it was actually heard. */
    fun finished(context: Context, title: String, artist: String, album: String?, heardMs: Long, durationMs: Long) {
        init(context)
        if (active(context) == null) return
        if (title.isBlank() || !isRealArtist(artist)) return
        if (durationMs in 1..30_000) return
        val needed = if (durationMs > 0) minOf(durationMs / 2, 240_000L) else 240_000L
        if (heardMs < needed) return
        val entry = JSONObject()
            .put("track", title).put("artist", artist).put("album", album.orEmpty())
            .put("ts", (System.currentTimeMillis() - heardMs) / 1000)
            .put("duration", durationMs / 1000)
        scope.launch {
            queueLock.withLock {
                val q = readQueue(context)
                q.put(entry)
                // Keep the newest plays if someone stays offline for weeks.
                val trimmed = if (q.length() > MAX_QUEUE) JSONArray((q.length() - MAX_QUEUE until q.length()).map { q.get(it) }) else q
                writeQueue(context, trimmed)
                _state.value = _state.value.copy(pending = trimmed.length())
            }
            flush(context)
        }
    }

    /** Sends waiting plays, 50 per request (Last.fm's limit). Leaves them queued if offline. */
    fun flush(context: Context) {
        val key = active(context) ?: return
        scope.launch {
            queueLock.withLock {
                var q = readQueue(context)
                while (q.length() > 0) {
                    val batch = (0 until minOf(50, q.length())).map { q.getJSONObject(it) }
                    val params = mutableMapOf("method" to "track.scrobble", "sk" to key)
                    batch.forEachIndexed { i, e ->
                        params["track[$i]"] = e.getString("track")
                        params["artist[$i]"] = e.getString("artist")
                        params["timestamp[$i]"] = e.getLong("ts").toString()
                        e.optString("album").takeIf { it.isNotBlank() }?.let { params["album[$i]"] = it }
                        e.optLong("duration").takeIf { it > 0 }?.let { params["duration[$i]"] = it.toString() }
                    }
                    val result = runCatching { call(params) }.getOrNull() ?: break // offline: try later
                    if (result.has("error")) {
                        val code = result.optInt("error")
                        // 9 = session revoked on last.fm: sign out so the card asks again.
                        if (code == 9) { signOut(context); break }
                        // 11/16 = Last.fm busy: try later. Anything else: the batch is bad, drop it.
                        if (code == 11 || code == 16) break
                    } else {
                        val p = prefs(context)
                        val today = dayStamp()
                        val sent = (if (p.getInt("day", 0) == today) p.getInt("sent", 0) else 0) + batch.size
                        p.edit().putInt("day", today).putInt("sent", sent).apply()
                        _state.value = _state.value.copy(sentToday = sent)
                    }
                    q = JSONArray((batch.size until q.length()).map { q.get(it) })
                    writeQueue(context, q)
                    _state.value = _state.value.copy(pending = q.length())
                }
            }
        }
    }

    private fun isRealArtist(artist: String): Boolean {
        val a = artist.trim().lowercase()
        return a.isNotEmpty() && a != "<unknown>" && a != "unknown" && a != "unknown artist"
    }

    private fun call(params: Map<String, String>): JSONObject {
        val all = params + mapOf("api_key" to BuildConfig.LASTFM_API_KEY)
        val sig = md5(all.toSortedMap().entries.joinToString("") { it.key + it.value } + BuildConfig.LASTFM_API_SECRET)
        val body = (all + mapOf("api_sig" to sig, "format" to "json")).entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val conn = URL(API).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.setRequestProperty("User-Agent", "Ampli/${BuildConfig.VERSION_NAME}")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return if (text.isBlank()) throw java.io.IOException("HTTP ${conn.responseCode}") else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun dayStamp(): Int {
        val c = java.util.Calendar.getInstance()
        return c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun sentToday(p: android.content.SharedPreferences) = if (p.getInt("day", 0) == dayStamp()) p.getInt("sent", 0) else 0

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun queueFile(context: Context) = File(context.filesDir, QUEUE_FILE)

    private fun readQueue(context: Context): JSONArray =
        runCatching { JSONArray(queueFile(context).readText()) }.getOrDefault(JSONArray())

    private fun writeQueue(context: Context, q: JSONArray) {
        runCatching { queueFile(context).writeText(q.toString()) }
    }
}
