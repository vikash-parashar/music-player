package com.vikash.mp3player.monetization

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/**
 * "Watch an ad, try Pro free for 24 hours". The trial unlocks the Pro sound and themes (not ad
 * removal) and ends by itself; people who have tried Pro buy it more often.
 */
object ProTrial {
    const val HOURS = 24
    private const val PREFS = "pro_trial"
    private const val KEY_UNTIL = "until"

    private val _until = MutableStateFlow(0L)
    /** When the trial ends (epoch ms), 0 if never started. */
    val until: StateFlow<Long> = _until.asStateFlow()

    fun init(context: Context) {
        _until.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UNTIL, 0L)
    }

    fun isActive(now: Long = System.currentTimeMillis()) = now < _until.value

    fun grant(context: Context, now: Long = System.currentTimeMillis()) {
        val end = now + HOURS * 60L * 60L * 1000L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_UNTIL, end).apply()
        _until.value = end
    }

    /** True while a trial runs; flips back to false the moment it ends. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val active: Flow<Boolean> = _until.transformLatest { end ->
        val left = end - System.currentTimeMillis()
        if (left > 0) {
            emit(true)
            delay(left)
        }
        emit(false)
    }.distinctUntilChanged()
}
