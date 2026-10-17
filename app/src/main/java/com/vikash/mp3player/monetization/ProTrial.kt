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

    private const val LENGTH_MS = HOURS * 60L * 60L * 1000L

    fun init(context: Context) {
        val end = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UNTIL, 0L)
        // More than 24 h left means the clock was set back: end the trial instead of stretching it.
        _until.value = if (end - System.currentTimeMillis() > LENGTH_MS) 0L else end
    }

    fun isActive(now: Long = System.currentTimeMillis()) = now < _until.value && _until.value - now <= LENGTH_MS

    fun grant(context: Context, now: Long = System.currentTimeMillis()) {
        val end = now + LENGTH_MS
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_UNTIL, end).apply()
        _until.value = end
        com.vikash.mp3player.data.OwnerHooks.event("pro_trial")
    }

    /** True while a trial runs; flips back to false the moment it ends. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val active: Flow<Boolean> = _until.transformLatest { end ->
        val left = end - System.currentTimeMillis()
        if (left in 1..LENGTH_MS) {
            emit(true)
            delay(left)
        }
        emit(false)
    }.distinctUntilChanged()
}
