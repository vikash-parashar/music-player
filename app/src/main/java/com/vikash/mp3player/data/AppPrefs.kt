package com.vikash.mp3player.data

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.StringRes
import com.vikash.mp3player.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the Songs list (and folder, album, artist lists) are ordered. */
enum class SongSort(@StringRes val labelRes: Int) {
    Title(R.string.sort_title), Artist(R.string.sort_artist), Recent(R.string.sort_recent), Longest(R.string.sort_longest);

    fun apply(songs: List<com.vikash.mp3player.data.Song>): List<com.vikash.mp3player.data.Song> = when (this) {
        Title -> songs.sortedBy { it.title.lowercase() }
        Artist -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        Recent -> songs.sortedByDescending { it.dateAddedSec }
        Longest -> songs.sortedByDescending { it.durationMs }
    }
}

enum class ThemeMode(@StringRes val labelRes: Int) {
    System(R.string.settings_theme_auto), Light(R.string.settings_theme_day), Dark(R.string.settings_theme_night)
}

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

    /** Pro: pure black backgrounds in the dark theme (saves battery on AMOLED screens). */
    private val _amoledBlack = MutableStateFlow(prefs.getBoolean(KEY_AMOLED, false))
    val amoledBlack: StateFlow<Boolean> = _amoledBlack.asStateFlow()

    fun setAmoledBlack(on: Boolean) {
        _amoledBlack.value = on
        prefs.edit().putBoolean(KEY_AMOLED, on).apply()
    }

    /** Robot arm CD change animation when a song is tapped. */
    private val _discAnimation = MutableStateFlow(prefs.getBoolean(KEY_DISC_ANIM, true))
    val discAnimation: StateFlow<Boolean> = _discAnimation.asStateFlow()

    fun setDiscAnimation(on: Boolean) {
        _discAnimation.value = on
        prefs.edit().putBoolean(KEY_DISC_ANIM, on).apply()
    }

    /** Pro: colours taken from the phone's wallpaper (Android 12+), instead of Ampli rose. */
    private val _wallpaperColors = MutableStateFlow(prefs.getBoolean(KEY_WALLPAPER, false))
    val wallpaperColors: StateFlow<Boolean> = _wallpaperColors.asStateFlow()

    fun setWallpaperColors(on: Boolean) {
        _wallpaperColors.value = on
        prefs.edit().putBoolean(KEY_WALLPAPER, on).apply()
    }

    /**
     * Lite mode for phones with about 3 GB of RAM or less: the dance buddy, visualiser, spinning
     * discs and other looping animations rest, and transitions are shorter. The first value is
     * guessed from the phone's memory; after that the user's own choice in Settings is kept.
     */
    private val _liteMode = MutableStateFlow(readLiteMode(context))
    val liteMode: StateFlow<Boolean> = _liteMode.asStateFlow()

    fun setLiteMode(on: Boolean) {
        _liteMode.value = on
        prefs.edit().putBoolean(KEY_LITE, on).apply()
    }

    /** True once the first-run welcome screens were finished. */
    val welcomeDone: Boolean get() = prefs.getBoolean(KEY_WELCOME, false)

    fun setWelcomeDone() {
        val edit = prefs.edit().putBoolean(KEY_WELCOME, true)
        // A fresh install gets the "Switching from Wynk or Samsung Music?" screen next. Written
        // here so it survives the app being closed between welcome and that screen.
        if (!prefs.contains(KEY_SWITCH)) edit.putBoolean(KEY_SWITCH, false)
        edit.apply()
    }

    /**
     * True once the one-time "Switching from Wynk or Samsung Music?" screen was finished or
     * skipped. Users upgrading from a build that had already finished welcome never set the key,
     * so for them it falls back to [welcomeDone] (true) and the screen is not shown.
     */
    val switchScreenDone: Boolean get() = prefs.getBoolean(KEY_SWITCH, welcomeDone)

    fun setSwitchScreenDone() {
        prefs.edit().putBoolean(KEY_SWITCH, true).apply()
    }

    /** Checks the phone's memory only on the first run and saves the result. */
    private fun readLiteMode(context: Context): Boolean {
        if (prefs.contains(KEY_LITE)) return prefs.getBoolean(KEY_LITE, false)
        val detected = isLowMemoryPhone(context)
        prefs.edit().putBoolean(KEY_LITE, detected).apply()
        return detected
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
        const val KEY_WELCOME = "welcome_done"
        const val KEY_SWITCH = "switch_screen_done"
        const val KEY_AMOLED = "amoled_black"
        const val KEY_DISC_ANIM = "disc_animation"
        const val KEY_WALLPAPER = "wallpaper_colors"
        const val KEY_LITE = "lite_mode"
    }
}

/** About 3.2 GB: phones sold as "3 GB" report a little less than 3 GiB of total memory. */
private const val LITE_RAM_BYTES = 3_200_000_000L

/** True on Android Go phones and on phones with roughly 3 GB of RAM or less. */
fun isLowMemoryPhone(context: Context): Boolean = runCatching {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    if (am.isLowRamDevice) return@runCatching true
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    info.totalMem in 1..LITE_RAM_BYTES
}.getOrDefault(false)
