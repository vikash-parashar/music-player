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
    /** When the file was added to the phone (seconds), for "Recently added". 0 for online tracks. */
    val dateAddedSec: Long = 0,
    /** File size in bytes, 0 if unknown. Used to keep the best copy when cleaning duplicates. */
    val sizeBytes: Long = 0,
)
