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
 * Free Pro time. Every new install gets [WELCOME_DAYS] days of full Pro with no ads (counted from
 * the day the app was installed), and after that a free user can watch one ad for another
 * [HOURS] hours. People who have lived with Pro for two weeks buy it far more often than people
 * who only read about it.
 */
object ProTrial {
    const val HOURS = 24
    const val WELCOME_DAYS = 14
    private const val PREFS = "pro_trial"
    private const val KEY_UNTIL = "until"

    private const val LENGTH_MS = HOURS * 60L * 60L * 1000L
    private const val WELCOME_MS = WELCOME_DAYS * 24L * 60L * 60L * 1000L

    private val _until = MutableStateFlow(0L)
    /** When free Pro ends (epoch ms), 0 if there is none. */
    val until: StateFlow<Long> = _until.asStateFlow()

    /** When the welcome trial ends (epoch ms); 0 if it could not be worked out. */
    @Volatile
    var welcomeUntil = 0L
        private set

    fun init(context: Context) {
        val now = System.currentTimeMillis()
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(0L)
        // A start time in the future means the clock was set back: no welcome trial then.
        welcomeUntil = if (installed in 1..now) installed + WELCOME_MS else 0L
        val rewarded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UNTIL, 0L)
        // More than 24 h left on an ad trial means the clock was set back: end it instead of stretching it.
        val ad = if (rewarded - now > LENGTH_MS) 0L else rewarded
        _until.value = maxOf(ad, welcomeUntil)
    }

    fun isActive(now: Long = System.currentTimeMillis()) = now < _until.value && _until.value - now <= WELCOME_MS

    /** True while the 14-day welcome trial is the free Pro that is running. */
    fun inWelcome(now: Long = System.currentTimeMillis()) = welcomeUntil > now && _until.value == welcomeUntil

    /** Whole days of free Pro left, rounded up (0 when none). */
    fun daysLeft(now: Long = System.currentTimeMillis()): Int {
        val left = _until.value - now
        return if (left <= 0) 0 else ((left + 86_399_999L) / 86_400_000L).toInt()
    }

    /** The rewarded-ad trial: 24 more hours of Pro, never shortening a longer trial already running. */
    fun grant(context: Context, now: Long = System.currentTimeMillis()) {
        val end = now + LENGTH_MS
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_UNTIL, end).apply()
        _until.value = maxOf(end, _until.value)
        com.vikash.mp3player.data.OwnerHooks.event("pro_trial")
    }

    /** True while a trial runs; flips back to false the moment it ends. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val active: Flow<Boolean> = _until.transformLatest { end ->
        val left = end - System.currentTimeMillis()
        if (left in 1..WELCOME_MS) {
            emit(true)
            delay(left)
        }
        emit(false)
    }.distinctUntilChanged()
}
