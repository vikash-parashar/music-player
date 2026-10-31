package com.vikash.mp3player.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Virtualizer
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Ampli Pro sound: a true 10-band equaliser (Android 9+, built on DynamicsProcessing so it does
 * not depend on how many bands the phone's own equaliser has), bass boost and 3D surround
 * (Virtualizer). A limiter stops loud boosts from clipping. Effects only run while Pro is
 * unlocked (bought, or the 24-hour trial).
 */
object ProSound {
    /** Centre frequencies of the 10 bands, in Hz. */
    val FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    const val MAX_DB = 12f
    const val MAX_STRENGTH = 1000

    /** Named curves, gains in dB for each of the 10 bands. */
    enum class Preset(val gains: FloatArray) {
        Flat(FloatArray(10)),
        Bass(floatArrayOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f)),
        Vocal(floatArrayOf(-2f, -2f, -1f, 1f, 3f, 4f, 4f, 2f, 0f, -1f)),
        Bollywood(floatArrayOf(4f, 3f, 2f, 0f, -1f, 1f, 3f, 3f, 2f, 1f)),
        Rock(floatArrayOf(5f, 4f, 2f, -1f, -2f, -1f, 2f, 4f, 5f, 5f)),
        Classical(floatArrayOf(3f, 2f, 1f, 0f, 0f, 0f, -1f, -1f, 1f, 3f)),
    }

    data class State(
        /** False below Android 9 or when the phone refuses the effect. */
        val eqAvailable: Boolean = false,
        val bassAvailable: Boolean = false,
        val surroundAvailable: Boolean = false,
        val unlocked: Boolean = false,
        val eqEnabled: Boolean = false,
        val gains: List<Float> = List(10) { 0f },
        val bass: Int = 0,
        val surround: Int = 0,
        /** Name of the headphone curve in use (AutoEq), or null for a preset or hand-set curve. */
        val headphone: String? = null,
    )

    private const val PREFS = "pro_sound"
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var appContext: Context? = null
    private var dynamics: Any? = null // DynamicsProcessing, typed loosely so older phones never load the class
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    fun attach(context: Context, audioSessionId: Int) {
        release()
        val app = context.applicationContext
        appContext = app
        val p = prefs(app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dynamics = runCatching { createDynamics(audioSessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, audioSessionId).takeIf { it.strengthSupported } }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, audioSessionId).takeIf { it.strengthSupported } }.getOrNull()
        _state.value = _state.value.copy(
            eqAvailable = dynamics != null,
            bassAvailable = bassBoost != null,
            surroundAvailable = virtualizer != null,
            eqEnabled = p.getBoolean("eq_enabled", false),
            gains = List(10) { p.getFloat("gain_$it", 0f) },
            bass = p.getInt("bass", 0),
            surround = p.getInt("surround", 0),
            headphone = p.getString("headphone", null),
        )
        apply()
    }

    /** Called whenever Pro access changes (purchase, refund, trial start or end). */
    fun setUnlocked(unlocked: Boolean) {
        if (_state.value.unlocked == unlocked) return
        _state.value = _state.value.copy(unlocked = unlocked)
        apply()
    }

    fun setEqEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("eq_enabled", enabled).apply()
        _state.value = _state.value.copy(eqEnabled = enabled)
        apply()
    }

    fun setGain(context: Context, band: Int, db: Float) {
        val value = db.coerceIn(-MAX_DB, MAX_DB)
        prefs(context).edit().putFloat("gain_$band", value).remove("headphone").apply()
        _state.value = _state.value.copy(gains = _state.value.gains.toMutableList().also { it[band] = value }, headphone = null)
        apply()
    }

    fun usePreset(context: Context, preset: Preset) = useCurve(context, preset.gains, null)

    /** Applies a headphone correction curve (AutoEq) and turns the equaliser on. */
    fun useHeadphone(context: Context, name: String, gains: FloatArray) {
        useCurve(context, gains, name)
        if (!_state.value.eqEnabled) setEqEnabled(context, true)
    }

    private fun useCurve(context: Context, gains: FloatArray, headphone: String?) {
        val values = List(10) { (gains.getOrNull(it) ?: 0f).coerceIn(-MAX_DB, MAX_DB) }
        val edit = prefs(context).edit()
        values.forEachIndexed { i, g -> edit.putFloat("gain_$i", g) }
        if (headphone == null) edit.remove("headphone") else edit.putString("headphone", headphone)
        edit.apply()
        _state.value = _state.value.copy(gains = values, headphone = headphone)
        apply()
    }

    fun setBass(context: Context, strength: Int) {
        val value = strength.coerceIn(0, MAX_STRENGTH)
        prefs(context).edit().putInt("bass", value).apply()
        _state.value = _state.value.copy(bass = value)
        apply()
    }

    fun setSurround(context: Context, strength: Int) {
        val value = strength.coerceIn(0, MAX_STRENGTH)
        prefs(context).edit().putInt("surround", value).apply()
        _state.value = _state.value.copy(surround = value)
        apply()
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { (dynamics as? DynamicsProcessing)?.release() }
        dynamics = null
        runCatching { bassBoost?.release() }
        bassBoost = null
        runCatching { virtualizer?.release() }
        virtualizer = null
    }

    private fun apply() {
        val s = _state.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) applyDynamics(s)
        runCatching {
            bassBoost?.let {
                it.setStrength(s.bass.toShort())
                it.setEnabled(s.unlocked && s.bass > 0)
            }
        }
        runCatching {
            virtualizer?.let {
                it.setStrength(s.surround.toShort())
                it.setEnabled(s.unlocked && s.surround > 0)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createDynamics(sessionId: Int): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount = */ 2,
            /* preEqInUse = */ true, /* preEqBandCount = */ FREQUENCIES.size,
            /* mbcInUse = */ false, /* mbcBandCount = */ 0,
            /* postEqInUse = */ false, /* postEqBandCount = */ 0,
            /* limiterInUse = */ true,
        ).build()
        return DynamicsProcessing(0, sessionId, config).also { dp ->
            // Gentle limiter just under 0 dBFS so boosted bands don't distort.
            dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyDynamics(s: State) {
        val dp = dynamics as? DynamicsProcessing ?: return
        runCatching {
            FREQUENCIES.forEachIndexed { i, centre ->
                // Each band reaches up to halfway (geometrically) to the next centre.
                val cutoff = if (i == FREQUENCIES.lastIndex) 20000f else sqrt(centre.toFloat() * FREQUENCIES[i + 1])
                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoff, s.gains[i]))
            }
            dp.setEnabled(s.unlocked && s.eqEnabled)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
