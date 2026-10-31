package com.vikash.mp3player.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Media ids of radio stations start with this; HLS stations with [RADIO_HLS_PREFIX]. */
const val RADIO_PREFIX = "radio:"
const val RADIO_HLS_PREFIX = "radio:hls:"

/** One live station from radio-browser.info, the free community station directory. */
@Serializable
data class RadioStation(
    @SerialName("stationuuid") val id: String = "",
    val name: String = "",
    @SerialName("url_resolved") val url: String = "",
    val favicon: String = "",
    val tags: String = "",
    @SerialName("countrycode") val country: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Int = 0,
) {
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun toSong(): Song = Song(
        id = (if (hls == 1) RADIO_HLS_PREFIX else RADIO_PREFIX) + id,
        uri = url.toUri(),
        title = name.trim(),
        artist = listOf(country, tagList.take(2).joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Live radio" },
        album = "Live radio",
        durationMs = 0,
        artworkUri = favicon.takeIf { it.startsWith("http") }?.let(Uri::parse),
        isOnline = true,
    )
}

/**
 * Internet radio. Station lists come from the radio-browser.info API (public domain data, no
 * key); audio streams straight from each broadcaster. Favourite stations stay on the phone.
 */
object RadioRepository {
    private const val PREFS = "radio"
    private const val KEY_FAVS = "favourites"
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // The API asks apps to spread load over its mirrors; these are the long-running ones.
    private val mirrors = listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info", "fi1.api.radio-browser.info")
    @Volatile private var mirror = mirrors.random()

    /** Genre chips under the search box. */
    val genres = listOf("bollywood", "hindi", "pop", "lofi", "news", "classical", "devotional", "jazz", "rock", "edm", "talk", "kids")

    private val _favourites = MutableStateFlow<List<RadioStation>>(emptyList())
    val favourites: StateFlow<List<RadioStation>> = _favourites.asStateFlow()
    @Volatile private var loaded = false

    fun loadFavourites(context: Context) {
        if (loaded) return
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FAVS, null)
        _favourites.value = raw?.let { runCatching { json.decodeFromString<List<RadioStation>>(it) }.getOrNull() }.orEmpty()
        loaded = true
    }

    fun toggleFavourite(context: Context, station: RadioStation) {
        loadFavourites(context)
        val now = _favourites.value
        _favourites.value = if (now.any { it.id == station.id }) now.filterNot { it.id == station.id } else listOf(station) + now
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAVS, json.encodeToString(_favourites.value)).apply()
    }

    /** Most-listened stations in a country (ISO code such as IN, US, GB). */
    suspend fun top(countryCode: String): List<RadioStation> =
        get("/json/stations/bycountrycodeexact/${countryCode.uppercase()}?order=clickcount&reverse=true&hidebroken=true&limit=80")

    suspend fun byGenre(tag: String): List<RadioStation> =
        get("/json/stations/bytagexact/${enc(tag)}?order=clickcount&reverse=true&hidebroken=true&limit=80")

    suspend fun search(query: String): List<RadioStation> =
        get("/json/stations/search?name=${enc(query)}&order=clickcount&reverse=true&hidebroken=true&limit=80")

    /** Tells the directory a station was played; it uses this to rank popular stations. */
    suspend fun countClick(station: RadioStation) {
        withContext(Dispatchers.IO) { runCatching { fetch("/json/url/${station.id}") } }
    }

    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

    private suspend fun get(path: String): List<RadioStation> = withContext(Dispatchers.IO) {
        val body = fetch(path)
        json.decodeFromString<List<RadioStation>>(body)
            .filter { it.url.startsWith("http") && it.name.isNotBlank() }
            .distinctBy { it.name.lowercase().trim() to it.url }
    }

    private fun fetch(path: String): String {
        var last: Exception? = null
        // Try the current mirror, then the others.
        for (host in listOf(mirror) + mirrors.filter { it != mirror } + "all.api.radio-browser.info") {
            try {
                val conn = (URL("https://$host$path").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "Ampli/1.0 (Android music player)")
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    mirror = host
                    return conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("radio directory unreachable")
    }
}
