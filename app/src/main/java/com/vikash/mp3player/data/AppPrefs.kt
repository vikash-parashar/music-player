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

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_FAVORITES = "favorites"
    }
}
