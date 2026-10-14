package com.vikash.mp3player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.data.Song

/** Search, stream and download free, legally licensed music from Jamendo. */
@Composable
fun OnlineScreen(
    state: OnlineState,
    currentId: String?,
    onQueryChange: (String) -> Unit,
    onLoadPopular: () -> Unit,
    onPlay: (songs: List<Song>, startIndex: Int, source: String) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDownload: (Song) -> Unit,
) {
    if (!state.configured) {
        EmptyMessage("Online music is off in this build. Add JAMENDO_CLIENT_ID to local.properties (see README).")
        return
    }
    LaunchedEffect(Unit) { if (state.results.isEmpty() && state.query.isBlank()) onLoadPopular() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search songs, artists, genres") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, "Clear") }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            if (state.query.isBlank()) "Popular this week" else "Results",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        when {
            state.error != null -> EmptyMessage("Couldn't reach the music catalog. Check your connection.\n(${state.error})")
            !state.loading && state.results.isEmpty() -> EmptyMessage("No songs found.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.results.size, key = { state.results[it].id }) { i ->
                    val song = state.results[i]
                    SongRow(
                        song = song,
                        isCurrent = song.id == currentId,
                        onClick = { onPlay(state.results, i, "Online") },
                        actions = buildList {
                            add(SongAction("Play next") { onPlayNext(song) })
                            add(SongAction("Add to queue") { onAddToQueue(song) })
                            if (song.downloadUrl != null) add(SongAction("Download") { onDownload(song) })
                        },
                    )
                }
                item {
                    Text(
                        "Music provided by Jamendo under Creative Commons licenses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
