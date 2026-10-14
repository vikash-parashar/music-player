package com.vikash.mp3player.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.vikash.mp3player.R

/**
 * Saves an online track into the phone's Music/MP3 Player folder using the system
 * DownloadManager, so downloads survive the app closing and show progress in the
 * notification shade. Finished files are indexed by Android and appear in the Library tab.
 */
class SongDownloader(private val context: Context) {

    private val manager = context.getSystemService(DownloadManager::class.java)

    /** [folder] is a path under Music, e.g. "Audiobooks/Gitanjali" for spoken audio. */
    fun download(song: Song, folder: String = DEFAULT_FOLDER): Long {
        val url = requireNotNull(song.downloadUrl) { context.getString(R.string.online_license_no_download) }
        val ext = Uri.parse(url).lastPathSegment?.substringAfterLast('.', "")?.lowercase()
            ?.takeIf { it in setOf("mp3", "m4a", "aac", "ogg", "opus", "flac") } ?: "mp3"
        val mime = when (ext) { "m4a", "aac" -> "audio/mp4"; "ogg", "opus" -> "audio/ogg"; "flac" -> "audio/flac"; else -> "audio/mpeg" }
        val fileName = "${song.artist} - ${song.title}".safeName().take(120) + ".$ext"
        val dir = folder.split('/').joinToString("/") { it.safeName().take(60).trim().ifEmpty { "_" } }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(song.title)
            .setDescription(song.artist)
            .setMimeType(mime)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "$dir/$fileName")
        return manager.enqueue(request)
    }

    private fun String.safeName() = replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {
        const val DEFAULT_FOLDER = "MP3 Player"
    }
}
