package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.media3.common.Player
import com.vikash.mp3player.playback.PlayerConnection
import com.vikash.mp3player.playback.PlayerUiState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val INTRO_MS = 2600

/**
 * The record player that opens when a song is tapped. The deck rises in, the record drops onto
 * the platter and the arm swings onto it. The song is already playing. The view then stays open,
 * the record turning with the music and following the playing song, until the user swipes left
 * or right (or presses Back). A tap skips the intro. The song name sits on a tinted strip under the
 * player, with shuffle, previous, play/pause, next and repeat below it.
 */
@Composable
fun DiscChangeOverlay(
    title: String,
    artist: String,
    artworkUri: android.net.Uri?,
    state: PlayerUiState,
    player: PlayerConnection,
    onDone: () -> Unit,
) {
    val isPlaying = state.isPlaying
    val progress = { player.positionMs().toFloat() / state.durationMs.coerceAtLeast(1) }
    val done by rememberUpdatedState(onDone)
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(INTRO_MS, easing = LinearEasing)) }
    BackHandler { done() }

    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val fg = MaterialTheme.colorScheme.onBackground

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = swipe.value
                alpha = 1f - (abs(swipe.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(swipe.value) > threshold) {
                                val out = if (swipe.value > 0) size.width.toFloat() else -size.width.toFloat()
                                swipe.animateTo(out, tween(220))
                                done()
                            } else {
                                swipe.animateTo(0f, spring())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f, spring()) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + dx) }
                }
            }
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                if (intro.value < 1f) scope.launch { intro.snapTo(1f) }
            },
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RecordPlayerView(
                artworkUri = artworkUri, title = title, artist = artist,
                isPlaying = isPlaying, progress = progress, intro = { intro.value }, alignBottom = true,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp),
            )
            // Song name right under the record player, on a tinted strip.
            Column(
                Modifier
                    .alpha(easeOut(seg(intro.value, 0.5f, 0.85f)))
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    artist, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val active = MaterialTheme.colorScheme.primary
                val inactive = MaterialTheme.colorScheme.onSurfaceVariant
                IconButton(onClick = player::toggleShuffle) {
                    Icon(Icons.Default.Shuffle, stringResource(R.string.player_shuffle), tint = if (state.shuffle) active else inactive)
                }
                IconButton(onClick = { player.previous() }) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.common_previous), Modifier.size(36.dp), tint = fg)
                }
                GradientPlayButton(isPlaying, 68.dp, player::togglePlay)
                IconButton(onClick = { player.next() }) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.common_next), Modifier.size(36.dp), tint = fg)
                }
                IconButton(onClick = player::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        stringResource(R.string.player_repeat),
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) inactive else active,
                    )
                }
            }
        }
    }
}

/**
 * A flat record player: the song's poster and name on the record label, the record turning with
 * the music, and a tone arm that swings onto the record while the song plays, moves inward as the
 * song goes on, and lifts off on pause or when the song changes. Used by Now Playing and by the
 * song-tap view. [intro] runs 0..1 for the song-tap entrance; Now Playing leaves it at 1.
 */
@Composable
fun RecordPlayerView(
    artworkUri: android.net.Uri?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    intro: () -> Float = { 1f },
    alignBottom: Boolean = false,
) {
    val spin = LocalDiscAngle.current
    val art = rememberStickerPainter(artworkUri, 640)
    val logo = painterResource(R.drawable.ic_launcher_foreground)
    val text = rememberTextMeasurer()
    val colors = deckColors(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val arm = rememberArmAngle(title, isPlaying, progress, intro)
    Canvas(modifier) {
        val t = intro()
        val w = min(size.width * 0.92f, size.height / DECK_RATIO)
        val rise = easeOut(seg(t, 0f, 0.25f))
        val x0 = (size.width - w) / 2f
        val y0 = (if (alignBottom) size.height - w * DECK_RATIO else (size.height - w * DECK_RATIO) / 2f) + (1f - rise) * w * 0.12f
        drawDeck(
            x0, y0, w, colors, spin.value, arm.value, title, artist, art, logo, text,
            playing = isPlaying, drop = seg(t, 0.2f, 0.55f), alpha = rise,
        )
    }
}

/** Arm angle in degrees: 0 is at rest beside the record; it eases toward where the needle should be. */
@Composable
private fun rememberArmAngle(title: String, isPlaying: Boolean, progress: () -> Float, intro: () -> Float): State<Float> {
    val angle = remember { mutableFloatStateOf(0f) }
    val playing = rememberUpdatedState(isPlaying)
    val prog = rememberUpdatedState(progress)
    val introNow = rememberUpdatedState(intro)
    // When the song changes the arm lifts off, then comes back to the outer groove.
    val liftUntil = remember { mutableLongStateOf(0L) }
    val firstTitle = remember { title }
    LaunchedEffect(title) {
        if (title != firstTitle || liftUntil.longValue != 0L) liftUntil.longValue = System.nanoTime() + 900_000_000L
    }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val lifting = System.nanoTime() < liftUntil.longValue
            if (!playing.value && !lifting && abs(angle.floatValue) < 0.05f && introNow.value() >= 1f) {
                angle.floatValue = 0f
                last = 0L
                snapshotFlow { playing.value }.first { it }
                continue
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val onRecord = playing.value && System.nanoTime() >= liftUntil.longValue && introNow.value() >= 0.6f
                val target = if (onRecord) armFor(0.93f - 0.53f * prog.value().coerceIn(0f, 1f)) else 0f
                angle.floatValue += (target - angle.floatValue) * min(1f, dt * 3.2f)
            }
        }
    }
    return angle
}

