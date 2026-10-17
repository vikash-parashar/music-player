package com.vikash.mp3player.ui

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.PlayerConnection
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min

/** Turning angle of the playing CD, shared by Now Playing, the mini player and the playing row. */
val LocalDiscAngle = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** Full speed in degrees per second. */
const val DISC_MAX_SPEED = 150f

/**
 * Drives [LocalDiscAngle] like a real disc: it starts slowly when a song begins, turns while it
 * plays, slows over the last seconds of the song and coasts to a stop on pause. Sleeps while
 * nothing plays, so it costs nothing when idle.
 */
@Composable
fun rememberDiscSpin(player: PlayerConnection): State<Float> {
    val angle: MutableFloatState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        var speed = 0f
        var last = 0L
        var lastPoll = 0L
        var pos = 0L
        while (true) {
            val st = player.state.value
            if (!st.isPlaying && speed < 0.5f) {
                speed = 0f
                last = 0L
                player.state.first { it.isPlaying }
                continue
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                if (now - lastPoll > 250_000_000L) {
                    pos = player.positionMs()
                    lastPoll = now
                } else if (st.isPlaying) {
                    pos += (dt * 1000).toLong()
                }
                val target = if (st.isPlaying) DISC_MAX_SPEED * speedFactor(pos, st.durationMs) else 0f
                val rate = if (target > speed) 1.6f else 1.2f
                speed += (target - speed) * min(1f, dt * rate)
                angle.floatValue = (angle.floatValue + speed * dt) % 360f
            }
        }
    }
    return angle
}

/** 0..1: slow start over the first 3 s, slow down over the last 8 s. */
private fun speedFactor(posMs: Long, durMs: Long): Float {
    val rampIn = (posMs / 3000f).coerceIn(0f, 1f)
    val rampOut = if (durMs > 0) ((durMs - posMs) / 8000f).coerceIn(0f, 1f) else 1f
    val f = min(rampIn, rampOut)
    return 0.06f + 0.94f * FastOutSlowInEasing.transform(f)
}

/** Loads the song poster for the CD sticker; null until it is ready or when there is none. */
@Composable
fun rememberStickerPainter(uri: Uri?, px: Int = 512): Painter? {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(remember(uri) { ImageRequest.Builder(context).data(uri).size(px).build() })
    val state by painter.state.collectAsState()
    return painter.takeIf { uri != null && state is AsyncImagePainter.State.Success }
}

/** The song's poster printed as a round sticker on a CD that turns with the music. */
@Composable
fun SpinningCd(uri: Uri?, size: Dp, modifier: Modifier = Modifier, spinning: Boolean = true) {
    val angle = LocalDiscAngle.current
    val art = rememberStickerPainter(uri, if (size.value > 120f) 640 else 160)
    val logo = painterResource(R.drawable.ic_launcher_foreground)
    Canvas(modifier.size(size)) {
        drawCd(center, size.toPx() / 2f, if (spinning) angle.value else 0f, art, logo)
    }
}

private val DiscSheen = listOf(
    Color(0xFFD9DCE6), Color(0xFFFBCFE8), Color(0xFFA5F3FC), Color(0xFFE9D5FF),
    Color(0xFFFDE68A), Color(0xFFD9DCE6), Color(0xFFBAE6FD), Color(0xFFF5D0FE), Color(0xFFD9DCE6),
)

private fun ring(c: Offset, outer: Float, inner: Float) = Path().apply {
    fillType = PathFillType.EvenOdd
    addOval(Rect(c, outer))
    addOval(Rect(c, inner))
}

/** Draws [painter] cropped to fill a square of side [side] centred on [c]. */
fun DrawScope.drawCover(painter: Painter, c: Offset, side: Float, alpha: Float = 1f) {
    val src = painter.intrinsicSize
    val (w, h) = if (src.isSpecified && src.width > 0f && src.height > 0f) {
        val s = max(side / src.width, side / src.height)
        src.width * s to src.height * s
    } else side to side
    translate(c.x - w / 2f, c.y - h / 2f) {
        with(painter) { draw(Size(w, h), alpha = alpha) }
    }
}

/** A CD: silver rim, the poster as a sticker, the clear hub ring and hole, and a still gloss. */
fun DrawScope.drawCd(c: Offset, r: Float, angle: Float, art: Painter?, logo: Painter, alpha: Float = 1f) {
    drawCircle(Brush.sweepGradient(DiscSheen, c), radius = r, center = c, alpha = alpha)
    rotate(angle, pivot = c) {
        clipPath(ring(c, r * 0.95f, r * 0.30f)) {
            if (art != null) {
                drawCover(art, c, r * 1.9f, alpha)
            } else {
                drawCircle(Ampli.Gradient, radius = r * 0.95f, center = c, alpha = alpha)
                drawCover(logo, c, r * 1.3f, alpha)
            }
        }
    }
    // Clear plastic hub and the hole.
    drawPath(ring(c, r * 0.30f, r * 0.15f), Color(0xFFE1E6EE).copy(alpha = 0.55f * alpha))
    drawCircle(Color.White.copy(alpha = 0.6f * alpha), radius = r * 0.22f, center = c, style = Stroke(max(1f, r * 0.012f)))
    drawCircle(Color(0xFF0B0A0E).copy(alpha = alpha), radius = r * 0.15f, center = c)
    // Gloss that does not turn, like light on a real disc.
    drawPath(
        ring(c, r * 0.95f, r * 0.15f),
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.32f), 0.35f to Color.Transparent, 0.62f to Color.Transparent,
            0.8f to Color.White.copy(alpha = 0.14f), 1f to Color.Transparent,
            start = c - Offset(r, r), end = c + Offset(r, r),
        ),
        alpha = alpha,
    )
    drawCircle(Color.White.copy(alpha = 0.45f * alpha), radius = r - 0.5f, center = c, style = Stroke(1f))
}
