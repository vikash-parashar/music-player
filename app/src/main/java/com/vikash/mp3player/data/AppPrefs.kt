package com.vikash.mp3player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the Songs list (and folder, album, artist lists) are ordered. */
enum class SongSort(val label: String) {
    Title("Title"), Artist("Artist"), Recent("Recently added"), Longest("Longest first");

    fun apply(songs: List<com.vikash.mp3player.data.Song>): List<com.vikash.mp3player.data.Song> = when (this) {
        Title -> songs.sortedBy { it.title.lowercase() }
        Artist -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        Recent -> songs.sortedByDescending { it.dateAddedSec }
        Longest -> songs.sortedByDescending { it.durationMs }
    }
}

enum class ThemeMode(val label: String) { System("Auto"), Light("Day"), Dark("Night") }

/** Small settings + the "My Favorites" playlist, kept in SharedPreferences. */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.System)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    /** Name of the chosen SeekStyle for the Now Playing progress bar. */
    private val _seekStyle = MutableStateFlow(prefs.getString(KEY_SEEK_STYLE, null) ?: "Classic")
    val seekStyle: StateFlow<String> = _seekStyle.asStateFlow()

    fun setSeekStyle(name: String) {
        _seekStyle.value = name
        prefs.edit().putString(KEY_SEEK_STYLE, name).apply()
    }

    /** Song ids, most recently liked first. */
    private val _favorites = MutableStateFlow(
        prefs.getString(KEY_FAVORITES, "").orEmpty().split('\n').filter { it.isNotBlank() }
    )
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    fun isFavorite(id: String) = id in _favorites.value

    /** Returns true when the song is now a favorite. */
    fun toggleFavorite(id: String): Boolean {
        val now = _favorites.value
        val added = id !in now
        val next = if (added) listOf(id) + now else now - id
        _favorites.value = next
        prefs.edit().putString(KEY_FAVORITES, next.joinToString("\n")).apply()
        return added
    }

    private val _songSort = MutableStateFlow(readSort())
    val songSort: StateFlow<SongSort> = _songSort.asStateFlow()

    fun setSongSort(sort: SongSort) {
        _songSort.value = sort
        prefs.edit().putString(KEY_SORT, sort.name).apply()
    }

    /** Folders the user hid from the Library (WhatsApp audio, call recordings...). */
    private val _hiddenFolders = MutableStateFlow(readHidden())
    val hiddenFolders: StateFlow<Set<String>> = _hiddenFolders.asStateFlow()

    fun setFolderHidden(folder: String, hidden: Boolean) {
        val next = if (hidden) _hiddenFolders.value + folder else _hiddenFolders.value - folder
        _hiddenFolders.value = next
        prefs.edit().putString(KEY_HIDDEN, next.joinToString("\n")).apply()
    }

    private fun readSort() = runCatching { SongSort.valueOf(prefs.getString(KEY_SORT, null) ?: "") }.getOrDefault(SongSort.Title)
    private fun readHidden() = prefs.getString(KEY_HIDDEN, "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    /** Re-reads everything after a restore from backup, then applies the matched favorites. */
    fun reloadAfterRestore(favorites: List<String>) {
        _themeMode.value = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.System)
        _seekStyle.value = prefs.getString(KEY_SEEK_STYLE, null) ?: "Classic"
        _songSort.value = readSort()
        _hiddenFolders.value = readHidden()
        _favorites.value = favorites
        prefs.edit().putString(KEY_FAVORITES, favorites.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_FAVORITES = "favorites"
        const val KEY_SEEK_STYLE = "seek_style"
        const val KEY_SORT = "song_sort"
        const val KEY_HIDDEN = "hidden_folders"
    }
}
