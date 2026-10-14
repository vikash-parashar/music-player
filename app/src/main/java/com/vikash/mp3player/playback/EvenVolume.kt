package com.vikash.mp3player.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * "Even volume" (ReplayGain): songs tagged with a loudness value by a tagger such as foobar2000
 * or MusicBrainz Picard are turned down to a common level, so a quiet old song and a loud new one
 * play at about the same volume. Saved in ampli_prefs, so backups include it.
 */
@OptIn(UnstableApi::class)
object EvenVolume {
    private const val KEY = "even_volume"
    private const val KEY_NAME = "REPLAYGAIN_TRACK_GAIN"
    /** Most ReplayGain values are -6 to -10 dB; this headroom keeps tagged songs from sounding much quieter than untagged ones. */
    private const val PREAMP_DB = 6f
    private val _enabled = MutableStateFlow<Boolean?>(null)

    fun enabled(context: Context): StateFlow<Boolean?> {
        if (_enabled.value == null) reload(context)
        return _enabled.asStateFlow()
    }

    fun set(context: Context, on: Boolean) {
        _enabled.value = on
        prefs(context).edit().putBoolean(KEY, on).apply()
    }

    fun reload(context: Context) {
        _enabled.value = prefs(context).getBoolean(KEY, false)
    }

    /** Volume factor (0..1) for the song whose tracks these are; 1 when it has no ReplayGain tag. */
    fun factor(tracks: Tracks): Float {
        val db = gainDb(tracks) ?: return 1f
        return 10f.pow((db + PREAMP_DB) / 20f).coerceIn(0.05f, 1f)
    }

    private fun gainDb(tracks: Tracks): Float? {
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    val text = when (val entry = metadata.get(j)) {
                        is TextInformationFrame ->
                            if (entry.id == "TXXX" && entry.description.equals(KEY_NAME, ignoreCase = true)) entry.values.firstOrNull() else null
                        is VorbisComment -> if (entry.key.equals(KEY_NAME, ignoreCase = true)) entry.value else null
                        is InternalFrame -> if (entry.description.equals(KEY_NAME, ignoreCase = true)) entry.text else null
                        else -> null
                    }
                    parseDb(text)?.let { return it }
                }
            }
        }
        return null
    }

    /** "-7.32 dB" → -7.32 */
    internal fun parseDb(text: String?): Float? =
        text?.trim()?.removeSuffix("dB")?.removeSuffix("db")?.trim()?.replace(',', '.')?.toFloatOrNull()
            ?.takeIf { it in -30f..30f }

    private fun prefs(context: Context) = context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
}
