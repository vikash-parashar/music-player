package com.vikash.mp3player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/** A saved spot in a track, e.g. a chapter start in a long audiobook or a favourite part of a mix. */
@Serializable
data class Bookmark(val positionMs: Long, val note: String = "")

/** Bookmarks per song id, saved as JSON in the app's files. */
class BookmarkStore(context: Context) {

    private val file = File(context.filesDir, "bookmarks.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ListSerializer(Bookmark.serializer()))

    private val _all = MutableStateFlow(load())
    val all: StateFlow<Map<String, List<Bookmark>>> = _all.asStateFlow()

    fun add(songId: String, positionMs: Long, note: String) {
        val list = (_all.value[songId].orEmpty() + Bookmark(positionMs, note.trim())).sortedBy { it.positionMs }
        save(_all.value + (songId to list))
    }

    fun remove(songId: String, bookmark: Bookmark) {
        val list = _all.value[songId].orEmpty() - bookmark
        save(if (list.isEmpty()) _all.value - songId else _all.value + (songId to list))
    }

    private fun load(): Map<String, List<Bookmark>> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())

    private fun save(map: Map<String, List<Bookmark>>) {
        _all.value = map
        runCatching { file.writeText(json.encodeToString(serializer, map)) }
    }
}
