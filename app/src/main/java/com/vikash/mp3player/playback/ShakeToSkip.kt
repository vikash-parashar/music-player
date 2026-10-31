package com.vikash.mp3player.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.media3.common.Player
import kotlin.math.sqrt

/**
 * Shake the phone to jump to the next song. Off by default (Settings). The accelerometer is only
 * listened to while music is playing, so it costs no battery when paused.
 */
class ShakeToSkip(private val context: Context, private val player: Player) : SensorEventListener {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var listening = false
    private var lastSkip = 0L
    private var firstPeak = 0L
    private var aboveThreshold = false
    private val prefs = context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
    // Settings writes the switch while this service runs; react straight away.
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY) update()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    /**
     * Call when playback starts or stops, or when the setting changes. Follows playWhenReady rather
     * than isPlaying: isPlaying blips to false while the next song loads, and the sensor used to be
     * dropped in that gap after the first skip.
     */
    fun update() {
        val active = player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        val want = enabled(context) && active && accel != null
        if (want && !listening) {
            firstPeak = 0L
            aboveThreshold = false
            listening = sensors?.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME) == true
        } else if (!want && listening) {
            sensors?.unregisterListener(this)
            listening = false
        }
    }

    fun release() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        if (listening) sensors?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        // Count a peak once, when the force rises past the threshold; it must drop back below
        // RELEASE_G before the next one counts, so one long jolt is never read as two.
        if (aboveThreshold) {
            if (g < RELEASE_G) aboveThreshold = false
            return
        }
        if (g < SHAKE_G) return
        aboveThreshold = true
        val now = SystemClock.elapsedRealtime()
        if (now - lastSkip < 1_000) return
        // Two strong peaks within 600 ms: a deliberate shake, not a bump in a pocket.
        if (firstPeak == 0L || now - firstPeak > 600) { firstPeak = now; return }
        lastSkip = now
        firstPeak = 0L
        skip()
    }

    private fun skip() {
        when {
            player.hasNextMediaItem() -> player.seekToNextMediaItem()
            // Last song in the queue: start the queue again rather than ignoring the shake.
            player.mediaItemCount > 1 -> player.seekToDefaultPosition(0)
            else -> return
        }
        if (!player.playWhenReady) player.play()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val SHAKE_G = 2.3f
        private const val RELEASE_G = 1.4f
        private const val KEY = "shake_to_skip"

        fun enabled(context: Context): Boolean =
            context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE).getBoolean(KEY, false)

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        }
    }
}
