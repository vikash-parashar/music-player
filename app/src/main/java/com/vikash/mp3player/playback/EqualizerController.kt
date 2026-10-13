package com.vikash.mp3player.playback

import android.content.Context
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(val index: Short, val centerHz: Int, val levelMb: Short)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val minLevelMb: Short = -1500,
    val maxLevelMb: Short = 1500,
    val bands: List<EqBand> = emptyList(),
    val presets: List<String> = emptyList(),
)

/**
 * Wraps the platform equalizer attached to the player's audio session. Band levels are
 * saved so they survive restarts. Some devices (and most emulators) have no equalizer;
 * [EqState.available] is false there and the screen says so.
 */
object EqualizerController {
    private var equalizer: Equalizer? = null
    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private const val PREFS = "equalizer"

    fun attach(context: Context, audioSessionId: Int) {
        release()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val eq = runCatching { Equalizer(0, audioSessionId) }.getOrNull() ?: return
        equalizer = eq
        for (i in 0 until eq.numberOfBands) {
            val saved = prefs.getInt("band_$i", Int.MIN_VALUE)
            if (saved != Int.MIN_VALUE) eq.setBandLevel(i.toShort(), saved.toShort())
        }
        eq.setEnabled(prefs.getBoolean("enabled", false))
        publish()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        equalizer?.setEnabled(enabled)
        prefs(context).edit().putBoolean("enabled", enabled).apply()
        publish()
    }

    fun setBandLevel(context: Context, band: Short, levelMb: Short) {
        equalizer?.setBandLevel(band, levelMb) ?: return
        prefs(context).edit().putInt("band_$band", levelMb.toInt()).apply()
        publish()
    }

    fun usePreset(context: Context, preset: Short) {
        val eq = equalizer ?: return
        eq.usePreset(preset)
        val edit = prefs(context).edit()
        for (i in 0 until eq.numberOfBands) edit.putInt("band_$i", eq.getBandLevel(i.toShort()).toInt())
        edit.apply()
        publish()
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        _state.value = EqState()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun publish() {
        val eq = equalizer ?: return
        val range = eq.bandLevelRange
        _state.value = EqState(
            available = true,
            enabled = eq.enabled,
            minLevelMb = range[0],
            maxLevelMb = range[1],
            bands = (0 until eq.numberOfBands).map { i ->
                val b = i.toShort()
                EqBand(b, eq.getCenterFreq(b) / 1000, eq.getBandLevel(b))
            },
            presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
        )
    }
}
