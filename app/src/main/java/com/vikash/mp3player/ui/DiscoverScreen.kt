package com.vikash.mp3player.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Audiobook
import com.vikash.mp3player.data.PodcastShow
import com.vikash.mp3player.data.Song
import java.text.DateFormat
import java.util.Date

/** Callbacks shared by the music, audiobook and podcast lists. */
class DiscoverActions(
    val play: (songs: List<Song>, startIndex: Int, source: String) -> Unit,
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    /** Saves the file under Music/[folder]. */
    val download: (song: Song, folder: String) -> Unit,
)

/** The Online tab: licensed music (Jamendo), free audiobooks (LibriVox) and podcasts. */
@Composable
fun DiscoverScreen(
    currentId: String?,
    actions: DiscoverActions,
    music: @Composable () -> Unit,
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = section) {
            Tab(selected = section == 0, onClick = { section = 0 }, text = { Text(stringResource(R.string.discover_music)) })
            Tab(selected = section == 1, onClick = { section = 1 }, text = { Text(stringResource(R.string.discover_audiobooks)) })
            Tab(selected = section == 2, onClick = { section = 2 }, text = { Text(stringResource(R.string.discover_podcasts)) })
        }
        when (section) {
            0 -> music()
            1 -> AudiobooksSection(currentId, actions)
            else -> PodcastsSection(currentId, actions)
        }
    }
}

