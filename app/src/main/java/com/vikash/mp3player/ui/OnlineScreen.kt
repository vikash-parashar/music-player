package com.vikash.mp3player.ui

import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
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
        EmptyMessage(stringResource(R.string.online_not_configured))
        return
    }
    LaunchedEffect(Unit) { if (state.results.isEmpty() && state.query.isBlank()) onLoadPopular() }
    val onlineQueue = stringResource(R.string.nav_online)
    val playNextLabel = stringResource(R.string.song_play_next)
    val addToQueueLabel = stringResource(R.string.song_add_to_queue)
    val downloadLabel = stringResource(R.string.online_download)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.online_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, stringResource(R.string.common_clear)) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            if (state.query.isBlank()) stringResource(R.string.online_popular) else stringResource(R.string.online_results),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        when {
            state.error != null -> EmptyMessage(stringResource(R.string.online_error, state.error.orEmpty()))
            !state.loading && state.results.isEmpty() -> EmptyMessage(stringResource(R.string.online_no_results))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.results.size, key = { state.results[it].id }) { i ->
                    val song = state.results[i]
                    SongRow(
                        song = song,
                        isCurrent = song.id == currentId,
                        onClick = { onPlay(state.results, i, onlineQueue) },
                        actions = buildList {
                            add(SongAction(playNextLabel, Icons.AutoMirrored.Filled.PlaylistPlay) { onPlayNext(song) })
                            add(SongAction(addToQueueLabel, Icons.AutoMirrored.Filled.QueueMusic) { onAddToQueue(song) })
                            if (song.downloadUrl != null) add(SongAction(downloadLabel, Icons.Default.Download) { onDownload(song) })
                        },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.online_credit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
