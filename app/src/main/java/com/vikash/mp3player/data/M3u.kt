package com.vikash.mp3player.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * M3U playlist files, so playlists move to and from other players (Musicolet, Poweramp, VLC,
 * a PC). Export writes "#EXTINF" lines plus each song's folder/file path; import matches entries
 * against the phone's library by file name, then by "artist - title".
 */
object M3u {

    fun export(context: Context, target: Uri, songs: List<Song>): Boolean = runCatching {
        context.contentResolver.openOutputStream(target, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { out ->
            out.write("#EXTM3U\n")
            songs.filterNot { it.isOnline }.forEach { s ->
                out.write("#EXTINF:${s.durationMs / 1000},${s.artist} - ${s.title}\n")
                val name = s.fileName.ifBlank { s.title }
                val path = if (s.folder.isBlank() || s.folder == INTERNAL_STORAGE) name else "${s.folder.trimEnd('/')}/$name"
                out.write("$path\n")
            }
        } ?: error("no stream")
        true
    }.getOrDefault(false)

    /** The playlist name (from the file name) and the library songs it lists, in order. */
    fun import(context: Context, source: Uri, library: List<Song>): Pair<String, List<Song>>? = runCatching {
        val lines = context.contentResolver.openInputStream(source)?.bufferedReader(Charsets.UTF_8)?.use { it.readLines() }
            ?: return null
        val byFile = library.filter { it.fileName.isNotBlank() }.groupBy { it.fileName.lowercase() }
        val byLabel = library.groupBy { "${it.artist} - ${it.title}".lowercase() }
        val found = mutableListOf<Song>()
        var pendingLabel: String? = null
        for (raw in lines) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingLabel = line.substringAfter(',', "").trim()
                line.startsWith("#") -> Unit
                else -> {
                    val file = line.replace('\\', '/').substringAfterLast('/').let { Uri.decode(it) }.lowercase()
                    val song = byFile[file]?.firstOrNull() ?: pendingLabel?.let { byLabel[it.lowercase()]?.firstOrNull() }
                    if (song != null && song !in found) found += song
                    pendingLabel = null
                }
            }
        }
        displayName(context, source).substringBeforeLast('.').ifBlank { "Imported" } to found
    }.getOrNull()

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull().orEmpty()
}