@Composable
private fun SearchField(query: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, stringResource(R.string.common_clear)) }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A row for a book or a show: cover, title and up to two lines under it. */
@Composable
private fun CoverRow(art: Uri?, title: String, line1: String, line2: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(art, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(line1, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!line2.isNullOrBlank()) Text(line2, maxLines = 1, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun spokenActions(song: Song, folder: String, actions: DiscoverActions): List<SongAction> = listOf(
    SongAction(stringResource(R.string.song_play_next)) { actions.playNext(song) },
    SongAction(stringResource(R.string.song_add_to_queue)) { actions.addToQueue(song) },
    SongAction(stringResource(R.string.online_download)) { actions.download(song, folder) },
)

// ---------------------------------------------------------------------------------- audiobooks

private val bookSuggestions = listOf("Tagore", "Sherlock Holmes", "Aesop", "Arabian Nights", "Jungle Book", "Gandhi", "Tolstoy", "Hindi")

@Composable
private fun AudiobooksSection(currentId: String?, actions: DiscoverActions) {
    val vm: DiscoverViewModel = viewModel()
    val state by vm.books.collectAsStateWithLifecycle()
    val open = state.open
    if (open != null) {
        BackHandler { vm.openBook(null) }
        BookDetail(open, currentId, actions, onBack = { vm.openBook(null) })
        return
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(state.query, stringResource(R.string.books_search_hint)) { vm.searchBooks(it) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            state.query.isBlank() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.books_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.books_try), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    bookSuggestions.forEach { s -> AssistChip(onClick = { vm.searchBooks(s, debounce = false) }, label = { Text(s) }) }
                }
            }
            state.failed -> EmptyMessage(stringResource(R.string.discover_offline))
            !state.loading && state.books.isEmpty() -> EmptyMessage(stringResource(R.string.online_no_results))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.books.size, key = { state.books[it].id }) { i ->
                    val b = state.books[i]
                    CoverRow(
                        art = b.chapters.firstOrNull()?.artworkUri,
                        title = b.title,
                        line1 = b.author,
                        line2 = listOf(b.language, b.totalTime, stringResource(R.string.books_chapters, b.chapters.size))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = { vm.openBook(b) },
                    )
                }
                item {
                    Text(stringResource(R.string.books_credit), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun BookDetail(book: Audiobook, currentId: String?, actions: DiscoverActions, onBack: () -> Unit) {
    val folder = "Audiobooks/${book.title}"
    LazyColumn(Modifier.fillMaxSize()) {
        item { DetailHeader(book.title, onBack) }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(book.chapters.firstOrNull()?.artworkUri, 96.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(listOf(book.language, book.totalTime).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (book.description.isNotBlank()) item {
            Text(book.description, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.play(book.chapters, 0, book.title) }) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(stringResource(R.string.books_play))
                }
                OutlinedButton(onClick = { book.chapters.forEach { actions.download(it, folder) } }) {
                    Text(stringResource(R.string.books_download_all))
                }
            }
            Text(stringResource(R.string.books_resume_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(book.chapters.size, key = { book.chapters[it].id }) { i ->
            val ch = book.chapters[i]
            SongRow(
                song = ch,
                isCurrent = ch.id == currentId,
                onClick = { actions.play(book.chapters, i, book.title) },
                actions = spokenActions(ch, folder, actions),
            )
        }
    }
}

// ---------------------------------------------------------------------------------- podcasts

@Composable
private fun PodcastsSection(currentId: String?, actions: DiscoverActions) {
    val vm: DiscoverViewModel = viewModel()
    val state by vm.podcasts.collectAsStateWithLifecycle()
    val follows by vm.follows.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.shows.isEmpty() && state.query.isBlank() && !state.loading) vm.searchPodcasts("", debounce = false) }
    val open = state.open
    if (open != null) {
        BackHandler { vm.openShow(null) }
        ShowDetail(open, state, following = follows.any { it.feedUrl == open.feedUrl }, currentId, actions,
            onFollow = { vm.toggleFollow(open) }, onBack = { vm.openShow(null) })
        return
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(state.query, stringResource(R.string.podcasts_search_hint)) { vm.searchPodcasts(it) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize()) {
            if (state.query.isBlank() && follows.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.podcasts_following)) }
                items(follows.size, key = { "f" + follows[it].feedUrl }) { i ->
                    val s = follows[i]
                    CoverRow(s.artworkUrl?.let(Uri::parse), s.title, s.author, null) { vm.openShow(s) }
                }
            }
            item { SectionTitle(if (state.query.isBlank()) stringResource(R.string.podcasts_top) else stringResource(R.string.online_results)) }
            when {
                state.failed -> item { EmptyMessage(stringResource(R.string.discover_offline)) }
                !state.loading && state.shows.isEmpty() -> item { EmptyMessage(stringResource(R.string.online_no_results)) }
                else -> items(state.shows.size, key = { state.shows[it].feedUrl }) { i ->
                    val s = state.shows[i]
                    CoverRow(s.artworkUrl?.let(Uri::parse), s.title, s.author, null) { vm.openShow(s) }
                }
            }
            item {
                Text(stringResource(R.string.podcasts_credit), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun ShowDetail(
    show: PodcastShow,
    state: PodcastsState,
    following: Boolean,
    currentId: String?,
    actions: DiscoverActions,
    onFollow: () -> Unit,
    onBack: () -> Unit,
) {
    val folder = "Podcasts/${show.title}"
    val songs = state.episodes.map { it.song }
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    LazyColumn(Modifier.fillMaxSize()) {
        item { DetailHeader(show.title, onBack) }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(show.artworkUrl?.let(Uri::parse), 96.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(show.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (show.author.isNotBlank()) Text(show.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    if (following) OutlinedButton(onClick = onFollow) { Text(stringResource(R.string.podcasts_unfollow)) }
                    else FilledTonalButton(onClick = onFollow) { Text(stringResource(R.string.podcasts_follow)) }
                }
            }
        }
        if (state.episodesLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.failed) item { EmptyMessage(stringResource(R.string.discover_offline)) }
        else if (!state.episodesLoading && songs.isEmpty()) item { EmptyMessage(stringResource(R.string.podcasts_no_episodes)) }
        items(state.episodes.size, key = { state.episodes[it].song.id }) { i ->
            val ep = state.episodes[i]
            Column {
                SongRow(
                    song = ep.song,
                    isCurrent = ep.song.id == currentId,
                    onClick = { actions.play(songs, i, show.title) },
                    actions = spokenActions(ep.song, folder, actions),
                )
                if (ep.publishedMs > 0) Text(
                    dateFormat.format(Date(ep.publishedMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 84.dp, bottom = 4.dp),
                )
            }
        }
    }
}
