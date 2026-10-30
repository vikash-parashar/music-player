package com.vikash.mp3player.ui

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lets another app (Ampli Car Hub on a car head unit) open the player straight into Car mode and
 * start the music:
 *
 *     Intent("com.vikash.ampli.action.CAR_MODE").setPackage("com.vikash.ampli")
 *
 * If the intent can't be resolved, the caller sends the driver to the Play listing instead.
 */
object CarLaunch {
    const val ACTION_CAR_MODE = "com.vikash.ampli.action.CAR_MODE"

    /** Set true to start playing (resume, or shuffle everything) once Car mode is open. Default true. */
    const val EXTRA_AUTOPLAY = "autoplay"

    /**
     * Optional spoken song name ("Hey Ampli, play Kesariya"). The best-matching local song,
     * artist or album plays; with no match it falls back to normal autoplay.
     */
    const val EXTRA_QUERY = "query"

    private val _requests = MutableStateFlow(0)
    /** Goes up by one for every Car mode launch from outside. */
    val requests: StateFlow<Int> = _requests.asStateFlow()

    /** True until the app has acted on the latest launch's autoplay. */
    @Volatile var autoplayPending = false

    /** The latest launch's [EXTRA_QUERY], until the app has acted on it. */
    @Volatile var queryPending: String? = null

    /** Call from the activity with every intent it receives (onCreate and onNewIntent). */
    fun handle(intent: Intent?) {
        if (intent?.action != ACTION_CAR_MODE) return
        autoplayPending = intent.getBooleanExtra(EXTRA_AUTOPLAY, true)
        queryPending = intent.getStringExtra(EXTRA_QUERY)?.trim()?.takeIf { it.isNotEmpty() }
        _requests.value += 1
        intent.action = null // a later rotation or recreation must not count as a new launch
    }
}
