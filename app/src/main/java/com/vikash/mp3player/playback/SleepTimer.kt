package com.vikash.mp3player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared between the UI and [PlaybackService]; the service pauses playback when it fires. */
object SleepTimer {
    private val _endsAtMillis = MutableStateFlow<Long?>(null)
    val endsAtMillis: StateFlow<Long?> = _endsAtMillis.asStateFlow()

    fun start(minutes: Int) {
        _endsAtMillis.value = System.currentTimeMillis() + minutes * 60_000L
    }

    fun cancel() {
        _endsAtMillis.value = null
    }
}
