package com.vikash.mp3player.data

import android.content.Context

/** Launch counts and timestamps that decide when to ask for a Play rating or check for updates. */
class Engagement(context: Context) {
    private val prefs = context.getSharedPreferences("ampli_engagement", Context.MODE_PRIVATE)

    fun recordLaunch(now: Long = System.currentTimeMillis()) {
        val edit = prefs.edit().putInt(KEY_LAUNCHES, prefs.getInt(KEY_LAUNCHES, 0) + 1)
        if (!prefs.contains(KEY_FIRST)) edit.putLong(KEY_FIRST, now)
        edit.apply()
    }

    /** After 5 launches over at least 3 days, and at most once a month (Play limits it further). */
    fun shouldAskReview(now: Long = System.currentTimeMillis()): Boolean =
        prefs.getInt(KEY_LAUNCHES, 0) >= 5 &&
            now - prefs.getLong(KEY_FIRST, now) >= 3 * DAY &&
            now - prefs.getLong(KEY_REVIEW_ASKED, 0L) >= 30 * DAY

    fun markReviewAsked(now: Long = System.currentTimeMillis()) =
        prefs.edit().putLong(KEY_REVIEW_ASKED, now).apply()

    /** The Play update prompt shows at most once a day. */
    fun shouldCheckUpdate(now: Long = System.currentTimeMillis()): Boolean =
        now - prefs.getLong(KEY_UPDATE_CHECKED, 0L) >= DAY

    fun markUpdateChecked(now: Long = System.currentTimeMillis()) =
        prefs.edit().putLong(KEY_UPDATE_CHECKED, now).apply()

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val KEY_LAUNCHES = "launches"
        const val KEY_FIRST = "first_launch"
        const val KEY_REVIEW_ASKED = "review_asked"
        const val KEY_UPDATE_CHECKED = "update_checked"
    }
}
