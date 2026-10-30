package com.vikash.mp3player.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** One narrated episode of an Ampli Stories series. */
data class StoryEpisode(val n: Int, val title: String, val url: String, val durationSec: Int)

/** A Pocket FM-style audio series: short episodes, the first few free. */
data class StorySeries(
    val id: String,
    val title: String,
    val titleEn: String,
    val genre: String,
    val author: String,
    val description: String,
    val descriptionEn: String,
    val freeEpisodes: Int,
    val bg: Long,
    val fg: Long,
    val accent: Long,
    val episodes: List<StoryEpisode>,
) {
    /** Media id used in the queue, so resume, stats and the unlock check can find the episode. */
    fun mediaId(n: Int) = "story:$id:$n"

    fun song(e: StoryEpisode) = Song(
        id = mediaId(e.n),
        uri = Uri.parse(e.url),
        title = e.title,
        artist = title,
        album = title,
        durationMs = e.durationSec * 1000L,
        artworkUri = null,
        isOnline = true,
    )
}

/**
 * The Stories catalogue. A copy ships inside the app (assets/stories_catalog.json, written by the
 * Narrate Stories workflow) so the tab works offline at once; a newer one is fetched from
 * ampliapps.in and cached, so new episodes appear without an app update.
 */
object StoriesRepository {
    private const val ASSET = "stories_catalog.json"
    private const val CACHE = "stories_catalog.json"
    private val REMOTE = listOf(
        "https://ampliapps.in/stories/catalog.json",
        "https://raw.githubusercontent.com/vikash-parashar/music-player/main/docs/stories/catalog.json",
    )

    private val _series = MutableStateFlow<List<StorySeries>>(emptyList())
    val series: StateFlow<List<StorySeries>> = _series.asStateFlow()
    private var loaded = false

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        val cache = File(context.filesDir, CACHE)
        val local = runCatching { cache.readText() }.getOrNull()
            ?: runCatching { context.assets.open(ASSET).bufferedReader().use { it.readText() } }.getOrNull()
        val bundledVersion = runCatching {
            JSONObject(context.assets.open(ASSET).bufferedReader().use { it.readText() }).optLong("version")
        }.getOrDefault(0L)
        var best = local?.let { runCatching { JSONObject(it) }.getOrNull() }
        // An app update can ship a newer catalogue than the cached one.
        if (best != null && best.optLong("version") < bundledVersion) {
            best = runCatching { JSONObject(context.assets.open(ASSET).bufferedReader().use { it.readText() }) }.getOrNull()
        }
        if (!loaded) best?.let { _series.value = parse(it) }
        loaded = true
        for (url in REMOTE) {
            val text = runCatching { fetch(url) }.getOrNull() ?: continue
            val json = runCatching { JSONObject(text) }.getOrNull() ?: continue
            if (json.optLong("version") >= (best?.optLong("version") ?: 0L)) {
                runCatching { cache.writeText(text) }
                _series.value = parse(json)
            }
            break
        }
    }

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    internal fun parse(json: JSONObject): List<StorySeries> {
        val arr = json.optJSONArray("series") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val eps = s.optJSONArray("episodes") ?: return@mapNotNull null
            val episodes = (0 until eps.length()).mapNotNull { j ->
                val e = eps.optJSONObject(j) ?: return@mapNotNull null
                val url = e.optString("url")
                if (url.isBlank()) null else StoryEpisode(e.optInt("n"), e.optString("title"), url, e.optInt("durationSec"))
            }.sortedBy { it.n }
            if (episodes.isEmpty()) return@mapNotNull null
            val cover = s.optJSONObject("cover")
            StorySeries(
                id = s.optString("id"),
                title = s.optString("title"),
                titleEn = s.optString("titleEn"),
                genre = s.optString("genre"),
                author = s.optString("author"),
                description = s.optString("description"),
                descriptionEn = s.optString("descriptionEn"),
                freeEpisodes = s.optInt("freeEpisodes", 5),
                bg = color(cover?.optString("bg"), 0xFF171717),
                fg = color(cover?.optString("fg"), 0xFFC9DDD0),
                accent = color(cover?.optString("accent"), 0xFFA31C1B),
                episodes = episodes,
            )
        }
    }

    private fun color(hex: String?, fallback: Long): Long =
        hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.or(0xFF000000) ?: fallback
}

/**
 * Which locked episodes this listener opened by watching an ad, how many ad unlocks are left today,
 * and where they stopped in each series.
 */
class StoryAccess(context: Context) {
    private val prefs = context.getSharedPreferences("story_access", Context.MODE_PRIVATE)
    private val _unlocked = MutableStateFlow(prefs.getStringSet(KEY_UNLOCKED, emptySet()).orEmpty())
    val unlocked: StateFlow<Set<String>> = _unlocked.asStateFlow()

    fun isOpen(series: StorySeries, n: Int, pass: Boolean) =
        pass || n <= series.freeEpisodes || series.mediaId(n) in _unlocked.value

    fun adUnlocksLeft(today: LocalDate = LocalDate.now()): Int {
        val used = if (prefs.getString(KEY_DAY, null) == today.toString()) prefs.getInt(KEY_USED, 0) else 0
        return (DAILY_AD_UNLOCKS - used).coerceAtLeast(0)
    }

    fun unlockWithAd(series: StorySeries, n: Int, today: LocalDate = LocalDate.now()) {
        val day = today.toString()
        val used = if (prefs.getString(KEY_DAY, null) == day) prefs.getInt(KEY_USED, 0) else 0
        val set = _unlocked.value + series.mediaId(n)
        _unlocked.value = set
        prefs.edit { putStringSet(KEY_UNLOCKED, set); putString(KEY_DAY, day); putInt(KEY_USED, used + 1) }
    }

    /** Last episode started in a series, for "Continue episode N". */
    fun lastEpisode(seriesId: String): Int = prefs.getInt("last_$seriesId", 0)

    fun setLastEpisode(seriesId: String, n: Int) = prefs.edit { putInt("last_$seriesId", n) }

    companion object {
        const val DAILY_AD_UNLOCKS = 3
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_DAY = "ad_day"
        private const val KEY_USED = "ad_used"
    }
}
