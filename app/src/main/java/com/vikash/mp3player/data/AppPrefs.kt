package com.vikash.mp3player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** Re-reads everything after a restore from backup, then applies the matched favorites. */
    fun reloadAfterRestore(favorites: List<String>) {
        _themeMode.value = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.System)
        _seekStyle.value = prefs.getString(KEY_SEEK_STYLE, null) ?: "Classic"
        _favorites.value = favorites
        prefs.edit().putString(KEY_FAVORITES, favorites.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_FAVORITES = "favorites"
        const val KEY_SEEK_STYLE = "seek_style"
    }
}
