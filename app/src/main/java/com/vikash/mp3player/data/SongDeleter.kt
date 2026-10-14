package com.vikash.mp3player.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import com.vikash.mp3player.R

/** Deletes songs from the phone. Android asks the user first for files other apps made. */
object SongDeleter {

    sealed interface Result {
        data class Deleted(val count: Int) : Result
        /** Show Android's "Allow Ampli to delete?" prompt. On Android 11+ the prompt deletes the files itself. */
        data class NeedsConsent(val sender: IntentSender, val deletesItself: Boolean) : Result
        data object NeedsStoragePermission : Result
        data class Failed(val message: String) : Result
    }

    /** Android 11+ shows its own confirmation listing the files, so Ampli doesn't need to ask twice. */
    val systemConfirms: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun delete(context: Context, songs: List<Song>): Result {
        val local = songs.filter { !it.isOnline }
        if (local.isEmpty()) return Result.Failed(context.getString(R.string.delete_only_local))
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createDeleteRequest(resolver, local.map { it.uri })
            return Result.NeedsConsent(request.intentSender, deletesItself = true)
        }
        var deleted = 0
        for (song in local) {
            try {
                deleted += resolver.delete(song.uri, null, null)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    // Android 10 asks per file; after the OK the same delete is tried again.
                    return Result.NeedsConsent(e.userAction.actionIntent.intentSender, deletesItself = false)
                }
                return if (deleted > 0) Result.Deleted(deleted) else Result.NeedsStoragePermission
            } catch (e: Exception) {
                return if (deleted > 0) Result.Deleted(deleted) else Result.Failed(context.getString(R.string.delete_failed))
            }
        }
        return Result.Deleted(deleted)
    }
}
