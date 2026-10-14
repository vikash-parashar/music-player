package com.vikash.mp3player.data

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Moves songs to another folder. Android asks the user first for files other apps made. */
object SongMover {

    sealed interface Result {
        data class Moved(val count: Int) : Result
        /** Show Android's "Allow Ampli to modify?" prompt, then call [move] again. */
        data class NeedsConsent(val sender: IntentSender) : Result
        data object NeedsStoragePermission : Result
        data class Failed(val count: Int) : Result
    }

    /** Top folders Android lets apps put music in (Android 10+). */
    private val allowedRoots = listOf(
        Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_PODCASTS, Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_RINGTONES, Environment.DIRECTORY_ALARMS, Environment.DIRECTORY_NOTIFICATIONS,
    ) + (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listOf(Environment.DIRECTORY_AUDIOBOOKS) else emptyList())

    /** True when songs can be moved into [folder] (e.g. "Music/Bollywood"). */
    fun canMoveTo(folder: String): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || allowedRoots.any { folder == it || folder.startsWith("$it/") }

    /** Cleans a folder the user typed: "  Music//Old songs/ " -> "Music/Old songs". */
    fun normalize(folder: String): String =
        folder.split('/').map { it.trim().replace(Regex("[\\\\:*?\"<>|]"), "_") }.filter { it.isNotEmpty() }.joinToString("/")

    /** Android 11+: one system prompt covers all the files; returns null when no prompt is needed. */
    fun consentRequest(context: Context, songs: List<Song>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            MediaStore.createWriteRequest(context.contentResolver, songs.filter { !it.isOnline }.map { it.uri }).intentSender
        else null

    fun move(context: Context, songs: List<Song>, folder: String): Result {
        val target = normalize(folder)
        val local = songs.filter { !it.isOnline && it.folder != target }
        if (local.isEmpty()) return Result.Moved(0)
        var moved = 0
        for (song in local) {
            try {
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply { put(MediaStore.Audio.Media.RELATIVE_PATH, "$target/") }
                    context.contentResolver.update(song.uri, values, null, null) > 0
                } else {
                    moveLegacy(context, song, target)
                }
                if (ok) moved++
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    // Android 10 asks file by file; files moved so far stay moved.
                    return Result.NeedsConsent(e.userAction.actionIntent.intentSender)
                }
                return if (moved > 0) Result.Moved(moved) else Result.NeedsStoragePermission
            } catch (_: Exception) {
                // Skip this one and carry on with the rest.
            }
        }
        return if (moved == 0) Result.Failed(local.size) else Result.Moved(moved)
    }

    /** Android 9 and older: rename the file and tell MediaStore where it went. */
    @Suppress("DEPRECATION")
    private fun moveLegacy(context: Context, song: Song, target: String): Boolean {
        val path = context.contentResolver.query(song.uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: return false
        val source = File(path)
        val dir = File(Environment.getExternalStorageDirectory(), target).apply { mkdirs() }
        var dest = File(dir, source.name)
        var n = 2
        while (dest.exists()) dest = File(dir, source.nameWithoutExtension + " ($n)." + source.extension).also { n++ }
        if (!source.renameTo(dest)) return false
        val values = ContentValues().apply { put(MediaStore.Audio.Media.DATA, dest.absolutePath) }
        context.contentResolver.update(song.uri, values, null, null)
        return true
    }
}