private const val DECK_RATIO = 1.2f

// Deck layout, in deck widths: the record centre and radius, and the arm pivot.
private const val REC_X = 0.49f
private const val REC_Y = 0.44f * DECK_RATIO
private const val REC_R = 0.41f
private const val PIV_X = 0.75f
private const val PIV_Y = 0.81f * DECK_RATIO

// The arm, relative to the pivot with the arm at rest: tube end, bend start, bend control, needle.
private val ARM_A = Offset(0.15f, 0.045f)
private val ARM_B = Offset(-0.47f, 0.045f)
private val ARM_Q = Offset(-0.535f, 0.045f)
private val ARM_D = Offset(-0.60f, -0.008f)

private fun rot(p: Offset, deg: Float): Offset {
    val r = deg * PI.toFloat() / 180f
    val c = cos(r)
    val s = sin(r)
    return Offset(p.x * c - p.y * s, p.x * s + p.y * c)
}

/** Arm angle that puts the needle [fraction] of the record radius from its centre. */
private fun armFor(fraction: Float): Float {
    val want = fraction * REC_R
    var lo = 0f
    var hi = 70f
    repeat(22) {
        val m = (lo + hi) / 2f
        val d = rot(ARM_D, m)
        if (hypot(PIV_X + d.x - REC_X, PIV_Y + d.y - REC_Y) > want) lo = m else hi = m
    }
    return (lo + hi) / 2f
}

private class DeckColors(
    val deck0: Color, val deck1: Color, val edge: Color, val hair: Color, val brand: Color,
    val arm: Color, val weight: Color, val weightRing: Color, val text: Color, val rest: Color, val knob: Color,
)

private fun deckColors(dark: Boolean) = if (dark) DeckColors(
    deck0 = Color(0xFF211C25), deck1 = Color(0xFF19151D), edge = Color.White.copy(alpha = 0.07f),
    hair = Color.White.copy(alpha = 0.14f), brand = Ampli.Blush, arm = Color(0xFFD6D1DA), weight = Color(0xFF2E2833),
    weightRing = Ampli.Blush, text = Color(0xFFF2EEF0).copy(alpha = 0.55f), rest = Color(0xFF0E0C11),
    knob = Color.White.copy(alpha = 0.18f),
) else DeckColors(
    deck0 = Color(0xFFF7F2F4), deck1 = Color(0xFFEEE7EA), edge = Color(0xFF0F0D14).copy(alpha = 0.07f),
    hair = Color(0xFF0F0D14).copy(alpha = 0.16f), brand = Ampli.Rose, arm = Color(0xFF1D1A21), weight = Color(0xFF141216),
    weightRing = Ampli.Rose, text = Color(0xFF17141B).copy(alpha = 0.5f), rest = Color(0xFF1D1A21),
    knob = Color(0xFF0F0D14).copy(alpha = 0.22f),
)

private val Vinyl = Color(0xFF121014)

private fun ringPath(c: Offset, outer: Float, inner: Float) = Path().apply {
    fillType = PathFillType.EvenOdd
    addOval(Rect(c, outer))
    addOval(Rect(c, inner))
}

