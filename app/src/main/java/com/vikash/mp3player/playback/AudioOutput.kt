package com.vikash.mp3player.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Hi-Res output (Pro): keeps 24-bit and 32-bit files at full depth by sending float PCM to the
 * audio system, and on Android 14+ asks for a bit-perfect path to a USB DAC so the file's own
 * sample rate reaches the DAC without Android's mixer resampling it. Also reports what is
 * playing and where, for the Settings card and the Now Playing badge.
 */
object AudioOutput {
    const val PREFS = "audio_output"
    private const val KEY_HIRES = "hires_output"
    private const val KEY_BIT_PERFECT = "usb_bit_perfect"

    data class Info(
        /** Output name, e.g. "Speaker", "Bluetooth: OnePlus Buds" or "USB: FiiO KA3". */
        val device: String = "",
        val isUsb: Boolean = false,
        val isBluetooth: Boolean = false,
        /** Source file: codec label and sample rate (0 when unknown). */
        val codec: String = "",
        val sourceRate: Int = 0,
        val sourceBits: Int = 0,
        /** What the player hands to Android. */
        val outputRate: Int = 0,
        val outputBits: Int = 0,
        val bitPerfect: Boolean = false,
    ) {
        /** 24-bit or above 48 kHz: worth a "Hi-Res" badge. */
        val isHiRes: Boolean get() = sourceBits >= 24 || sourceRate > 48_000
    }

    private val _info = MutableStateFlow(Info())
    val info: StateFlow<Info> = _info.asStateFlow()

    fun hiResEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIRES, false)
    fun bitPerfectEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BIT_PERFECT, false)

    /** Takes effect the next time the player starts (Hi-Res changes the audio pipeline). */
    fun setHiRes(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIRES, on).apply()

    fun setBitPerfect(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BIT_PERFECT, on).apply()
        attached?.let { if (Build.VERSION.SDK_INT >= 34) it.applyBitPerfect() }
    }

    /** Bit-perfect USB needs Android 14. */
    val bitPerfectSupported: Boolean get() = Build.VERSION.SDK_INT >= 34

    private var attached: Tracker? = null

    /** Called by the playback service; returns the listener to add to the player. */
    @OptIn(UnstableApi::class)
    fun attach(context: Context, isPro: () -> Boolean): AnalyticsListener {
        val t = Tracker(context.applicationContext, isPro)
        attached = t
        t.start()
        return t
    }

    fun detach() {
        attached?.stop()
        attached = null
    }

    @OptIn(UnstableApi::class)
    private class Tracker(private val context: Context, private val isPro: () -> Boolean) : AnalyticsListener {
        private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val main = Handler(Looper.getMainLooper())
        private var trackRate = 0
        private var trackEncoding = C.ENCODING_PCM_16BIT

        private val devices = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshDevice()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshDevice()
        }

        fun start() {
            audio.registerAudioDeviceCallback(devices, main)
            refreshDevice()
        }

        fun stop() {
            runCatching { audio.unregisterAudioDeviceCallback(devices) }
            if (Build.VERSION.SDK_INT >= 34) clearBitPerfect()
        }

        private fun outputs() = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()

        private fun usbDevice(): AudioDeviceInfo? = outputs().firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }

        /** Android routes media to the most recently connected wired/USB/Bluetooth device, else the speaker. */
        private fun refreshDevice() {
            val list = outputs()
            val usb = usbDevice()
            val bt = list.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) }
            val wired = list.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
            val name = when {
                usb != null -> "USB: ${usb.productName}"
                bt != null -> "Bluetooth: ${bt.productName}"
                wired != null -> "Wired headphones"
                else -> "Phone speaker"
            }
            _info.update { it.copy(device = name, isUsb = usb != null, isBluetooth = usb == null && bt != null) }
            if (Build.VERSION.SDK_INT >= 34) applyBitPerfect()
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
        ) {
            val bits = when (format.pcmEncoding) {
                C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
                C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
                C.ENCODING_PCM_8BIT -> 8
                else -> 0
            }
            _info.update {
                it.copy(
                    codec = codecLabel(format.sampleMimeType),
                    sourceRate = format.sampleRate.coerceAtLeast(0),
                    sourceBits = bits,
                )
            }
        }

        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
            trackRate = audioTrackConfig.sampleRate
            trackEncoding = audioTrackConfig.encoding
            val bits = when (audioTrackConfig.encoding) {
                C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_32BIT -> 32
                C.ENCODING_PCM_24BIT -> 24
                else -> 16
            }
            _info.update { it.copy(outputRate = audioTrackConfig.sampleRate, outputBits = bits) }
            if (Build.VERSION.SDK_INT >= 34) main.post { applyBitPerfect() }
        }

        /**
         * Bit-perfect works only when the DAC offers a mixer format that matches the track exactly
         * (same rate and encoding). Otherwise Android keeps its normal mixer, which still plays.
         */
        @RequiresApi(34)
        fun applyBitPerfect() {
            val usb = usbDevice()
            if (usb == null || !bitPerfectEnabled(context) || !isPro() || trackRate <= 0) {
                clearBitPerfect()
                return
            }
            val wantEncoding = when (trackEncoding) {
                C.ENCODING_PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
                C.ENCODING_PCM_32BIT -> AudioFormat.ENCODING_PCM_32BIT
                C.ENCODING_PCM_24BIT -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                else -> AudioFormat.ENCODING_PCM_16BIT
            }
            val match = runCatching { audio.getSupportedMixerAttributes(usb) }.getOrDefault(emptyList())
                .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
                .firstOrNull { it.format.sampleRate == trackRate && it.format.encoding == wantEncoding }
            if (match == null) { clearBitPerfect(); return }
            val ok = runCatching { audio.setPreferredMixerAttributes(mediaAttributes, usb, match) }.getOrDefault(false)
            _info.update { it.copy(bitPerfect = ok) }
        }

        @RequiresApi(34)
        private fun clearBitPerfect() {
            usbDevice()?.let { runCatching { audio.clearPreferredMixerAttributes(mediaAttributes, it) } }
            _info.update { it.copy(bitPerfect = false) }
        }

        private val mediaAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    private fun codecLabel(mime: String?): String = when (mime) {
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_ALAC -> "ALAC"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_WAV, MimeTypes.AUDIO_RAW -> "PCM"
        MimeTypes.AUDIO_AC3 -> "AC-3"
        MimeTypes.AUDIO_E_AC3 -> "E-AC-3"
        null -> ""
        else -> mime.substringAfter('/').uppercase()
    }
}
