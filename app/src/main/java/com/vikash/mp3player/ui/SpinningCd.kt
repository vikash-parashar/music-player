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

/** The song's poster printed as the label of a small record that turns with the music. */
@Composable
fun SpinningCd(uri: Uri?, size: Dp, modifier: Modifier = Modifier, spinning: Boolean = true) {
    val angle = LocalDiscAngle.current
    val art = rememberStickerPainter(uri, if (size.value > 120f) 640 else 160)
    val logo = painterResource(R.drawable.ic_launcher_foreground)
    Canvas(modifier.size(size)) {
        drawCd(center, size.toPx() / 2f, if (spinning) angle.value else 0f, art, logo)
    }
}

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

/** A small record: black vinyl with grooves, the poster as the label, and a still gloss. */
fun DrawScope.drawCd(c: Offset, r: Float, angle: Float, art: Painter?, logo: Painter, alpha: Float = 1f) {
    drawCircle(Color(0xFF121014), radius = r, center = c, alpha = alpha)
    for (i in 0 until 5) {
        drawCircle(Color.White.copy(alpha = 0.07f * alpha), radius = r * (0.68f + 0.07f * i), center = c, style = Stroke(max(0.6f, r * 0.012f)))
    }
    drawPath(
        ring(c, r * 0.98f, r * 0.58f),
        Brush.sweepGradient(
            0f to Color.Transparent, 0.08f to Color.White.copy(alpha = 0.16f), 0.16f to Color.Transparent,
            0.5f to Color.Transparent, 0.58f to Color.White.copy(alpha = 0.12f), 0.66f to Color.Transparent,
            1f to Color.Transparent, center = c,
        ),
        alpha = alpha,
    )
    rotate(angle, pivot = c) {
        clipPath(Path().apply { addOval(Rect(c, r * 0.56f)) }) {
            if (art != null) {
                drawCover(art, c, r * 1.12f, alpha)
            } else {
                drawCircle(Ampli.Gradient, radius = r * 0.56f, center = c, alpha = alpha)
                drawCover(logo, c, r * 0.9f, alpha)
            }
        }
    }
    drawCircle(Color(0xFF0B0A0E).copy(alpha = alpha), radius = max(1.5f, r * 0.07f), center = c)
    drawCircle(Ampli.Rose.copy(alpha = 0.5f * alpha), radius = r - 0.5f, center = c, style = Stroke(1f))
}
