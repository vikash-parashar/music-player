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
    /** File name such as "Tum Hi Ho.mp3", empty for online tracks. Used for M3U playlists. */
    val fileName: String = "",
    /** Release year from the file's tags, 0 if unknown. Feeds the "Old is gold" mix in Car mode. */
    val year: Int = 0,
    /** Genre tag (Android 11+ reads it from MediaStore), empty if unknown. */
    val genre: String = "",
)

/** Group name for songs with no genre tag; the UI shows a translated label for it. */
const val UNKNOWN_GENRE = "Unknown genre"

/**
 * Group name for songs with no folder (files in the storage root). Kept in English because it is
 * also saved in the hidden-folders setting; the UI shows a translated label for it.
 */
const val INTERNAL_STORAGE = "Internal storage"

/** Group name for songs with no album tag; the UI shows a translated label for it. */
const val UNKNOWN_ALBUM = "Unknown album"
