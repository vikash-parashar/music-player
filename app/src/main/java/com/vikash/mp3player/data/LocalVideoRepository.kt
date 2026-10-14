package com.vikash.mp3player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.vikash.mp3player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One video file on the phone: camera recordings, WhatsApp videos, downloads from other apps. */
data class Video(
    val id: Long,
    val uri: Uri,
    val title: String,
    /** Folder the file lives in, e.g. "DCIM/Camera" or "Snaptube/download". */
    val folder: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
)

/** Reads every video Android has indexed, newest first. */
class LocalVideoRepository(private val context: Context) {

    suspend fun loadVideos(): List<Video> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            folderColumn,
        )
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val videos = mutableListOf<Video>()
        context.contentResolver.query(collection, projection, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val folderCol = c.getColumnIndexOrThrow(folderColumn)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                videos += Video(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(nameCol)?.substringBeforeLast('.') ?: context.getString(R.string.videos_untitled),
                    folder = folderOf(c.getString(folderCol).orEmpty()),
                    durationMs = c.getLong(durationCol),
                    sizeBytes = c.getLong(sizeCol),
                    dateAddedSec = c.getLong(dateCol),
                )
            }
        }
        videos
    }

    @Suppress("DEPRECATION")
    private val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.RELATIVE_PATH
    } else {
        MediaStore.Video.Media.DATA
    }

    private fun folderOf(raw: String): String {
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) raw.trimEnd('/')
        else raw.substringBeforeLast('/').removePrefix("/storage/emulated/0/")
        return path.ifBlank { INTERNAL_STORAGE }
    }
}