/** Draws the deck, [w] wide, with its top left corner at ([x0], [y0]). No shadow or glow. */
private fun DrawScope.drawDeck(
    x0: Float, y0: Float, w: Float, k: DeckColors, spin: Float, arm: Float,
    title: String, artist: String, art: Painter?, logo: Painter, text: TextMeasurer,
    playing: Boolean, drop: Float, alpha: Float,
) {
    if (alpha <= 0f || w <= 1f) return
    val h = w * DECK_RATIO
    // Deck: flat, matte, with a hairline edge.
    drawRoundRect(
        Brush.linearGradient(listOf(k.deck0, k.deck1), Offset(x0, y0), Offset(x0 + w, y0 + h)),
        Offset(x0, y0), Size(w, h), CornerRadius(w * 0.035f), alpha = alpha,
    )
    drawRoundRect(k.edge, Offset(x0, y0), Size(w, h), CornerRadius(w * 0.035f), style = Stroke(1f), alpha = alpha)

    // Brand, written down the top right corner.
    val brand = text.measure(
        "AMPLI",
        TextStyle(color = k.brand, fontSize = (w * 0.038f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = 0.2.em),
    )
    val bp = Offset(x0 + w * 0.925f, y0 + w * 0.07f)
    rotate(90f, pivot = bp) {
        drawText(brand, topLeft = Offset(bp.x, bp.y - brand.size.height / 2f), alpha = alpha)
    }

    // Power ring with a rose light while playing.
    val pr = Offset(x0 + w * 0.11f, y0 + w * 0.11f)
    drawCircle(k.hair, w * 0.045f, pr, style = Stroke(1f), alpha = alpha)
    drawCircle(if (playing) Ampli.Rose else k.knob, w * 0.007f, Offset(pr.x, pr.y - w * 0.022f), alpha = alpha)
    val pl = text.measure(if (playing) "PLAY" else "PAUSE", TextStyle(color = k.text, fontSize = (w * 0.017f).toSp(), fontWeight = FontWeight.SemiBold))
    drawText(pl, topLeft = Offset(pr.x - pl.size.width / 2f, pr.y + w * 0.008f - pl.size.height / 2f), alpha = alpha)

    // Record, dropping onto the platter during the intro.
    val c = Offset(x0 + w * REC_X, y0 + w * REC_Y)
    val r = w * REC_R
    val d = easeOut(drop)
    if (d > 0f) {
        scale(1f + 0.25f * (1f - d), pivot = c) {
            drawRecord(c, r, spin, title, artist, art, logo, text, alpha * d)
        }
    }

    // Knobs: speed and volume, like the knob in the Ampli logo.
    listOf(Triple(0.40f, "33", 0f), Triple(0.57f, "VOL", 40f)).forEach { (fx, label, deg) ->
        val kc = Offset(x0 + w * fx, y0 + h * 0.925f)
        val kr = w * 0.05f
        drawCircle(k.hair, kr, kc, style = Stroke(1f), alpha = alpha)
        drawLine(
            Ampli.Rose, kc + rot(Offset(0f, -kr * 0.2f), deg), kc + rot(Offset(0f, -kr * 0.75f), deg),
            strokeWidth = maxOf(1.5f, w * 0.006f), cap = StrokeCap.Round, alpha = alpha,
        )
        val lab = text.measure(label, TextStyle(color = k.text, fontSize = (w * 0.017f).toSp(), fontWeight = FontWeight.SemiBold))
        drawText(lab, topLeft = Offset(kc.x + kr * 1.25f, kc.y - lab.size.height / 2f), alpha = alpha)
    }

    // Tone arm: base ring, rest post, tube, rose headshell and the counterweight on the pivot.
    val p = Offset(x0 + w * PIV_X, y0 + w * PIV_Y)
    drawCircle(k.hair, w * 0.12f, p, style = Stroke(1f), alpha = alpha)
    drawRoundRect(
        k.rest, Offset(x0 + w * 0.478f, p.y + w * 0.045f - w * 0.022f), Size(w * 0.024f, w * 0.044f),
        CornerRadius(w * 0.012f), alpha = alpha,
    )
    fun pt(o: Offset) = p + rot(o, arm) * w
    val a = pt(ARM_A)
    val b = pt(ARM_B)
    val q = pt(ARM_Q)
    val n = pt(ARM_D)
    val tube = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        quadraticTo(q.x, q.y, n.x, n.y)
    }
    drawPath(tube, k.arm, alpha = alpha, style = Stroke(w * 0.014f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val ang = atan2(n.y - q.y, n.x - q.x) * 180f / PI.toFloat()
    rotate(ang, pivot = n) {
        drawRoundRect(Ampli.Rose, Offset(n.x - w * 0.035f, n.y - w * 0.014f), Size(w * 0.05f, w * 0.028f), CornerRadius(w * 0.006f), alpha = alpha)
    }
    drawCircle(k.weight, w * 0.06f, p, alpha = alpha)
    drawCircle(k.weightRing, w * 0.06f, p, style = Stroke(1.2f), alpha = alpha * 0.6f)
    drawCircle(Ampli.Rose, w * 0.008f, p + rot(Offset(0f, -w * 0.04f), arm - 30f), alpha = alpha)
}

/** A black record with grooves, fixed light on the vinyl, and the poster label with the song name. */
private fun DrawScope.drawRecord(
    c: Offset, r: Float, spin: Float, title: String, artist: String,
    art: Painter?, logo: Painter, text: TextMeasurer, alpha: Float,
) {
    drawCircle(Vinyl, r, c, alpha = alpha)
    for (i in 0 until 46) {
        val major = i % 9 == 0
        val gr = r * (0.385f + 0.6f * i / 45f)
        val a = if (major) 0.11f else 0.035f + 0.025f * ((i * 7) % 3)
        drawCircle(Color.White.copy(alpha = a), gr, c, style = Stroke(if (major) 1.2f else 0.7f), alpha = alpha)
    }
    drawPath(
        ringPath(c, r * 0.99f, r * 0.37f),
        Brush.sweepGradient(
            0f to Color.Transparent, 0.08f to Color.White.copy(alpha = 0.13f), 0.15f to Color.Transparent,
            0.5f to Color.Transparent, 0.58f to Color.White.copy(alpha = 0.10f), 0.65f to Color.Transparent,
            1f to Color.Transparent, center = c,
        ),
        alpha = alpha,
    )
    drawCircle(Ampli.Rose.copy(alpha = 0.45f), r * 0.993f, c, style = Stroke(1.2f), alpha = alpha)

    val l = r * 0.37f
    rotate(spin, pivot = c) {
        clipPath(Path().apply { addOval(Rect(c, l)) }) {
            if (art != null) {
                drawCover(art, c, l * 2f, alpha)
            } else {
                drawCircle(Ampli.Gradient, l, c, alpha = alpha)
                drawCover(logo, c, l * 1.4f, alpha)
            }
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0x000A080C), 0.25f to Color(0x8C0A080C), 1f to Color(0xB30A080C),
                    startY = c.y - l * 0.05f, endY = c.y + l,
                ),
                topLeft = Offset(c.x - l, c.y - l * 0.05f), size = Size(l * 2f, l * 1.05f), alpha = alpha,
            )
            labelLine(text, title, c, l * 0.33f, l * 1.5f, TextStyle(color = Color.White, fontSize = (l * 0.19f).toSp(), fontWeight = FontWeight.Bold), alpha)
            labelLine(text, artist, c, l * 0.56f, l * 1.3f, TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = (l * 0.12f).toSp()), alpha)
            labelLine(
                text, "AMPLI · 33⅓", c, -l * 0.62f, l * 1.4f,
                TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = (l * 0.1f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = 0.3.em),
                alpha,
            )
        }
    }
    drawCircle(Color.Black.copy(alpha = 0.4f), l, c, style = Stroke(1f), alpha = alpha)
    drawCircle(Color(0xFFE4E1E6), r * 0.03f, c, alpha = alpha)
    drawCircle(Color(0xFF2A2630), r * 0.012f, c, alpha = alpha)
}

/** One centred line of label text at [dy] below the record centre, cut with an ellipsis to [maxW]. */
private fun DrawScope.labelLine(text: TextMeasurer, s: String, c: Offset, dy: Float, maxW: Float, style: TextStyle, alpha: Float) {
    if (s.isBlank() || maxW < 1f) return
    val m = text.measure(
        s, style, overflow = TextOverflow.Ellipsis, maxLines = 1, softWrap = false,
        constraints = Constraints(maxWidth = maxW.toInt().coerceAtLeast(1)),
    )
    drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y + dy - m.size.height / 2f), alpha = alpha)
}

/** 0..1 progress of [t] inside the window [a]..[b]. */
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
