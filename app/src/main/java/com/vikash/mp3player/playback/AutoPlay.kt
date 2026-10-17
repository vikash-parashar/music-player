package com.vikash.mp3player.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

/**
 * "Play when headphones connect": resumes the queue when Bluetooth earbuds, a car stereo or wired
 * earphones connect. Works while the player service is alive (app open, or paused in the
 * notification); Android doesn't let a closed app start playing in the background.
 */
class AutoPlay(private val context: Context, private val player: () -> Player?) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val known = mutableSetOf<Int>()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val fresh = addedDevices.filter { it.isSink && it.isHeadphoneLike() && known.add(it.id) }
            // Registration replays devices that were already connected; they are in [known], so skipped.
            if (fresh.isNotEmpty() && isEnabled(context)) {
                val p = player() ?: return
                if (!p.isPlaying && p.mediaItemCount > 0) {
                    if (p.playbackState == Player.STATE_IDLE) p.prepare()
                    p.play()
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            removedDevices.forEach { known.remove(it.id) }
        }
    }

    fun start() {
        // Seed with what is connected now, so the registration callback adds nothing new.
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isHeadphoneLike() }.forEach { known.add(it.id) }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun release() {
        audio.unregisterAudioDeviceCallback(callback)
    }

    private fun AudioDeviceInfo.isHeadphoneLike(): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET -> true
        else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    companion object {
        private const val PREFS = "ampli_prefs"
        private const val KEY = "autoplay_on_connect"

        fun isEnabled(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

        fun setEnabled(context: Context, enabled: Boolean) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}
