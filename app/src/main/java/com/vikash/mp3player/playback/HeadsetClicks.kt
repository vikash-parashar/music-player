package com.vikash.mp3player.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Earphone button presses (Musicolet-style): 1 press = play/pause, 2 = next song, 3 = previous.
 * Only for the button on wired or Bluetooth earphones; the notification and the app are not delayed.
 */
class HeadsetClicks(private val player: () -> Player) {

    private val handler = Handler(Looper.getMainLooper())
    private var count = 0
    private val fire = Runnable {
        val p = player()
        when (count) {
            1 -> if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_IDLE) p.prepare(); p.play() }
            2 -> p.seekToNext()
            else -> p.seekToPrevious()
        }
        count = 0
    }

    /** Returns true when the press was handled here. */
    fun onMediaButton(intent: Intent): Boolean {
        val event = keyEvent(intent) ?: return false
        if (event.keyCode != KeyEvent.KEYCODE_HEADSETHOOK && event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        handler.removeCallbacks(fire)
        count++
        if (count >= 3) fire.run() else handler.postDelayed(fire, WINDOW_MS)
        return true
    }

    fun release() = handler.removeCallbacks(fire)

    private fun keyEvent(intent: Intent): KeyEvent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)

    companion object {
        private const val WINDOW_MS = 450L
        private const val KEY = "headset_clicks"
        private var flow: MutableStateFlow<Boolean>? = null

        /** On by default; a switch in Settings turns it off. */
        fun enabled(context: Context): StateFlow<Boolean> =
            (flow ?: MutableStateFlow(prefs(context).getBoolean(KEY, true)).also { flow = it }).asStateFlow()

        fun setEnabled(context: Context, on: Boolean) {
            prefs(context).edit().putBoolean(KEY, on).apply()
            (flow ?: MutableStateFlow(on).also { flow = it }).value = on
        }

        private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
    }
}
