package com.vikash.mp3player.data

import android.content.Context

/**
 * Which short clips the library leaves out. Indian phones fill up with WhatsApp voice notes and
 * call recordings; they are audio files too, so without this they crowd the Songs list.
 * Read straight from SharedPreferences so the repository and Settings agree without wiring.
 */
object LibraryFilter {
    private const val PREFS = "ampli_prefs"
    private const val KEY_MIN_SEC = "library_min_seconds"
    private const val KEY_HIDE_RECORDINGS = "library_hide_recordings"

    /** Choices offered in Settings, in seconds. */
    val MIN_CHOICES = listOf(10, 30, 60)
    const val DEFAULT_MIN_SEC = 30

    fun minSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MIN_SEC, DEFAULT_MIN_SEC)

    fun setMinSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_MIN_SEC, seconds).apply()
    }

    fun hideRecordings(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_RECORDINGS, true)

    fun setHideRecordings(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_RECORDINGS, on).apply()
    }

    // Folder names used by WhatsApp, the stock dialers (Samsung, Xiaomi, OnePlus, Oppo/Realme,
    // Vivo, Google) and recorder apps. Matched case-insensitively against the song's folder path.
    private val RECORDING_FOLDERS = listOf(
        "voice notes", "voice recorder", "call recordings", "callrecordings", "call_rec",
        "callrecord", "recordings/call", "sound_recorder", "record/call",
        "phonerecord", "voicerecorder",
    )

    fun isRecordingFolder(folder: String): Boolean {
        val f = folder.lowercase()
        return RECORDING_FOLDERS.any { it in f }
    }
}
