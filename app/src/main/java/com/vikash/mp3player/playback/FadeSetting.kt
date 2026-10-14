package com.vikash.mp3player.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Fade between songs": the end of each song fades out and the next one fades in, so
 * tracks don't stop or start abruptly. 0 = off. Saved in ampli_prefs, so backups include it.
 */
object FadeSetting {
    val CHOICES = listOf(0, 2, 4, 6, 8, 12)
    private const val KEY = "fade_seconds"
    private val _seconds = MutableStateFlow(-1)

    fun seconds(context: Context): StateFlow<Int> {
        if (_seconds.value < 0) reload(context)
        return _seconds.asStateFlow()
    }

    fun set(context: Context, seconds: Int) {
        _seconds.value = seconds
        prefs(context).edit().putInt(KEY, seconds).apply()
    }

    /** Re-reads the saved value, e.g. after a restore from backup. */
    fun reload(context: Context) {
        _seconds.value = prefs(context).getInt(KEY, 0)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
}
