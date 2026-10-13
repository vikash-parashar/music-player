package com.vikash.mp3player.data

import android.net.Uri

/** One playable track, either a file on the phone or a stream from the online catalog. */
data class Song(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    /** Folder the file lives in, e.g. "Music/Bollywood". Empty for online tracks. */
    val folder: String = "",
    val durationMs: Long,
    val artworkUri: Uri?,
    val isOnline: Boolean = false,
    /** Set only for online tracks whose license allows downloading. */
    val downloadUrl: String? = null,
)
