package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import com.vikash.mp3player.ui.skin.LocalSkin
import com.vikash.mp3player.ui.skin.TactileIconButton
import com.vikash.mp3player.ui.skin.GlowToggle
import com.vikash.mp3player.ui.skin.skinBackdrop
import com.vikash.mp3player.ui.skin.skinScreenColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
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
    onSetRingtone: () -> Unit,
    abLoop: AbLoop,
    onCycleAb: () -> Unit,
    onCarMode: () -> Unit,
    isPro: Boolean,
    onGetPro: () -> Unit,
    onClose: () -> Unit,
    onVisualizer: () -> Unit = {},
    onMakeStatus: () -> Unit = {},
    introToken: Int = 0,
    introShown: Int = 0,
    onIntroShown: (Int) -> Unit = {},
    playerStyle: com.vikash.mp3player.data.PlayerStyle = com.vikash.mp3player.data.PlayerStyle.Record,
    stylesUnlocked: Boolean = false,
    onPickStyle: (com.vikash.mp3player.data.PlayerStyle) -> Unit = {},
    /** Free users: a native ad card under the controls. */
    showAd: Boolean = false,
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { com.vikash.mp3player.data.OwnerHooks.event("feature_record_player") }
    var showCast by remember { mutableStateOf(false) }
    val casting by com.vikash.mp3player.cast.CastState.casting.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSound by remember { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showStyles by remember { mutableStateOf(false) }
    // Free users: a locked style plays for a few seconds ("Pro preview"), then asks to unlock.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var previewStyle by remember { mutableStateOf<com.vikash.mp3player.data.PlayerStyle?>(null) }
    var lockedStyle by remember { mutableStateOf<com.vikash.mp3player.data.PlayerStyle?>(null) }
    var previewLeft by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(previewStyle) {
        val s = previewStyle ?: return@LaunchedEffect
        for (left in StylePreview.SECONDS downTo 1) { previewLeft = left; delay(1000) }
        lockedStyle = s
        previewStyle = null
    }
    LaunchedEffect(stylesUnlocked) { if (stylesUnlocked) { previewStyle = null; lockedStyle = null } }
    val bookmarkStore = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.vikash.mp3player.MusicApp).bookmarks
    val allBookmarks by bookmarkStore.all.collectAsState()
    BackHandler { if (showQueue) showQueue = false else onClose() }
    // A song tapped in a list: the record drops onto the deck and the arm swings in, once per tap.
    // Keyed on the tap only, so marking it shown doesn't cut the animation short.
    val intro = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(introToken) {
        if (introToken != 0 && introToken != introShown) {
            onIntroShown(introToken)
            intro.snapTo(0f)
            intro.animateTo(1f, androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
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

    Surface(
        Modifier.fillMaxSize().skinBackdrop(),
        color = skinScreenColor(),
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.player_close)) }
                Text(if (showQueue) stringResource(R.string.player_queue) else stringResource(R.string.player_now_playing), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showCast = true }) {
                    Icon(if (casting) Icons.Default.CastConnected else Icons.Default.Cast, stringResource(R.string.cast_play_on_tv),
                        tint = if (casting) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current)
                }
                IconButton(onClick = onCarMode) { Icon(Icons.Default.DirectionsCar, stringResource(R.string.car_mode)) }
                IconButton(onClick = onSetRingtone) { Icon(Icons.Default.RingVolume, stringResource(R.string.song_set_ringtone)) }
                IconButton(onClick = { showQueue = !showQueue }) { Icon(Icons.Default.QueueMusic, stringResource(R.string.player_queue)) }
            }

            if (showQueue) {
                // Saved queues: tap to switch (resumes where it left off), x to remove.
                if (state.queues.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        items(state.queues.size, key = { i -> "$i:${state.queues[i].name}" }) { i ->
                            val q = state.queues[i]
                            val selected = i == state.activeQueue
                            val queueName = if (q.name == ALL_SONGS_QUEUE) stringResource(R.string.library_all_songs) else q.name
                            FilterChip(
                                selected = selected,
                                onClick = { player.switchQueue(i) },
                                label = { Text("$queueName · ${q.size}", maxLines = 1) },
                                modifier = Modifier.animateItem(),
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close, stringResource(R.string.player_remove_queue),
                                        Modifier.size(16.dp).clickable { player.removeQueue(i) },
                                    )
                                },
                            )
                        }
                    }
                }
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex)
                // The same song can be in the queue twice, so each key is the song id plus which
                // copy of it this is; removing another song keeps the rest of the keys.
                val queueKeys = remember(state.queue) {
                    val seen = HashMap<String, Int>()
                    state.queue.map { item ->
                        val n = seen.getOrElse(item.mediaId) { 0 }
                        seen[item.mediaId] = n + 1
                        "${item.mediaId}#$n"
                    }
                }
                LazyColumn(Modifier.weight(1f), state = listState) {
                    items(state.queue.size, key = { i -> queueKeys[i] }) { i ->
                        val item = state.queue[i]
                        val isCurrent = i == state.currentIndex
                        Row(
                            Modifier.animateItem().fillMaxWidth().clickable { player.skipToQueueItem(i) }.padding(vertical = 6.dp),
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
                                Icon(Icons.Default.Close, stringResource(R.string.player_remove_from_queue))
                            }
                        }
                    }
                }
            } else {
                if (showLyrics) {
                    LyricsView(
                        songId = current.mediaId,
                        positionMs = position,
                        onSeek = { player.seekTo(it); position = it },
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                        pro = isPro,
                        title = current.title,
                        artist = current.artist,
                    )
                } else {
                    // The chosen player style; Pro styles show the record when Pro isn't active,
                    // except during a free user's short Pro preview (or its unlock card that follows).
                    val chosen = if (!playerStyle.pro || stylesUnlocked) playerStyle else com.vikash.mp3player.data.PlayerStyle.Record
                    val style = previewStyle ?: lockedStyle ?: chosen
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)) {
                    PlayerPicture(
                        style = style,
                        artworkUri = current.artworkUri,
                        songId = current.mediaId,
                        title = current.title,
                        artist = current.artist,
                        isPlaying = state.isPlaying,
                        positionMs = position,
                        durationMs = state.durationMs,
                        onSeek = { player.seekTo(it); position = it },
                        onTogglePlay = player::togglePlay,
                        onNext = { player.next() },
                        onPrevious = { player.previous() },
                        modifier = Modifier.fillMaxSize(),
                        intro = { intro.value },
                    )
                    if (previewStyle != null) StylePreviewBadge(previewLeft, Modifier.align(Alignment.TopStart))
                    lockedStyle?.let { locked ->
                        StyleUnlockCard(
                            styleName = stringResource(playerStyleLabel(locked)),
                            onUnlock = { lockedStyle = null; onGetPro() },
                            onBack = { lockedStyle = null },
                        )
                    }
                    }
                }
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
                if (!LocalSkin.current.isClassic) {
                    // Pro app styles: square push keys with a dome that sinks in.
                    GlowToggle(state.shuffle, player::toggleShuffle, Icons.Default.Shuffle, stringResource(R.string.player_shuffle), size = 44.dp)
                    TactileIconButton(Icons.Default.SkipPrevious, stringResource(R.string.common_previous), { player.previous() }, size = 54.dp)
                    GradientPlayButton(state.isPlaying, 72.dp, player::togglePlay)
                    TactileIconButton(Icons.Default.SkipNext, stringResource(R.string.common_next), { player.next() }, size = 54.dp)
                    GlowToggle(
                        state.repeatMode != Player.REPEAT_MODE_OFF, player::cycleRepeat,
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        stringResource(R.string.player_repeat), size = 44.dp,
                    )
                } else {
                IconButton(onClick = player::toggleShuffle) {
                    Icon(Icons.Default.Shuffle, stringResource(R.string.player_shuffle), tint = if (state.shuffle) active else inactive)
                }
                IconButton(onClick = { player.previous() }) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.common_previous), Modifier.size(36.dp)) }
                GradientPlayButton(state.isPlaying, 76.dp, player::togglePlay)
                IconButton(onClick = { player.next() }) { Icon(Icons.Default.SkipNext, stringResource(R.string.common_next), Modifier.size(36.dp)) }
                IconButton(onClick = player::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        stringResource(R.string.player_repeat),
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) inactive else active,
                    )
                }
                }
            }

            // Extra tools: speed/pitch, lyrics and A-B repeat.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
                val normal = state.speed == 1f && state.pitch == 1f
                val eqOn by com.vikash.mp3player.playback.EqualizerController.state.collectAsState()
                val proEqOn by com.vikash.mp3player.playback.ProSound.state.collectAsState()
                FilterChip(
                    selected = eqOn.enabled || proEqOn.eqEnabled,
                    onClick = { showSound = true },
                    leadingIcon = { Icon(Icons.Default.Equalizer, null, Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.settings_equalizer)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !normal,
                    onClick = { showSpeed = true },
                    leadingIcon = { Icon(Icons.Default.Speed, null, Modifier.size(18.dp)) },
                    label = { Text("%.2f".format(java.util.Locale.US, state.speed).trimEnd('0').trimEnd('.') + "x") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = playerStyle != com.vikash.mp3player.data.PlayerStyle.Record,
                    onClick = { showLyrics = false; showStyles = true },
                    leadingIcon = { Icon(Icons.Default.Palette, null, Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.pstyle_chip)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = showLyrics,
                    onClick = { showLyrics = !showLyrics },
                    leadingIcon = { Icon(Icons.Default.Lyrics, null, Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.lyrics_title)) },
                )
                Spacer(Modifier.width(8.dp))
                val marks = allBookmarks[current.mediaId].orEmpty()
                FilterChip(
                    selected = marks.isNotEmpty(),
                    onClick = { showBookmarks = true },
                    leadingIcon = { Icon(Icons.Default.Bookmark, null, Modifier.size(18.dp)) },
                    label = { Text(if (marks.isEmpty()) stringResource(R.string.bookmarks_title) else "${stringResource(R.string.bookmarks_title)} · ${marks.size}") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = false,
                    onClick = onVisualizer,
                    leadingIcon = { Icon(Icons.Default.GraphicEq, null, Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.visualizer_title)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = false,
                    onClick = onMakeStatus,
                    leadingIcon = { Icon(Icons.Default.Movie, null, Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.status_chip)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = abLoop.aMs != null,
                    onClick = onCycleAb,
                    leadingIcon = { Icon(Icons.Default.Loop, null, Modifier.size(18.dp)) },
                    label = {
                        Text(
                            when {
                                abLoop.active -> stringResource(R.string.player_ab_on)
                                abLoop.aMs != null -> stringResource(R.string.player_ab_set_b)
                                else -> stringResource(R.string.player_ab_repeat)
                            }
                        )
                    },
                )
            }

            // Free users: one native ad, kept well clear of the playback buttons (AdMob placement rules).
            if (showAd && !showQueue) {
                Spacer(Modifier.height(20.dp))
                com.vikash.mp3player.monetization.NativeAdCard()
            }
        }
    }

    if (showStyles) PlayerStyleSheet(
        current = playerStyle,
        unlocked = stylesUnlocked,
        onPick = onPickStyle,
        onGetPro = { showStyles = false; onGetPro() },
        onDismiss = { showStyles = false },
        onPreview = { style ->
            if (StylePreview.tryUse(ctx, style)) { lockedStyle = null; previewStyle = style } else onGetPro()
        },
    )
    if (showSound) SoundSheet(isPro, onGetPro) { showSound = false }
    if (showSpeed) SpeedPitchDialog(state.speed, state.pitch, player::setSpeedAndPitch) { showSpeed = false }
    if (showCast) CastDialog(isPro, onGetPro) { showCast = false }
    if (showBookmarks) BookmarksDialog(
        positionMs = position,
        bookmarks = allBookmarks[current.mediaId].orEmpty(),
        onAdd = { at, note -> bookmarkStore.add(current.mediaId, at, note) },
        onJump = { player.seekTo(it); position = it },
        onRemove = { bookmarkStore.remove(current.mediaId, it) },
        onDismiss = { showBookmarks = false },
    )
}

@Composable
private fun SpeedPitchDialog(speed: Float, pitch: Float, onChange: (Float, Float) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_speed_pitch)) },
        text = {
            Column {
                Text(stringResource(R.string.player_speed_value, "%.2f".format(speed)))
                Slider(value = speed, onValueChange = { onChange(snap(it), pitch) }, valueRange = 0.5f..2f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f).forEach { v ->
                        FilterChip(selected = speed == v, onClick = { onChange(v, pitch) }, label = { Text("${v}x") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.player_pitch_value, "%.2f".format(pitch)))
                Slider(value = pitch, onValueChange = { onChange(speed, snap(it)) }, valueRange = 0.5f..2f)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
        dismissButton = { TextButton(onClick = { onChange(1f, 1f) }) { Text(stringResource(R.string.player_reset)) } },
    )
}

/** Round to 0.05 steps so values read cleanly (1.25x, not 1.2473x). */
private fun snap(v: Float) = (Math.round(v * 20f) / 20f)
