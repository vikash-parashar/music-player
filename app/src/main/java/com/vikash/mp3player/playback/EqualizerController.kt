package com.vikash.mp3player.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    /** Where the sound is going right now, e.g. "Phone speaker" or the car's Bluetooth name. */
    val outputLabel: String = "Phone speaker",
)

/**
 * Wraps the platform equalizer attached to the player's audio session.
 *
 * Settings are kept separately for each output: phone speaker, wired headphones, USB audio,
 * and every Bluetooth device by name (so the car and the earbuds each get their own sound).
 * When the output changes, the matching settings are applied automatically.
 *
 * Some devices (and most emulators) have no equalizer; [EqState.available] is false there.
 */
object EqualizerController {
    private var equalizer: Equalizer? = null
    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var profile = Output("speaker", "Phone speaker")
    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private const val PREFS = "equalizer"

    private data class Output(val key: String, val label: String)

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onRouteChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onRouteChanged()
    }

    fun attach(context: Context, audioSessionId: Int) {
        release()
        val eq = runCatching { Equalizer(0, audioSessionId) }.getOrNull() ?: return
        equalizer = eq
        appContext = context.applicationContext
        audioManager = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also {
            it.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        }
        profile = currentOutput()
        applyProfile()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        equalizer?.setEnabled(enabled)
        prefs(context).edit().putBoolean(key("enabled"), enabled).apply()
        publish()
    }

    fun setBandLevel(context: Context, band: Short, levelMb: Short) {
        equalizer?.setBandLevel(band, levelMb) ?: return
        prefs(context).edit().putInt(key("band_$band"), levelMb.toInt()).apply()
        publish()
    }

    fun usePreset(context: Context, preset: Short) {
        val eq = equalizer ?: return
        eq.usePreset(preset)
        val edit = prefs(context).edit()
        for (i in 0 until eq.numberOfBands) edit.putInt(key("band_$i"), eq.getBandLevel(i.toShort()).toInt())
        edit.apply()
        publish()
    }

    /** Re-applies saved levels, e.g. after a restore from backup. */
    fun reload() = applyProfile()

    fun release() {
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        audioManager = null
        equalizer?.release()
        equalizer = null
        _state.value = EqState()
    }

    private fun onRouteChanged() {
        val next = currentOutput()
        if (next != profile) {
            profile = next
            applyProfile()
        }
    }

    /** Loads this output's saved levels. A new output starts from the old single-profile settings. */
    private fun applyProfile() {
        val eq = equalizer ?: return
        val prefs = prefs(appContext ?: return)
        for (i in 0 until eq.numberOfBands) {
            val saved = prefs.getInt(key("band_$i"), prefs.getInt("band_$i", Int.MIN_VALUE))
            if (saved != Int.MIN_VALUE) eq.setBandLevel(i.toShort(), saved.toShort())
        }
        eq.setEnabled(prefs.getBoolean(key("enabled"), prefs.getBoolean("enabled", false)))
        publish()
    }

    private fun currentOutput(): Output {
        val outs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        fun named(d: AudioDeviceInfo, fallback: String) = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: fallback
        outs.firstOrNull { it.isBluetooth() }?.let { d ->
            val name = named(d, "Bluetooth")
            return Output("bt:$name", name)
        }
        outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
            ?.let { return Output("wired", "Wired headphones") }
        outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            ?.let { return Output("usb", "USB audio") }
        return Output("speaker", "Phone speaker")
    }

    private fun AudioDeviceInfo.isBluetooth(): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    private fun key(name: String) = "${profile.key}|$name"

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
            outputLabel = profile.label,
        )
    }
}
