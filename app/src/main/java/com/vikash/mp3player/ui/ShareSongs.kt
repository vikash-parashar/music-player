package com.vikash.mp3player.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Song

/**
 * Sends song files to other apps (WhatsApp, Instagram, Gmail, Drive, Bluetooth...) through the
 * system share sheet. Local songs go as their MediaStore content URIs with a read grant, so no
 * copying or FileProvider is needed. Online tracks have no file, so they are skipped.
 */
fun shareSongs(context: Context, songs: List<Song>) {
    val local = songs.filter { !it.isOnline }
    if (local.isEmpty()) {
        Toast.makeText(context, R.string.share_no_files, Toast.LENGTH_SHORT).show()
        return
    }
    val uris = ArrayList<Uri>(local.map { it.uri })
    val type = if (local.size == 1) context.contentResolver.getType(local[0].uri) ?: "audio/*" else "audio/*"
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.apply {
        setType(type)
        putExtra(Intent.EXTRA_SUBJECT, local.joinToString(", ") { it.title }.take(200))
        // ClipData carries the read grant to the receiving app on Android 10+.
        clipData = ClipData.newRawUri(null, uris[0]).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
        .onFailure { Toast.makeText(context, R.string.share_no_app, Toast.LENGTH_SHORT).show() }
}
