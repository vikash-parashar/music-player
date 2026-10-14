package com.vikash.mp3player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A playlist the user made. [songIds] keeps the order songs were added in. */
@Serializable
data class UserPlaylist(val id: String, val name: String, val songIds: List<String> = emptyList())

/** The user's own playlists, saved as JSON in the app's files. */
class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlists.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    /** Creates a playlist (optionally with songs) and returns its id. */
    fun create(name: String, songIds: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        save(_playlists.value + UserPlaylist(id, uniqueName(name.trim().ifEmpty { "New playlist" }), songIds.distinct()))
        return id
    }

    fun rename(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        save(_playlists.value.map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun delete(id: String) = save(_playlists.value.filterNot { it.id == id })

    /** Adds songs at the end, skipping ones already in the playlist. Returns how many were added. */
    fun add(id: String, songIds: List<String>): Int {
        var added = 0
        save(_playlists.value.map { p ->
            if (p.id != id) p else {
                val new = songIds.distinct().filterNot { it in p.songIds }
                added = new.size
                p.copy(songIds = p.songIds + new)
            }
        })
        return added
    }

    fun remove(id: String, songId: String) =
        save(_playlists.value.map { if (it.id == id) it.copy(songIds = it.songIds - songId) else it })

    /** Replaces everything, used by restore from backup. */
    fun replaceAll(list: List<UserPlaylist>) = save(list)

    private fun uniqueName(name: String): String {
        val taken = _playlists.value.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var n = 2
        while ("$name $n".lowercase() in taken) n++
        return "$name $n"
    }

    private fun load(): List<UserPlaylist> =
        runCatching { json.decodeFromString<List<UserPlaylist>>(file.readText()) }.getOrDefault(emptyList())

    private fun save(list: List<UserPlaylist>) {
        _playlists.value = list
        runCatching { file.writeText(json.encodeToString(list)) }
    }
}
