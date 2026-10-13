package com.vikash.mp3player.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/**
 * Saves an online track into the phone's Music/MP3 Player folder using the system
 * DownloadManager, so downloads survive the app closing and show progress in the
 * notification shade. Finished files are indexed by Android and appear in the Library tab.
 */
class SongDownloader(context: Context) {

    private val manager = context.getSystemService(DownloadManager::class.java)

    fun download(song: Song): Long {
        val url = requireNotNull(song.downloadUrl) { "This track's license does not allow downloads" }
        val fileName = "${song.artist} - ${song.title}".replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120) + ".mp3"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(song.title)
            .setDescription(song.artist)
            .setMimeType("audio/mpeg")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "MP3 Player/$fileName")
        return manager.enqueue(request)
    }
}
