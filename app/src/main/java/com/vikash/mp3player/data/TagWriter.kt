package com.vikash.mp3player.data

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import com.vikash.mp3player.R
import java.io.File

/** Saves new title, artist and album into an MP3 on the phone, and updates the Library. */
object TagWriter {

    sealed interface Result {
        data object Saved : Result
        /** Android wants the user to allow editing this file; launch it, then save again. */
        data class NeedsConsent(val sender: IntentSender) : Result
        /** Android 9 and below: the storage write permission is missing. */
        data object NeedsStoragePermission : Result
        data class Failed(val message: String) : Result
    }

    fun isEditable(context: Context, song: Song): Boolean =
        !song.isOnline && runCatching { context.contentResolver.getType(song.uri) }.getOrNull()
            .let { it == null || it == "audio/mpeg" || it == "audio/mp3" }

    fun write(context: Context, song: Song, title: String, artist: String, album: String): Result {
        val resolver = context.contentResolver
        if (!isEditable(context, song)) return Result.Failed(context.getString(R.string.tags_only_mp3))

        val original = File(context.cacheDir, "tag-original.mp3")
        val edited = File(context.cacheDir, "tag-edited.mp3")
        try {
            // 1. Copy the file and build the edited version in the cache, away from the real file.
            resolver.openInputStream(song.uri)?.use { input -> original.outputStream().use { input.copyTo(it) } }
                ?: return Result.Failed(context.getString(R.string.tags_open_failed))
            val frames = linkedMapOf("TIT2" to title, "TPE1" to artist, "TALB" to album)
            original.inputStream().buffered().use { input ->
                edited.outputStream().buffered().use { out -> Id3Editor.rewrite(input, out, frames) }
            }

            // 2. Ask for write access. Opening fails here, before anything is changed.
            val out = try {
                resolver.openOutputStream(song.uri, "wt")
            } catch (e: SecurityException) {
                return consentFor(context, song, e)
            } ?: return Result.Failed(context.getString(R.string.tags_save_failed))

            // 3. Write. If it breaks halfway, put the original back.
            val ok = runCatching { out.use { o -> edited.inputStream().use { it.copyTo(o) } } }.isSuccess
            if (!ok) {
                runCatching { resolver.openOutputStream(song.uri, "wt")?.use { o -> original.inputStream().use { it.copyTo(o) } } }
                return Result.Failed(context.getString(R.string.tags_save_failed_unchanged))
            }

            // 4. Tell the Library right away instead of waiting for Android's next scan.
            runCatching {
                resolver.update(song.uri, ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, title.trim())
                    put(MediaStore.Audio.Media.ARTIST, artist.trim())
                    put(MediaStore.Audio.Media.ALBUM, album.trim())
                }, null, null)
            }
            return Result.Saved
        } catch (e: Id3Editor.UnsupportedTagException) {
            return Result.Failed(context.getString(R.string.tags_unsupported))
        } catch (e: SecurityException) {
            return consentFor(context, song, e)
        } catch (e: Exception) {
            return Result.Failed(context.getString(R.string.tags_save_failed))
        } finally {
            original.delete()
            edited.delete()
        }
    }

    private fun consentFor(context: Context, song: Song, e: SecurityException): Result = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Result.NeedsConsent(MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri)).intentSender)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException ->
            Result.NeedsConsent(e.userAction.actionIntent.intentSender)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> Result.NeedsStoragePermission
        else -> Result.Failed(context.getString(R.string.tags_not_allowed))
    }
}
