package com.vikash.mp3player.data

import android.net.Uri
import com.vikash.mp3player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Online search and streaming from Jamendo, a catalog of Creative Commons music whose
 * artists allow free streaming and (per track) downloading. API docs:
 * https://developer.jamendo.com/v3.0/tracks
 */
class JamendoRepository {

    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = BuildConfig.JAMENDO_CLIENT_ID.isNotBlank()

    /** Empty [query] returns the most popular tracks this week. */
    suspend fun search(query: String, limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        check(isConfigured) { "Add JAMENDO_CLIENT_ID to local.properties to enable online music." }
        val params = buildMap {
            put("client_id", BuildConfig.JAMENDO_CLIENT_ID)
            put("format", "json")
            put("limit", limit.toString())
            put("audioformat", "mp32")
            put("include", "licenses")
            if (query.isBlank()) put("order", "popularity_week") else put("search", query.trim())
        }
        val qs = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val body = httpGet("https://api.jamendo.com/v3.0/tracks/?$qs")
        val response = json.decodeFromString<JamendoResponse>(body)
        if (response.headers.status != "success") {
            error(response.headers.errorMessage.ifBlank { "Search failed" })
        }
        response.results.map { it.toSong() }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} from music catalog")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun JamendoTrack.toSong() = Song(
        id = "jamendo:$id",
        uri = Uri.parse(audio),
        title = name,
        artist = artistName,
        album = albumName,
        durationMs = duration * 1000L,
        artworkUri = image.takeIf { it.isNotBlank() }?.let(Uri::parse),
        isOnline = true,
        downloadUrl = audiodownload.takeIf { audiodownloadAllowed && it.isNotBlank() },
    )
}

@Serializable
private data class JamendoResponse(
    val headers: JamendoHeaders,
    val results: List<JamendoTrack> = emptyList(),
)

@Serializable
private data class JamendoHeaders(
    val status: String,
    @SerialName("error_message") val errorMessage: String = "",
)

@Serializable
private data class JamendoTrack(
    val id: String,
    val name: String,
    val duration: Int = 0,
    @SerialName("artist_name") val artistName: String = "",
    @SerialName("album_name") val albumName: String = "",
    val image: String = "",
    val audio: String,
    val audiodownload: String = "",
    @SerialName("audiodownload_allowed") val audiodownloadAllowed: Boolean = false,
)
