package com.vikash.mp3player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class SongStat(
    val title: String = "",
    val artist: String = "",
    val plays: Int = 0,
    val listenedMs: Long = 0,
    val lastPlayedMs: Long = 0,
)

@Serializable
data class StatsData(
    val songs: Map<String, SongStat> = emptyMap(),
    /** Listening time per month, keyed "2026-10". */
    val monthMs: Map<String, Long> = emptyMap(),
)

/**
 * Remembers what the listener actually plays, on the phone only (nothing is uploaded).
 * Feeds the Most played / Never played lists and the listening stats screen.
 *
 * A play counts once the song has been heard for [COUNT_AFTER_MS], or to the end if shorter,
 * so skipping through a queue doesn't inflate the numbers.
 */
class PlayStats(context: Context) {

    private val file = File(context.filesDir, "play_stats.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _data = MutableStateFlow(load())
    val data: StateFlow<StatsData> = _data.asStateFlow()

    private fun load(): StatsData =
        runCatching { json.decodeFromString<StatsData>(file.readText()) }.getOrDefault(StatsData())

    /** Called when a song stops being the current one, with how long it was actually heard. */
    @Synchronized
    fun record(id: String, title: String, artist: String, heardMs: Long, durationMs: Long) {
        if (id.isEmpty() || heardMs < 1000) return
        val counts = heardMs >= COUNT_AFTER_MS || (durationMs > 0 && heardMs >= durationMs * 9 / 10)
        val now = System.currentTimeMillis()
        val old = _data.value
        val prev = old.songs[id] ?: SongStat()
        val stat = prev.copy(
            title = title.ifEmpty { prev.title },
            artist = artist.ifEmpty { prev.artist },
            plays = prev.plays + if (counts) 1 else 0,
            listenedMs = prev.listenedMs + heardMs,
            lastPlayedMs = if (counts) now else prev.lastPlayedMs,
        )
        val month = MONTH.format(Date(now))
        val next = old.copy(
            songs = old.songs + (id to stat),
            monthMs = old.monthMs + (month to ((old.monthMs[month] ?: 0L) + heardMs)),
        )
        _data.value = next
        save(next)
    }

    /** Replaces everything, used by restore from backup. */
    fun replaceAll(data: StatsData) {
        _data.value = data
        save(data)
    }

    fun clear() = replaceAll(StatsData())

    private fun save(data: StatsData) {
        io.launch {
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.encodeToString(data))
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        const val COUNT_AFTER_MS = 30_000L
        private val MONTH = SimpleDateFormat("yyyy-MM", Locale.US)
    }
}
