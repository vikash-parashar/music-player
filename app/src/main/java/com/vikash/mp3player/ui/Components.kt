package com.vikash.mp3player.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
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
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.vikash.mp3player.R
import com.vikash.mp3player.data.INTERNAL_STORAGE
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.UNKNOWN_ALBUM
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
    // Load again when "Find album art" saves a cover, so albums with no art pick it up.
    val artVersion by com.vikash.mp3player.data.AlbumArtFinder.version.collectAsState()
    Box(modifier.size(size).clip(shape)) {
        if (uri == null) placeholder() else key(artVersion) {
            SubcomposeAsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Heart toggle: filled rose when the song is in My Favorites. */
@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            stringResource(if (isFavorite) R.string.favorites_remove else R.string.favorites_add),
            tint = if (isFavorite) Ampli.Rose else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One entry in a song's menu. [icon] shows in the round button; [danger] tints it red (Delete). */
data class SongAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    actions: List<SongAction>,
    isFavorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
    /** Swipe the row to the right to play the song next (a common Musicolet request). */
    onSwipePlayNext: (() -> Unit)? = null,
    /** Long-press starts multi-select. */
    onLongClick: (() -> Unit)? = null,
    /** Non-null while multi-select is on: shows a checkbox instead of the heart and menu. */
    selected: Boolean? = null,
) {
    if (onSwipePlayNext == null || selected != null) {
        SongRowContent(song, isCurrent, onClick, actions, isFavorite, onToggleFavorite, onLongClick, selected)
        return
    }
    val onSwipe by rememberUpdatedState(onSwipePlayNext)
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) onSwipe()
            false // always spring back; the song stays in the list
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.song_play_next), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
    ) {
        Surface { SongRowContent(song, isCurrent, onClick, actions, isFavorite, onToggleFavorite, onLongClick, null) }
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
fun BrandHeader(showAdFreePill: Boolean, onAdFreeClick: () -> Unit, onRecognize: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text("Ampli", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onRecognize) { Icon(Icons.Default.Hearing, stringResource(R.string.recognize_title)) }
        if (showAdFreePill) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Ampli.Gradient)
                    .clickable(onClick = onAdFreeClick).padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.common_get_pro), color = Color.White, style = MaterialTheme.typography.labelLarge)
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
            stringResource(if (isPlaying) R.string.common_pause else R.string.common_play),
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * Display name for a library group (folder, album...). The built-in group names are kept in
 * English as keys, so they are translated here for display.
 */
@Composable
fun groupLabel(name: String): String = when (name) {
    INTERNAL_STORAGE -> stringResource(R.string.library_internal_storage)
    UNKNOWN_ALBUM -> stringResource(R.string.library_unknown_album)
    else -> name
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
