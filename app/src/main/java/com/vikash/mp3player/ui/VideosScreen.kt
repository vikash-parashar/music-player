package com.vikash.mp3player.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import com.vikash.mp3player.ui.skin.SkinButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vikash.mp3player.data.Video
import com.vikash.mp3player.ui.theme.Ampli
import com.vikash.mp3player.video.VideoPlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoState(val loading: Boolean = false, val videos: List<Video> = emptyList())

private val videoPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun Context.hasVideoPermission() =
    ContextCompat.checkSelfPermission(this, videoPermission) == PackageManager.PERMISSION_GRANTED

@Composable
fun VideosScreen(state: VideoState, onLoad: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasVideoPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        if (it) onLoad()
    }
    LifecycleResumeEffect(Unit) {
        hasPermission = context.hasVideoPermission()
        if (hasPermission) onLoad()
        onPauseOrDispose { }
    }

    if (!hasPermission) {
        EmptyMessage(stringResource(R.string.videos_permission_needed)) {
            Button(onClick = { launcher.launch(videoPermission) }) { Text(stringResource(R.string.library_allow_access)) }
        }
        return
    }
    if (state.loading && state.videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator(Modifier.padding(32.dp)) }
        return
    }
    if (state.videos.isEmpty()) {
        EmptyMessage(stringResource(R.string.videos_none))
        return
    }

    // Folder chips: biggest folders first, so Camera / WhatsApp / Snaptube are easy to reach.
    val folders = remember(state.videos) {
        state.videos.groupingBy { it.folder }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(state.videos, folder) { if (folder == null) state.videos else state.videos.filter { it.folder == folder } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text(stringResource(R.string.videos_all_count, state.videos.size)) })
            folders.forEach { f ->
                FilterChip(selected = folder == f, onClick = { folder = f }, label = { Text(groupLabel(f).substringAfterLast('/')) })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.id }) { video ->
                Column(Modifier.clickable {
                    VideoPlayerActivity.start(context, shown.map { it.uri }, shown.indexOf(video))
                }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp))) {
                        VideoThumbnail(video)
                        Box(
                            Modifier.align(Alignment.Center).size(36.dp).clip(RoundedCornerShape(18.dp))
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White) }
                        Text(
                            formatDuration(video.durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    Text(
                        video.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "${groupLabel(video.folder).substringAfterLast('/')} · ${formatSize(video.sizeBytes)}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Shown on the Videos tab until the user has Ampli Pro. */
@Composable
fun LockedVideos(price: String?, onGetPro: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.VideoLibrary, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.videos_locked_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        Text(
            stringResource(R.string.videos_locked_text),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        ProCard(isPro = false, price = price, onGetPro = onGetPro)
    }
}

@Composable
private fun VideoThumbnail(video: Video) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, video.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(video.uri, Size(480, 300), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver, video.id, MediaStore.Video.Thumbnails.MINI_KIND, null,
                    )
                }
            }.getOrNull()
        }
    }
    val b = bitmap
    if (b != null) {
        Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxSize().background(Ampli.Gradient))
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f KB".format(bytes / 1024.0)
}
