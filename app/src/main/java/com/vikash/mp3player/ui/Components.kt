package com.vikash.mp3player.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.ui.theme.Ampli
import com.vikash.mp3player.playback.PlayerUiState

/** Album art with a music-note placeholder for files that have none. */
@Composable
fun Artwork(uri: Uri?, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(if (size > 100.dp) 16.dp else 6.dp)
    // Songs without album art get the Ampli mark on the brand gradient.
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize().background(Ampli.Gradient), contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    Box(modifier.size(size).clip(shape)) {
        if (uri == null) placeholder() else SubcomposeAsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Heart toggle: filled rose when the song is in My Favorites. */
@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (isFavorite) "Remove from My Favorites" else "Add to My Favorites",
            tint = if (isFavorite) Ampli.Rose else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

data class SongAction(val label: String, val onClick: () -> Unit)

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    actions: List<SongAction>,
    isFavorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.artworkUri, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOf(song.artist, formatDuration(song.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isFavorite != null) FavoriteButton(isFavorite, onToggleFavorite)
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More options") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = { menuOpen = false; action.onClick() },
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(state: PlayerUiState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val current = state.current ?: return
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(current.artworkUri, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    current.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GradientPlayButton(state.isPlaying, 40.dp, onToggle)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** App bar: logo + wordmark, with a "Get Pro" pill until the user has Pro. */
@Composable
fun BrandHeader(showAdFreePill: Boolean, onAdFreeClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text("Ampli", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (showAdFreePill) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Ampli.Gradient)
                    .clickable(onClick = onAdFreeClick).padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("Get Pro", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Big round play/pause button filled with the brand gradient. */
@Composable
fun GradientPlayButton(isPlaying: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Ampli.Gradient).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
