package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.vikash.mp3player.data.StatsData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.vikash.mp3player.ui.theme.Ampli
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.data.Song

private enum class LibraryTab(val label: String, val key: (Song) -> String) {
    Songs("Songs", { "" }),
    Albums("Albums", { it.album.ifBlank { "Unknown album" } }),
    Artists("Artists", { it.artist }),
    Folders("Folders", { it.folder.ifBlank { "Internal storage" } }),
    Playlists("Playlists", { "" }),
}

const val MY_FAVORITES = "My Favorites"

/** Built-in lists that fill themselves from favorites, play history and date added. */
private enum class SmartList(val title: String, val icon: ImageVector, val emptyText: String) {
    Favorites(MY_FAVORITES, Icons.Default.Favorite, "No favorites yet. Tap the heart next to a song, or on the player screen, to add it here."),
    MostPlayed("Most played", Icons.Default.Whatshot, "Play some music and your most played songs show up here."),
    RecentlyPlayed("Recently played", Icons.Default.History, "Songs you play show up here."),
    RecentlyAdded("Recently added", Icons.Default.NewReleases, "No songs yet."),
    NeverPlayed("Never played", Icons.Default.Explore, "You've played every song. Nice!"),
}

private const val SMART_LIMIT = 100

private fun SmartList.songs(all: List<Song>, favorites: List<String>, stats: StatsData): List<Song> {
    fun plays(s: Song) = stats.songs[s.id]?.plays ?: 0
    fun last(s: Song) = stats.songs[s.id]?.lastPlayedMs ?: 0L
    return when (this) {
        SmartList.Favorites -> {
            val byId = all.associateBy { it.id }
            favorites.mapNotNull { byId[it] }
        }
        SmartList.MostPlayed -> all.filter { plays(it) > 0 }
            .sortedWith(compareByDescending<Song> { plays(it) }.thenByDescending { last(it) }).take(SMART_LIMIT)
        SmartList.RecentlyPlayed -> all.filter { last(it) > 0 }.sortedByDescending { last(it) }.take(SMART_LIMIT)
        SmartList.RecentlyAdded -> all.sortedByDescending { it.dateAddedSec }.take(SMART_LIMIT)
        SmartList.NeverPlayed -> all.filter { plays(it) == 0 }
    }
}


@Composable
fun LibraryScreen(
    state: LibraryState,
    hasPermission: Boolean,
    currentId: String?,
    onRequestPermission: () -> Unit,
    onPlay: (songs: List<Song>, startIndex: Int, source: String) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    favorites: List<String>,
    onToggleFavorite: (Song) -> Unit,
    onSetRingtone: (Song) -> Unit,
    stats: StatsData,
    onOpenStats: () -> Unit,
) {
    if (!hasPermission) {
        EmptyMessage("Allow Ampli to see the music on this phone.") {
            Button(onClick = onRequestPermission) { Text("Allow access") }
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val current = LibraryTab.entries[tab]

    BackHandler(enabled = openGroup != null) { openGroup = null }

    val filtered = remember(state.songs, filter) {
        if (filter.isBlank()) state.songs else state.songs.filter {
            it.title.contains(filter, true) || it.artist.contains(filter, true) ||
                it.album.contains(filter, true) || it.folder.contains(filter, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Search your music") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Default.Clear, "Clear") }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            LibraryTab.entries.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i; openGroup = null }, text = { Text(t.label) })
            }
        }

        when {
            state.loading && state.songs.isEmpty() ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.padding(32.dp))
                }
            state.songs.isEmpty() -> EmptyMessage("No music found on this phone yet. Downloaded songs appear here too.")
            current == LibraryTab.Playlists && openGroup == null -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        PlaylistRow(
                            "Your listening stats", "Hours played, top songs and artists. Share it!",
                            Icons.Default.Insights, onClick = onOpenStats,
                        )
                    }
                    items(SmartList.entries.toList(), key = { it.name }) { list ->
                        val count = remember(state.songs, favorites, stats, list) { list.songs(state.songs, favorites, stats).size }
                        PlaylistRow(
                            list.title,
                            when {
                                count > 0 -> "$count songs"
                                list == SmartList.Favorites -> "Tap the heart on any song to add it"
                                else -> "Empty for now"
                            },
                            list.icon,
                            onClick = { openGroup = list.title },
                        )
                    }
                }
            }
            current == LibraryTab.Playlists && openGroup != null -> {
                val list = SmartList.entries.firstOrNull { it.title == openGroup } ?: SmartList.Favorites
                val songs = remember(filtered, favorites, stats, list) { list.songs(filtered, favorites, stats) }
                if (songs.isEmpty()) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { openGroup = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                            Text(list.title, style = MaterialTheme.typography.titleMedium)
                        }
                        EmptyMessage(list.emptyText)
                    }
                } else {
                    SongList(
                        title = list.title,
                        songs = songs,
                        currentId = currentId,
                        onBack = { openGroup = null },
                        onPlay = onPlay,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        favorites = favorites,
                        onToggleFavorite = onToggleFavorite,
                        onSetRingtone = onSetRingtone,
                    )
                }
            }
            current == LibraryTab.Songs || openGroup != null -> {
                val songs = if (openGroup == null) filtered else filtered.filter { current.key(it) == openGroup }
                SongList(
                    title = openGroup,
                    songs = songs,
                    currentId = currentId,
                    onBack = { openGroup = null },
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    favorites = favorites,
                    onToggleFavorite = onToggleFavorite,
                    onSetRingtone = onSetRingtone,
                )
            }
            else -> {
                val groups = remember(filtered, current) {
                    filtered.groupBy(current.key).toSortedMap(String.CASE_INSENSITIVE_ORDER)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(groups.entries.toList(), key = { it.key }) { (name, songs) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openGroup = name }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(songs.first().artworkUri, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${songs.size} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Ampli.Gradient),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SongList(
    title: String?,
    songs: List<Song>,
    currentId: String?,
    onBack: () -> Unit,
    onPlay: (songs: List<Song>, startIndex: Int, source: String) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    favorites: List<String>,
    onToggleFavorite: (Song) -> Unit,
    onSetRingtone: (Song) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                } else {
                    Text("${songs.size} songs", modifier = Modifier.weight(1f).padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { if (songs.isNotEmpty()) onPlay(songs.shuffled(), 0, title ?: "All songs") }) {
                    Icon(Icons.Default.Shuffle, "Shuffle all")
                }
            }
        }
        items(songs.size, key = { songs[it].id }) { i ->
            val song = songs[i]
            val fav = song.id in favorites
            SongRow(
                song = song,
                isCurrent = song.id == currentId,
                onClick = { onPlay(songs, i, title ?: "All songs") },
                actions = listOf(
                    SongAction("Play next") { onPlayNext(song) },
                    SongAction("Add to queue") { onAddToQueue(song) },
                    SongAction(if (fav) "Remove from $MY_FAVORITES" else "Add to $MY_FAVORITES") { onToggleFavorite(song) },
                    SongAction("Set as ringtone") { onSetRingtone(song) },
                ),
                isFavorite = fav,
                onToggleFavorite = { onToggleFavorite(song) },
                onSwipePlayNext = { onPlayNext(song) },
            )
        }
    }
}
