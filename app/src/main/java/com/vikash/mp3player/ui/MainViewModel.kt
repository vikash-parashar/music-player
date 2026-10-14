package com.vikash.mp3player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vikash.mp3player.MusicApp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.INTERNAL_STORAGE
import com.vikash.mp3player.data.Backup
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.playback.EqualizerController
import com.vikash.mp3player.playback.PlayerConnection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryState(
    val loading: Boolean = false,
    val songs: List<Song> = emptyList(),
)

/** A-B repeat: loop the part of the song between [aMs] and [bMs]. */
data class AbLoop(val aMs: Long? = null, val bMs: Long? = null, val songId: String? = null) {
    val active get() = aMs != null && bMs != null
}

data class OnlineState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Song> = emptyList(),
    val error: String? = null,
    val configured: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val musicApp = app as MusicApp
    val player = PlayerConnection(app).also { it.connect() }
    val billing = musicApp.billing
    val prefs = musicApp.prefs
    /** Every song on the phone, before hidden folders are taken out. */
    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = combine(_library, prefs.hiddenFolders) { lib, hidden ->
        if (hidden.isEmpty()) lib else lib.copy(songs = lib.songs.filterNot { it.folder.ifBlank { INTERNAL_STORAGE } in hidden })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryState())

    /** All folders with their song counts, hidden ones included, for Settings. */
    val folders: StateFlow<List<Pair<String, Int>>> = _library.map { lib ->
        lib.songs.groupingBy { it.folder.ifBlank { INTERNAL_STORAGE } }.eachCount().toList().sortedBy { it.first.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _online = MutableStateFlow(OnlineState(configured = musicApp.jamendo.isConfigured))
    val online: StateFlow<OnlineState> = _online.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** A translated app string, for snackbar messages. */
    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    /** A translated "N songs"-style string. */
    private fun plural(id: Int, count: Int, vararg args: Any): String =
        getApplication<Application>().resources.getQuantityString(id, count, count, *args)

    /** Heart a song: adds it to (or removes it from) the "My Favorites" playlist. */
    fun toggleFavorite(id: String, title: String) {
        val added = prefs.toggleFavorite(id)
        _messages.tryEmit(if (added) str(R.string.msg_added_to_favorites, title) else str(R.string.msg_removed_from_favorites))
    }


    /** Hearts several songs at once (multi-select). Songs already liked stay as they are. */
    fun addFavorites(songs: List<Song>) {
        val new = songs.filterNot { prefs.isFavorite(it.id) }
        new.asReversed().forEach { prefs.toggleFavorite(it.id) }
        _messages.tryEmit(if (new.isEmpty()) str(R.string.msg_already_in_favorites) else plural(R.plurals.msg_added_songs_to_favorites, new.size))
    }

    // ------------------------------------------------------------------ your playlists

    val playlists = musicApp.playlists.playlists

    fun createPlaylist(name: String, songs: List<Song>) {
        musicApp.playlists.create(name, songs.map { it.id })
        _messages.tryEmit(if (songs.isEmpty()) str(R.string.msg_playlist_created) else plural(R.plurals.msg_playlist_created_with_songs, songs.size))
    }

    fun addToPlaylist(id: String, songs: List<Song>) {
        val name = playlists.value.firstOrNull { it.id == id }?.name ?: return
        val added = musicApp.playlists.add(id, songs.map { it.id })
        _messages.tryEmit(
            when (added) {
                0 -> str(R.string.msg_already_in_playlist, name)
                1 -> str(R.string.msg_added_to_playlist, name)
                else -> plural(R.plurals.msg_added_songs_to_playlist, added, name)
            }
        )
    }

    fun removeFromPlaylist(id: String, song: Song) {
        musicApp.playlists.remove(id, song.id)
        _messages.tryEmit(str(R.string.msg_removed_song, song.title))
    }

    fun renamePlaylist(id: String, name: String) = musicApp.playlists.rename(id, name)

    fun deletePlaylist(id: String) {
        musicApp.playlists.delete(id)
        _messages.tryEmit(str(R.string.msg_playlist_deleted))
    }

    /** Several songs at once, in list order (multi-select). */
    fun addAllToQueue(songs: List<Song>) {
        songs.forEach { player.addToQueue(it) }
        _messages.tryEmit(plural(R.plurals.msg_added_songs_to_queue, songs.size))
    }

    private var searchJob: Job? = null

    val stats = musicApp.playStats.data

    fun clearStats() {
        musicApp.playStats.clear()
        _messages.tryEmit(str(R.string.msg_history_cleared))
    }

    private val _abLoop = MutableStateFlow(AbLoop())
    val abLoop: StateFlow<AbLoop> = _abLoop.asStateFlow()
    private var abJob: Job? = null

    /** First tap marks A, second marks B and starts looping, third turns it off. */
    fun cycleAbRepeat() {
        val pos = player.positionMs()
        val songId = player.state.value.current?.mediaId
        val loop = _abLoop.value.let { if (it.songId != songId) AbLoop() else it }
        when {
            loop.aMs == null -> {
                _abLoop.value = AbLoop(aMs = pos, songId = songId)
                _messages.tryEmit(str(R.string.msg_ab_a_set))
            }
            loop.bMs == null -> {
                if (pos <= loop.aMs + 500) {
                    _messages.tryEmit(str(R.string.msg_ab_b_after_a))
                    return
                }
                _abLoop.value = loop.copy(bMs = pos)
                startAbLoop()
            }
            else -> clearAbRepeat()
        }
    }

    private fun clearAbRepeat() {
        abJob?.cancel()
        abJob = null
        _abLoop.value = AbLoop()
    }

    private fun startAbLoop() {
        abJob?.cancel()
        val songId = _abLoop.value.songId
        abJob = viewModelScope.launch {
            while (true) {
                val loop = _abLoop.value
                val a = loop.aMs ?: break
                val b = loop.bMs ?: break
                // A different song started: the loop belonged to the old one.
                if (player.state.value.current?.mediaId != songId) { clearAbRepeat(); break }
                if (player.positionMs() >= b) player.seekTo(a)
                delay(50)
            }
        }
    }

    private val _videos = MutableStateFlow(VideoState())
    val videos: StateFlow<VideoState> = _videos.asStateFlow()

    fun loadVideos() {
        viewModelScope.launch {
            _videos.update { it.copy(loading = true) }
            val list = runCatching { musicApp.localVideos.loadVideos() }.getOrDefault(emptyList())
            _videos.value = VideoState(loading = false, videos = list)
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _library.update { it.copy(loading = true) }
            val songs = runCatching { musicApp.localMusic.loadSongs() }.getOrDefault(emptyList())
            _library.value = LibraryState(loading = false, songs = songs)
        }
    }

    /** Debounced, so typing doesn't fire a request per keystroke. Blank shows popular tracks. */
    fun searchOnline(query: String, debounce: Boolean = true) {
        _online.update { it.copy(query = query) }
        if (!musicApp.jamendo.isConfigured) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(400)
            _online.update { it.copy(loading = true, error = null) }
            runCatching { musicApp.jamendo.search(query) }
                .onSuccess { songs -> _online.update { it.copy(loading = false, results = songs) } }
                .onFailure { e -> _online.update { it.copy(loading = false, error = e.message ?: str(R.string.online_search_failed)) } }
        }
    }

    fun download(song: Song) {
        runCatching { musicApp.downloader.download(song) }
            .onSuccess { _messages.tryEmit(str(R.string.msg_downloading, song.title)) }
            .onFailure { _messages.tryEmit(it.message ?: str(R.string.msg_download_failed)) }
    }

    fun exportBackup(target: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                Backup.export(
                    getApplication(), target, _library.value.songs, stats.value, prefs.favorites.value,
                    musicApp.playlists.playlists.value,
                )
            }
                .onSuccess { _messages.tryEmit(str(R.string.msg_backup_saved)) }
                .onFailure { _messages.tryEmit(it.message ?: str(R.string.msg_backup_failed)) }
        }
    }

    fun restoreBackup(source: android.net.Uri) {
        viewModelScope.launch {
            runCatching { Backup.restore(getApplication(), source, _library.value.songs) }
                .onSuccess { restored ->
                    musicApp.playStats.replaceAll(restored.stats)
                    prefs.reloadAfterRestore(restored.favorites)
                    // Keep playlists made on this phone; add the ones from the backup that aren't here yet.
                    val here = musicApp.playlists.playlists.value
                    val names = here.map { it.name.lowercase() }.toSet()
                    musicApp.playlists.replaceAll(here + restored.playlists.filterNot { it.name.lowercase() in names })
                    EqualizerController.reload()
                    com.vikash.mp3player.playback.FadeSetting.reload(getApplication())
                    com.vikash.mp3player.playback.EvenVolume.reload(getApplication())
                    _messages.tryEmit(
                        str(R.string.msg_restored, restored.favorites.size, restored.playlists.size)
                    )
                }
                .onFailure { _messages.tryEmit(it.message ?: str(R.string.msg_restore_failed)) }
        }
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    override fun onCleared() {
        player.release()
    }
}
