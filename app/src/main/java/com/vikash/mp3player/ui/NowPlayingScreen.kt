package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.vikash.mp3player.playback.PlayerConnection
import com.vikash.mp3player.playback.PlayerUiState
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    player: PlayerConnection,
    favorites: List<String>,
    onToggleFavorite: (id: String, title: String) -> Unit,
    seekStyle: SeekStyle,
    onClose: () -> Unit,
) {
    var showQueue by remember { mutableStateOf(false) }
    BackHandler { if (showQueue) showQueue = false else onClose() }
    val current = state.current
    if (current == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    // Poll the position while visible; the controller doesn't push position updates.
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.current, state.isPlaying) {
        while (true) {
            if (!dragging) position = player.positionMs()
            delay(if (state.isPlaying) 100 else 500) // frequent updates keep the animated bar smooth
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close player") }
                Text(if (showQueue) "Queue" else "Now playing", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showQueue = !showQueue }) { Icon(Icons.Default.QueueMusic, "Queue") }
            }

            if (showQueue) {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex)
                LazyColumn(Modifier.weight(1f), state = listState) {
                    items(state.queue.size) { i ->
                        val item = state.queue[i]
                        val isCurrent = i == state.currentIndex
                        Row(
                            Modifier.fillMaxWidth().clickable { player.skipToQueueItem(i) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(item.artworkUri, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(item.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!isCurrent) IconButton(onClick = { player.removeFromQueue(i) }) {
                                Icon(Icons.Default.Close, "Remove from queue")
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(0.2f))
                Artwork(current.artworkUri, 300.dp, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.weight(0.2f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(current.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(current.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FavoriteButton(
                        isFavorite = current.mediaId in favorites,
                        onToggle = { onToggleFavorite(current.mediaId, current.title) },
                        modifier = Modifier.size(56.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            val duration = state.durationMs.coerceAtLeast(1)
            AnimatedSeekBar(
                progress = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
                playing = state.isPlaying && !dragging,
                style = seekStyle,
                onSeek = { dragging = true; dragValue = it },
                onSeekFinished = {
                    if (dragging) {
                        val target = (dragValue * duration).toLong()
                        player.seekTo(target)
                        position = target
                        dragging = false
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position).ifEmpty { "0:00" }, style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(state.durationMs), style = MaterialTheme.typography.bodySmall)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val active = MaterialTheme.colorScheme.primary
                val inactive = MaterialTheme.colorScheme.onSurfaceVariant
                IconButton(onClick = player::toggleShuffle) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffle) active else inactive)
                }
                IconButton(onClick = { player.previous() }) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                GradientPlayButton(state.isPlaying, 76.dp, player::togglePlay)
                IconButton(onClick = { player.next() }) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp)) }
                IconButton(onClick = player::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) inactive else active,
                    )
                }
            }
        }
    }
}
