package com.vikash.mp3player.playback

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Feeds the full-screen visualiser. The player's decoded audio passes through a tee (a copy, the
 * sound itself is untouched); while the visualiser is open, 1024-sample windows are turned into
 * [BARS] bar heights with an FFT. Needs no microphone permission, unlike Android's Visualizer.
 */
@OptIn(UnstableApi::class)
object VisualizerTap : TeeAudioProcessor.AudioBufferSink {
    const val BARS = 48
    private const val N = 1024
    private const val FRAME_MS = 33L

    private val _levels = MutableStateFlow(FloatArray(BARS))
    /** Bar heights from 0 to 1, low frequencies first. */
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    // How many screens (visualiser, dance buddy) want levels right now; no work is done at zero.
    private val listeners = java.util.concurrent.atomic.AtomicInteger(0)
    val listening: Boolean get() = listeners.get() > 0

    /** Call when a screen starts showing levels; pair every call with [release]. */
    fun acquire() { listeners.incrementAndGet() }

    fun release() { listeners.updateAndGet { (it - 1).coerceAtLeast(0) } }

    @Volatile private var channels = 2
    @Volatile private var encoding = C.ENCODING_PCM_16BIT
    @Volatile private var sampleRate = 44100

    private val window = FloatArray(N)
    private var filled = 0
    private var lastPublish = 0L
    private val smooth = FloatArray(BARS)
    private val hann = FloatArray(N) { (0.5 - 0.5 * cos(2 * Math.PI * it / (N - 1))).toFloat() }

    /** [hiRes] keeps 24/32-bit files at full depth (float output); see AudioOutput. */
    fun renderersFactory(context: Context, hiRes: Boolean = false): RenderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput || hiRes)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(this@VisualizerTap)))
                .build()
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRate = sampleRateHz
        this.channels = max(1, channelCount)
        this.encoding = encoding
        filled = 0
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!listening) return
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val ch = channels
        when (encoding) {
            C.ENCODING_PCM_16BIT -> while (b.remaining() >= 2 * ch) {
                var sum = 0f
                repeat(ch) { sum += b.short / 32768f }
                push(sum / ch)
            }
            C.ENCODING_PCM_FLOAT -> while (b.remaining() >= 4 * ch) {
                var sum = 0f
                repeat(ch) { sum += b.float }
                push(sum / ch)
            }
            else -> return
        }
    }

    private fun push(sample: Float) {
        window[filled++] = sample
        if (filled < N) return
        filled = 0
        val now = SystemClock.elapsedRealtime()
        if (now - lastPublish < FRAME_MS) return
        lastPublish = now
        publish()
    }

    private fun publish() {
        val re = FloatArray(N) { window[it] * hann[it] }
        val im = FloatArray(N)
        fft(re, im)
        val nyquist = sampleRate / 2f
        val minHz = 40f
        val maxHz = min(16000f, nyquist)
        val out = FloatArray(BARS)
        for (bar in 0 until BARS) {
            // Logarithmic bands, like the ear hears them.
            val lo = minHz * (maxHz / minHz).pow(bar / BARS.toFloat())
            val hi = minHz * (maxHz / minHz).pow((bar + 1) / BARS.toFloat())
            val from = max(1, (lo / nyquist * (N / 2)).toInt())
            val to = max(from + 1, (hi / nyquist * (N / 2)).toInt()).coerceAtMost(N / 2)
            var peak = 0f
            for (k in from until to) peak = max(peak, sqrt(re[k] * re[k] + im[k] * im[k]))
            // Roughly -60 dB..0 dB mapped to 0..1.
            val db = 20f * ln(peak / (N / 4f) + 1e-6f) / ln(10f)
            val level = ((db + 60f) / 60f).coerceIn(0f, 1f)
            // Rise fast, fall slowly, so bars look smooth.
            smooth[bar] = if (level > smooth[bar]) level else smooth[bar] * 0.82f + level * 0.18f
            out[bar] = smooth[bar]
        }
        _levels.value = out
    }

    /** In-place iterative radix-2 FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * Math.PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val xr = re[i + k + len / 2]; val xi = im[i + k + len / 2]
                    val vr = xr * cr - xi * ci
                    val vi = xr * ci + xi * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
