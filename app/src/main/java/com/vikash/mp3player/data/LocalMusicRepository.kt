package com.vikash.mp3player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.vikash.mp3player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads every music file Android has indexed (MP3 of any bitrate, CBR or VBR, plus M4A,
 * FLAC, OGG, WAV...) from all storage volumes, including the SD card and downloads.
 */
class LocalMusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            folderColumn,
        )
        // Skip ringtones, notification sounds and tiny clips.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf("10000")
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val unknownTitle = context.getString(R.string.library_unknown_title)
        val unknownArtist = context.getString(R.string.library_unknown_artist)
        val songs = mutableListOf<Song>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val folderCol = c.getColumnIndexOrThrow(folderColumn)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                songs += Song(
                    id = "local:$id",
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(titleCol) ?: unknownTitle,
                    artist = c.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: unknownArtist,
                    album = c.getString(albumCol) ?: "",
                    folder = folderOf(c.getString(folderCol).orEmpty()),
                    durationMs = c.getLong(durationCol),
                    artworkUri = ContentUris.withAppendedId(ALBUM_ART, c.getLong(albumIdCol)),
                    dateAddedSec = c.getLong(addedCol),
                    sizeBytes = c.getLong(sizeCol),
                    fileName = c.getString(nameCol).orEmpty(),
                )
            }
        }
        songs
    }

    // RELATIVE_PATH ("Music/Pop/") exists from Android 10; older versions only have the full file path.
    @Suppress("DEPRECATION")
    private val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.RELATIVE_PATH
    } else {
        MediaStore.Audio.Media.DATA
    }

    private fun folderOf(raw: String): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) raw.trimEnd('/')
        else raw.substringBeforeLast('/').removePrefix("/storage/emulated/0/")

    private companion object {
        val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
