package com.vikash.mp3player.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.vikash.mp3player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Saves favorites, settings, equalizer and listening history to one file the user picks
 * (phone storage, Google Drive...), and loads it back, also on a new phone.
 *
 * Song ids come from the phone's media index and change on a new phone, so every song is
 * also saved by title and artist and matched again on restore.
 */
object Backup {

    private const val VERSION = 1
    private val PREF_FILES = listOf("ampli_prefs", "equalizer")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    private data class PrefValue(val t: String, val v: String)

    @Serializable
    private data class SongRef(val id: String, val title: String, val artist: String)

    @Serializable
    private data class BackupFile(
        val app: String = "Ampli",
        val version: Int = VERSION,
        val createdMs: Long = 0,
        val prefs: Map<String, Map<String, PrefValue>> = emptyMap(),
        val favorites: List<SongRef> = emptyList(),
        val stats: StatsData = StatsData(),
        val playlists: List<PlaylistRef> = emptyList(),
    )

    @Serializable
    private data class PlaylistRef(val name: String, val songs: List<SongRef>)

    /** What a restore brings back, with song ids matched to this phone. */
    data class Restored(val stats: StatsData, val favorites: List<String>, val playlists: List<UserPlaylist>)

    suspend fun export(
        context: Context, target: Uri, songs: List<Song>, stats: StatsData, favorites: List<String>,
        playlists: List<UserPlaylist>,
    ) =
        withContext(Dispatchers.IO) {
            val byId = songs.associateBy { it.id }
            val file = BackupFile(
                createdMs = System.currentTimeMillis(),
                prefs = PREF_FILES.associateWith { name -> dump(context.getSharedPreferences(name, Context.MODE_PRIVATE)) },
                favorites = favorites.mapNotNull { id -> byId[id]?.let { SongRef(id, it.title, it.artist) } },
                stats = stats,
                playlists = playlists.map { p ->
                    PlaylistRef(p.name, p.songIds.mapNotNull { id -> byId[id]?.let { SongRef(id, it.title, it.artist) } })
                },
            )
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(json.encodeToString(file).toByteArray()) }
                ?: error(context.getString(R.string.backup_write_failed))
        }

    /** Returns the restored listening history, favorites and playlists, with ids matched to this phone's songs. */
    suspend fun restore(context: Context, source: Uri, songs: List<Song>): Restored =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
                ?: error(context.getString(R.string.backup_open_failed))
            val file = runCatching { json.decodeFromString<BackupFile>(text) }.getOrNull()
                ?.takeIf { it.app == "Ampli" } ?: error(context.getString(R.string.backup_not_ampli))

            file.prefs.forEach { (name, values) ->
                if (name in PREF_FILES) load(context.getSharedPreferences(name, Context.MODE_PRIVATE), values)
            }

            val ids = songs.associateBy { it.id }
            val byName = songs.associateBy { matchKey(it.title, it.artist) }
            fun localId(id: String, title: String, artist: String) =
                if (id in ids) id else byName[matchKey(title, artist)]?.id

            val favorites = file.favorites.mapNotNull { localId(it.id, it.title, it.artist) }.distinct()
            val stats = file.stats.copy(
                songs = file.stats.songs.mapNotNull { (id, s) ->
                    // Online tracks keep their id; local ones are matched by title and artist.
                    val newId = if (id.startsWith("local:")) localId(id, s.title, s.artist) else id
                    newId?.let { it to s }
                }.toMap(),
            )
            val playlists = file.playlists.map { p ->
                UserPlaylist(
                    java.util.UUID.randomUUID().toString(), p.name,
                    p.songs.mapNotNull { localId(it.id, it.title, it.artist) }.distinct(),
                )
            }
            Restored(stats, favorites, playlists)
        }

    private fun matchKey(title: String, artist: String) = "${title.trim().lowercase()}|${artist.trim().lowercase()}"

    private fun dump(prefs: SharedPreferences): Map<String, PrefValue> = prefs.all.mapNotNull { (k, v) ->
        when (v) {
            is Boolean -> k to PrefValue("b", v.toString())
            is Int -> k to PrefValue("i", v.toString())
            is Long -> k to PrefValue("l", v.toString())
            is Float -> k to PrefValue("f", v.toString())
            is String -> k to PrefValue("s", v)
            else -> null
        }
    }.toMap()

    private fun load(prefs: SharedPreferences, values: Map<String, PrefValue>) {
        val edit = prefs.edit()
        values.forEach { (k, p) ->
            runCatching {
                when (p.t) {
                    "b" -> edit.putBoolean(k, p.v.toBoolean())
                    "i" -> edit.putInt(k, p.v.toInt())
                    "l" -> edit.putLong(k, p.v.toLong())
                    "f" -> edit.putFloat(k, p.v.toFloat())
                    "s" -> edit.putString(k, p.v)
                }
            }
        }
        edit.commit()
    }
}
