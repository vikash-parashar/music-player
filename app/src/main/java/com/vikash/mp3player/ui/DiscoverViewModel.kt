package com.vikash.mp3player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vikash.mp3player.MusicApp
import com.vikash.mp3player.data.Audiobook
import com.vikash.mp3player.data.PodcastEpisode
import com.vikash.mp3player.data.PodcastShow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BooksState(
    val query: String = "",
    val loading: Boolean = false,
    val books: List<Audiobook> = emptyList(),
    val open: Audiobook? = null,
    val failed: Boolean = false,
)

data class PodcastsState(
    val query: String = "",
    val loading: Boolean = false,
    /** Search results, or today's top shows when the query is blank. */
    val shows: List<PodcastShow> = emptyList(),
    val open: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val episodesLoading: Boolean = false,
    val failed: Boolean = false,
)

/** Free audiobooks (LibriVox) and podcasts for the Online tab. */
class DiscoverViewModel(app: Application) : AndroidViewModel(app) {

    private val musicApp = app as MusicApp
    private val repo = musicApp.spoken
    val follows = musicApp.podcastFollows.shows

    private val _books = MutableStateFlow(BooksState())
    val books: StateFlow<BooksState> = _books.asStateFlow()
    private val _podcasts = MutableStateFlow(PodcastsState())
    val podcasts: StateFlow<PodcastsState> = _podcasts.asStateFlow()

    private var bookJob: Job? = null
    private var podcastJob: Job? = null
    private var episodeJob: Job? = null

    fun searchBooks(query: String, debounce: Boolean = true) {
        _books.update { it.copy(query = query) }
        bookJob?.cancel()
        if (query.isBlank()) { _books.update { it.copy(books = emptyList(), loading = false, failed = false) }; return }
        bookJob = viewModelScope.launch {
            if (debounce) delay(500)
            _books.update { it.copy(loading = true, failed = false) }
            runCatching { repo.searchAudiobooks(query) }
                .onSuccess { list -> _books.update { it.copy(loading = false, books = list) } }
                .onFailure { _books.update { it.copy(loading = false, failed = true) } }
        }
    }

    fun openBook(book: Audiobook?) = _books.update { it.copy(open = book) }

    fun searchPodcasts(query: String, debounce: Boolean = true) {
        _podcasts.update { it.copy(query = query) }
        podcastJob?.cancel()
        podcastJob = viewModelScope.launch {
            if (debounce && query.isNotBlank()) delay(500)
            _podcasts.update { it.copy(loading = true, failed = false) }
            runCatching { if (query.isBlank()) repo.topPodcasts() else repo.searchPodcasts(query) }
                .onSuccess { list -> _podcasts.update { it.copy(loading = false, shows = list) } }
                .onFailure { _podcasts.update { it.copy(loading = false, failed = true) } }
        }
    }

    fun openShow(show: PodcastShow?) {
        episodeJob?.cancel()
        _podcasts.update { it.copy(open = show, episodes = emptyList(), episodesLoading = show != null, failed = false) }
        if (show == null) return
        episodeJob = viewModelScope.launch {
            runCatching { repo.episodes(show) }
                .onSuccess { list -> _podcasts.update { it.copy(episodes = list, episodesLoading = false) } }
                .onFailure { _podcasts.update { it.copy(episodesLoading = false, failed = true) } }
        }
    }

    fun toggleFollow(show: PodcastShow): Boolean = musicApp.podcastFollows.toggle(show)
}
