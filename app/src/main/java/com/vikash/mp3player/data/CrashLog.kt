package com.vikash.mp3player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.vikash.mp3player.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Keeps the last crash on the phone so the user can choose to send it on the next launch.
 * Nothing is sent automatically. Play Console's Android vitals also lists crashes from Play
 * installs, so no crash SDK (and no extra data collection) is needed.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val MAX_TRACE = 20_000

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(app.filesDir, FILE).writeText(
                    buildString {
                        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
                        appendLine("Thread: ${thread.name}")
                        appendLine("Time: ${Date()}")
                        appendLine()
                        append(trace.take(MAX_TRACE))
                    }
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved report from the last crash, or null. */
    fun pending(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    fun emailIntent(report: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            val to = BuildConfig.SUPPORT_EMAIL.takeIf { it.isNotBlank() }?.let { arrayOf(it) } ?: emptyArray()
            putExtra(Intent.EXTRA_EMAIL, to)
            putExtra(Intent.EXTRA_SUBJECT, "[Ampli MP3] Crash report ${BuildConfig.VERSION_NAME}")
            putExtra(Intent.EXTRA_TEXT, report)
        }
}
