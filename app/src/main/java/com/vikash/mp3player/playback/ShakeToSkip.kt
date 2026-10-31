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
    private val prefs = context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
    // Settings writes the switch while this service runs; react straight away.
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY) update()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    /** Call when playback starts or stops, or when the setting changes. */
    fun update() {
        val want = enabled(context) && player.isPlaying && accel != null
        if (want && !listening) {
            listening = sensors?.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI) == true
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
        if (g < SHAKE_G) return
        val now = SystemClock.elapsedRealtime()
        // Two strong peaks within half a second: a deliberate shake, not a bump in a pocket.
        if (now - firstPeak > 500) { firstPeak = now; return }
        if (now - lastSkip < 1_200) return
        lastSkip = now
        firstPeak = 0L
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val SHAKE_G = 2.6f
        private const val KEY = "shake_to_skip"

        fun enabled(context: Context): Boolean =
            context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE).getBoolean(KEY, false)

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        }
    }
}
