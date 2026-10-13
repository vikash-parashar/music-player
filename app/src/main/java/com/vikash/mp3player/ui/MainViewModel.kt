package com.vikash.mp3player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vikash.mp3player.MusicApp
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.playback.PlayerConnection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryState(
    val loading: Boolean = false,
    val songs: List<Song> = emptyList(),
)

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

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _online = MutableStateFlow(OnlineState(configured = musicApp.jamendo.isConfigured))
    val online: StateFlow<OnlineState> = _online.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var searchJob: Job? = null

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
                .onFailure { e -> _online.update { it.copy(loading = false, error = e.message ?: "Search failed") } }
        }
    }

    fun download(song: Song) {
        runCatching { musicApp.downloader.download(song) }
            .onSuccess { _messages.tryEmit("Downloading \"${song.title}\" to Music/MP3 Player") }
            .onFailure { _messages.tryEmit(it.message ?: "Download failed") }
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    override fun onCleared() {
        player.release()
    }
}
