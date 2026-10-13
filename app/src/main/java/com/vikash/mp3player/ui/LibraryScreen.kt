package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    hasPermission: Boolean,
    currentId: String?,
    onRequestPermission: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
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
private fun SongList(
    title: String?,
    songs: List<Song>,
    currentId: String?,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
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
                IconButton(onClick = { if (songs.isNotEmpty()) onPlay(songs.shuffled(), 0) }) {
                    Icon(Icons.Default.Shuffle, "Shuffle all")
                }
            }
        }
        items(songs.size, key = { songs[it].id }) { i ->
            val song = songs[i]
            SongRow(
                song = song,
                isCurrent = song.id == currentId,
                onClick = { onPlay(songs, i) },
                actions = listOf(
                    SongAction("Play next") { onPlayNext(song) },
                    SongAction("Add to queue") { onAddToQueue(song) },
                ),
            )
        }
    }
}
