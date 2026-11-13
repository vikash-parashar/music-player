package com.vikash.mp3player.ui

import com.vikash.mp3player.ui.skin.skinSurface
import com.vikash.mp3player.ui.skin.skinPressSurface
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import com.vikash.mp3player.ui.skin.SkinCheckbox as Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.playback.PlayerUiState
import com.vikash.mp3player.ui.theme.Lux
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Whether music is playing right now, so the current song's row can dance (or rest when paused). */
val LocalIsPlaying = compositionLocalOf { false }

/** Row height is kept to 52dp so about 10 songs fit on one screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongRowContent(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    actions: List<SongAction>,
    isFavorite: Boolean?,
    onToggleFavorite: () -> Unit,
    onLongClick: (() -> Unit)?,
    selected: Boolean?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val playing = isCurrent && LocalIsPlaying.current
    // The playing row glows warm coral, and its title is gold (luxury look).
    val primary = Lux.Glow

    // The current song "floats": a soft rose glow sweeps across it and it bobs gently.
    // Only the current row runs these loops, so the rest of the list stays cheap.
    // Lite mode: the row is highlighted but still.
    val fx = if (isCurrent && !LocalLiteMode.current) rememberNowPlayingFx() else null
    val sweep = fx?.sweep?.value ?: 0f
    val lift = remember { Animatable(0f) }
    LaunchedEffect(isCurrent) { lift.animateTo(if (isCurrent) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 300f)) }

    val skin = com.vikash.mp3player.ui.skin.LocalSkin.current
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                // Pro app styles: each row sits on the style's own card (raised, clay, bordered...).
                when (skin.style) {
                    com.vikash.mp3player.ui.skin.SurfaceStyle.Material -> Modifier
                    com.vikash.mp3player.ui.skin.SurfaceStyle.Hairline -> Modifier.drawBehind {
                        drawLine(skin.edge, androidx.compose.ui.geometry.Offset(16.dp.toPx(), size.height), androidx.compose.ui.geometry.Offset(size.width - 16.dp.toPx(), size.height), 1.dp.toPx())
                    }
                    else -> Modifier.padding(horizontal = 12.dp, vertical = 3.dp).skinPressSurface(RoundedCornerShape(minOf(skin.corner, 18.dp)))
                }
            )
            .graphicsLayer {
                if (isCurrent) {
                    translationY = if (playing) (fx?.bob?.value ?: 0f) * 1.5.dp.toPx() else 0f
                    val s = 1f + 0.015f * lift.value
                    scaleX = s; scaleY = s
                }
            }
            .then(
                if (isCurrent) Modifier
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            0f to primary.copy(alpha = 0.10f),
                            (sweep - 0.25f).coerceIn(0f, 1f) to primary.copy(alpha = 0.10f),
                            sweep.coerceIn(0f, 1f) to primary.copy(alpha = if (playing) 0.30f else 0.16f),
                            (sweep + 0.25f).coerceIn(0f, 1f) to primary.copy(alpha = 0.10f),
                            1f to primary.copy(alpha = 0.10f),
                        )
                    )
                else Modifier
            )
            .background(if (selected == true) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (isCurrent) {
            // Pulsing accent strip on the left edge.
            Box(Modifier.matchParentSize().padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                        .graphicsLayer { alpha = if (playing) fx?.glow?.value ?: 1f else 0.6f }
                        .background(Brush.verticalGradient(listOf(Lux.Gold, Lux.GoldDeep))),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = if (isCurrent) 10.dp else 16.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCurrent) SpinningCd(song.artworkUri, 40.dp) else Artwork(song.artworkUri, 40.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = if (isCurrent && fx != null) TextOverflow.Clip else TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) Lux.GoldDeep else MaterialTheme.colorScheme.onSurface,
                    // Long titles of the playing song glide sideways so the whole name is seen.
                    modifier = if (playing && fx != null) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 800) else Modifier,
                )
                Text(
                    listOf(song.artist, formatDuration(song.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected != null) {
                Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 8.dp))
            } else {
                if (isFavorite != null) FavoriteButton(isFavorite, onToggleFavorite)
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.common_more_options)) }
            }
        }
    }
    if (menuOpen) SongActionsSheet(song, actions, onDismiss = { menuOpen = false })
}

private class NowPlayingFx(val sweep: State<Float>, val bob: State<Float>, val glow: State<Float>)

@Composable
private fun rememberNowPlayingFx(): NowPlayingFx {
    val loop = rememberInfiniteTransition(label = "nowPlaying")
    val sweep = loop.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val bob = loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val glow = loop.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    return remember(loop) { NowPlayingFx(sweep, bob, glow) }
}

/** Three bouncing equaliser bars; they rest low while paused. */
@Composable
fun PlayingBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    if (LocalLiteMode.current) {
        // Lite mode: three still bars, taller while playing.
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            floatArrayOf(0.6f, 1f, 0.75f).forEach { h ->
                Box(Modifier.weight(1f).fillMaxHeight(if (playing) h else 0.3f).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
        return
    }
    val t = rememberInfiniteTransition(label = "bars")
    val speeds = intArrayOf(520, 380, 640)
    val heights = speeds.mapIndexed { i, ms ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms, delayMillis = i * 90, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar$i")
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            val v = if (playing) h.value else 0.3f
            Box(Modifier.weight(1f).fillMaxHeight(v).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

/** How the song menu's buttons look; picked in Settings (Vikash chose C and H on 13 Nov 2026). */
enum class MenuStyle(@androidx.annotation.StringRes val labelRes: Int) {
    /** Soft raised circles that sink into the sheet when tapped. */
    Raised(R.string.menu_style_raised),
    /** Compact pills that wrap, so more fits on a short phone. */
    Pills(R.string.menu_style_pills),
}

val LocalMenuStyle = compositionLocalOf { MenuStyle.Raised }

/**
 * The song menu as a bottom sheet: soft raised round buttons, or wrapping pills, popping in one
 * after another.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SongActionsSheet(song: Song, actions: List<SongAction>, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val style = LocalMenuStyle.current
    val haptics = rememberHaptics()
    var fired by remember { mutableStateOf(false) }
    val run: (SongAction) -> Unit = { action ->
        // A second tap while the sheet slides away must not run an action twice.
        if (!fired) {
            fired = true
            haptics.tick()
            scope.launch { sheet.hide() }.invokeOnCompletion {
                onDismiss()
                action.onClick()
            }
        }
    }
    // The raised buttons get their light and shadow from the sheet colour itself.
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = base) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.artworkUri, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(18.dp))
            when (style) {
                MenuStyle.Raised -> FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    maxItemsInEachRow = 4,
                ) {
                    actions.forEachIndexed { i, action ->
                        RaisedActionButton(action, index = i, base = base, modifier = Modifier.weight(1f)) { run(action) }
                    }
                    // Keep the last row's buttons the same width as the others.
                    repeat((4 - actions.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
                }
                MenuStyle.Pills -> FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    actions.forEachIndexed { i, action -> PillActionButton(action, index = i) { run(action) } }
                }
            }
        }
    }
}

/** Springs each button in a little after the one before it. */
@Composable
private fun rememberAppear(index: Int): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val appear = remember { Animatable(0f) }
    val lite = LocalLiteMode.current
    LaunchedEffect(Unit) {
        if (lite) { appear.snapTo(1f); return@LaunchedEffect }
        delay(35L * index)
        appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
    }
    return appear
}

private fun Modifier.appearAndPress(appear: Float, pressed: Float): Modifier = graphicsLayer {
    alpha = appear.coerceIn(0f, 1f)
    val s = (0.6f + 0.4f * appear) * (1f - 0.06f * pressed)
    scaleX = s; scaleY = s
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/** Style C: a soft raised circle in the sheet's own colour; it sinks in while held. */
@Composable
private fun RaisedActionButton(action: SongAction, index: Int, base: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val appear = rememberAppear(index)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val held by source.collectIsPressedAsState()
    val press by androidx.compose.animation.core.animateFloatAsState(if (held) 1f else 0f, tween(140), label = "raisedPress")
    val dark = lerpColor(base, Color.Black, 0.38f)
    val light = lerpColor(base, Color.White, 0.55f)
    val ink = if (action.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(
        modifier
            .appearAndPress(appear.value, press)
            // No clip here: the soft shadow spreads past the button's own box.
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).drawBehind {
                val r = size.minDimension / 2
                val spread = 9.dp.toPx()
                val lift = (1f - press)
                val edge = r / (r + spread)
                if (lift > 0.01f) {
                    // Shadow below right, light above left: the circle seems to rise out of the sheet.
                    val off = 5.dp.toPx() * lift
                    drawCircle(
                        Brush.radialGradient(
                            0f to dark.copy(alpha = 0.7f * lift), edge to dark.copy(alpha = 0.7f * lift), 1f to Color.Transparent,
                            center = center + androidx.compose.ui.geometry.Offset(off, off), radius = r + spread,
                        ),
                        radius = r + spread, center = center + androidx.compose.ui.geometry.Offset(off, off),
                    )
                    drawCircle(
                        Brush.radialGradient(
                            0f to light.copy(alpha = 0.55f * lift), edge to light.copy(alpha = 0.55f * lift), 1f to Color.Transparent,
                            center = center - androidx.compose.ui.geometry.Offset(off, off), radius = r + spread,
                        ),
                        radius = r + spread, center = center - androidx.compose.ui.geometry.Offset(off, off),
                    )
                }
                drawCircle(base, radius = r)
                if (press > 0.01f) {
                    // Pressed in: dark on the top left, light on the bottom right.
                    drawCircle(
                        Brush.linearGradient(
                            listOf(dark.copy(alpha = 0.55f * press), Color.Transparent, light.copy(alpha = 0.45f * press)),
                            start = androidx.compose.ui.geometry.Offset.Zero,
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        ),
                        radius = r,
                    )
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon ?: Icons.Default.MoreHoriz, null, tint = ink, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            action.label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (action.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Style H: a compact pill with the icon and label side by side. */
@Composable
private fun PillActionButton(action: SongAction, index: Int, onClick: () -> Unit) {
    val appear = rememberAppear(index)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val held by source.collectIsPressedAsState()
    val press by androidx.compose.animation.core.animateFloatAsState(if (held) 1f else 0f, tween(120), label = "pillPress")
    val on = MaterialTheme.colorScheme.onSurface
    val tint = if (action.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val fill = if (action.danger) tint.copy(alpha = 0.12f + 0.08f * press) else on.copy(alpha = 0.07f + 0.07f * press)
    val line = if (action.danger) tint.copy(alpha = 0.35f) else on.copy(alpha = 0.12f)
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .appearAndPress(appear.value, press)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, line, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon ?: Icons.Default.MoreHoriz, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            action.label,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (action.danger) tint else on,
        )
    }
}

/** Bar above the tabs: previous, play/pause and next, with the song's CD turning. */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
) {
    val current = state.current ?: return
    val haptics = rememberHaptics()
    // A frosted card floating just above the dock, in the same luxury style. Swipe it sideways
    // for the next or previous song, or up to open the player.
    Box(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp)
            .swipeToSkip(onNext = onNext, onPrevious = onPrevious, onUp = onOpen)
            .luxGlass(RoundedCornerShape(22.dp), elevation = 10.dp).clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 9.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                SpinningCd(current.artworkUri, 42.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    current.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.common_previous)) }
            GradientPlayButton(state.isPlaying, 48.dp) { haptics.tick(); onToggle() }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, stringResource(R.string.common_next)) }
        }
    }
}
