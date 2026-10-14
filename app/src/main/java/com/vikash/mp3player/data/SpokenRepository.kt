package com.vikash.mp3player.data

import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/** A free audiobook from LibriVox (public domain, read by volunteers). */
data class Audiobook(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val language: String,
    val totalTime: String,
    val chapters: List<Song>,
)

/** A podcast show found in the directory or followed by the user. */
@Serializable
data class PodcastShow(
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val artworkUrl: String? = null,
)

/** One podcast episode; [song] is what gets queued. */
data class PodcastEpisode(
    val song: Song,
    val publishedMs: Long,
    val description: String,
)

/**
 * Free spoken audio that Ampli may stream legally:
 * - Audiobooks from LibriVox (public domain recordings hosted on archive.org). API:
 *   https://librivox.org/api/info
 * - Podcasts: shows are found through Apple's public podcast directory (iTunes Search API) and
 *   episodes come straight from each show's own RSS feed, which podcasters publish for any app.
 */
class SpokenRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ------------------------------------------------------------------ audiobooks

    /** Searches titles and authors at once; results are merged, title matches first. */
    suspend fun searchAudiobooks(query: String): List<Audiobook> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val base = "https://librivox.org/api/feed/audiobooks/?format=json&extended=1&limit=30"
        coroutineScope {
            val byTitle = async { fetchBooks("$base&title=$q") }
            val byAuthor = async { fetchBooks("$base&author=$q") }
            (byTitle.await() + byAuthor.await()).distinctBy { it.id }
        }
    }

    private fun fetchBooks(url: String): List<Audiobook> {
        // LibriVox answers "no results" with an error status, so a failed request means none found.
        val body = runCatching { httpGet(url) }.getOrNull() ?: return emptyList()
        val parsed = runCatching { json.decodeFromString(LibriVoxResponse.serializer(), body) }.getOrNull() ?: return emptyList()
        return parsed.books.mapNotNull { it.toAudiobook() }
    }

    private fun LibriVoxBook.toAudiobook(): Audiobook? {
        val author = authors.joinToString(", ") { "${it.first_name} ${it.last_name}".trim() }.ifBlank { "LibriVox" }
        val art = coverArt(url_zip_file)
        val chapters = sections.sortedBy { it.section_number.toIntOrNull() ?: 0 }.mapNotNull { s ->
            val url = https(s.listen_url) ?: return@mapNotNull null
            Song(
                id = "librivox:$id:${s.section_number}",
                uri = Uri.parse(url),
                title = s.title.ifBlank { "Chapter ${s.section_number}" },
                artist = author,
                album = title,
                durationMs = (s.playtime.toLongOrNull() ?: 0L) * 1000,
                artworkUri = art,
                isOnline = true,
                downloadUrl = url, // public domain: free to keep
            )
        }
        if (chapters.isEmpty()) return null
        return Audiobook(
            id = id,
            title = title.trim(),
            author = author,
            description = description.replace(Regex("<[^>]+>"), "").trim(),
            language = language,
            totalTime = totaltime,
            chapters = chapters,
        )
    }

    /** archive.org serves a cover for every LibriVox item next to its zip file. */
    private fun coverArt(zipUrl: String): Uri? {
        val item = Regex("archive\\.org/(?:compress|download)/([^/]+)").find(zipUrl)?.groupValues?.get(1) ?: return null
        return Uri.parse("https://archive.org/services/img/$item")
    }

    // ------------------------------------------------------------------ podcasts

    /** Shows matching [query] in Apple's podcast directory (India store). */
    suspend fun searchPodcasts(query: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val body = httpGet("https://itunes.apple.com/search?media=podcast&entity=podcast&country=IN&limit=40&term=$q")
        json.decodeFromString(ItunesResponse.serializer(), body).results.mapNotNull { it.toShow() }
    }

    /** Today's top podcasts in India. */
    suspend fun topPodcasts(): List<PodcastShow> = withContext(Dispatchers.IO) {
        val chart = json.decodeFromString(
            AppleChart.serializer(),
            httpGet("https://rss.applemarketingtools.com/api/v2/in/podcasts/top/30/podcasts.json"),
        )
        val ids = chart.feed.results.joinToString(",") { it.id }
        if (ids.isEmpty()) return@withContext emptyList()
        val shows = json.decodeFromString(ItunesResponse.serializer(), httpGet("https://itunes.apple.com/lookup?id=$ids&entity=podcast"))
            .results.mapNotNull { r -> r.toShow()?.let { r.collectionId.toString() to it } }.toMap()
        chart.feed.results.mapNotNull { shows[it.id] } // keep the chart order
    }

    private fun ItunesResult.toShow(): PodcastShow? {
        val feed = feedUrl?.takeIf { it.isNotBlank() } ?: return null
        return PodcastShow(
            feedUrl = feed,
            title = collectionName ?: trackName ?: return null,
            author = artistName.orEmpty(),
            artworkUrl = artworkUrl600 ?: artworkUrl100,
        )
    }

    /** Reads a show's RSS feed: newest episodes first. */
    suspend fun episodes(show: PodcastShow): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val conn = open(https(show.feedUrl) ?: show.feedUrl)
        val list = try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.use { parseFeed(it, show) }
        } finally {
            conn.disconnect()
        }
        list.sortedByDescending { it.publishedMs }
    }

    private fun parseFeed(input: InputStream, show: PodcastShow): List<PodcastEpisode> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var channelArt = show.artworkUrl
        val out = mutableListOf<PodcastEpisode>()
        var inItem = false
        var title = ""; var url: String? = null; var guid = ""; var pub = ""; var dur = ""; var art: String? = null; var desc = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> { inItem = true; title = ""; url = null; guid = ""; pub = ""; dur = ""; art = null; desc = "" }
                    "enclosure" -> if (inItem) {
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        if (url == null && (type.isEmpty() || type.startsWith("audio"))) url = parser.getAttributeValue(null, "url")
                    }
                    "itunes:image" -> {
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) {
                            if (inItem) art = href
                            else if (channelArt == null) channelArt = href
                        }
                    }
                    "title" -> if (inItem) title = parser.safeText()
                    "guid" -> if (inItem) guid = parser.safeText()
                    "pubDate" -> if (inItem) pub = parser.safeText()
                    "itunes:duration" -> if (inItem) dur = parser.safeText()
                    "description", "itunes:summary" -> if (inItem && desc.isEmpty()) desc = parser.safeText()
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "item") {
                inItem = false
                val audio = https(url)
                if (audio != null) {
                    val artwork = (art ?: channelArt)?.let { https(it) }?.let(Uri::parse)
                    out += PodcastEpisode(
                        song = Song(
                            id = "podcast:" + (guid.ifBlank { audio }).hashCode().toUInt().toString(16) + ":" + show.feedUrl.hashCode().toUInt().toString(16),
                            uri = Uri.parse(audio),
                            title = title.ifBlank { show.title },
                            artist = show.title,
                            album = show.title,
                            durationMs = parseDuration(dur),
                            artworkUri = artwork,
                            isOnline = true,
                            downloadUrl = audio, // podcasters publish episodes for download
                        ),
                        publishedMs = parseDate(pub),
                        description = desc.replace(Regex("<[^>]+>"), "").trim().take(400),
                    )
                }
                if (out.size >= 200) break
            }
            event = parser.next()
        }
        return out
    }

    /** Text of the current tag, or "" when it holds child tags instead. */
    private fun XmlPullParser.safeText(): String = runCatching { nextText() }.getOrDefault("").trim()

    private fun parseDuration(raw: String): Long {
        if (raw.isBlank()) return 0L
        val parts = raw.trim().split(":").map { it.toDoubleOrNull() ?: return 0L }
        val seconds = parts.fold(0.0) { acc, p -> acc * 60 + p }
        return (seconds * 1000).toLong()
    }

    private val dateFormats = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z")

    private fun parseDate(raw: String): Long {
        for (f in dateFormats) {
            runCatching { SimpleDateFormat(f, Locale.US).parse(raw.trim())?.time }.getOrNull()?.let { return it }
        }
        return 0L
    }

    // ------------------------------------------------------------------ http

    /** Android blocks plain http, and almost every host also serves https. */
    private fun https(url: String?): String? {
        val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (u.startsWith("http://")) "https://" + u.removePrefix("http://") else u
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Ampli/1.0 (Android music player)")
    }

    private fun httpGet(url: String): String {
        val conn = open(url)
        return try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------ JSON shapes

    @Suppress("PropertyName")
    @Serializable
    private data class LibriVoxResponse(val books: List<LibriVoxBook> = emptyList())

    @Suppress("PropertyName")
    @Serializable
    private data class LibriVoxBook(
        val id: String = "",
        val title: String = "",
        val description: String = "",
        val language: String = "",
        val totaltime: String = "",
        val url_zip_file: String = "",
        val authors: List<LibriVoxAuthor> = emptyList(),
        val sections: List<LibriVoxSection> = emptyList(),
    )

    @Suppress("PropertyName")
    @Serializable
    private data class LibriVoxAuthor(val first_name: String = "", val last_name: String = "")

    @Suppress("PropertyName")
    @Serializable
    private data class LibriVoxSection(
        val section_number: String = "",
        val title: String = "",
        val listen_url: String = "",
        val playtime: String = "",
    )

    @Serializable
    private data class ItunesResponse(val results: List<ItunesResult> = emptyList())

    @Serializable
    private data class ItunesResult(
        val collectionId: Long = 0,
        val collectionName: String? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val feedUrl: String? = null,
        val artworkUrl600: String? = null,
        val artworkUrl100: String? = null,
    )

    @Serializable
    private data class AppleChart(val feed: AppleChartFeed = AppleChartFeed())

    @Serializable
    private data class AppleChartFeed(val results: List<AppleChartItem> = emptyList())

    @Serializable
    private data class AppleChartItem(val id: String = "")
}
