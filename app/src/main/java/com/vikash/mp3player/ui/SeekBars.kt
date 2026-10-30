package com.vikash.mp3player.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.ui.theme.Ampli
import com.vikash.mp3player.ui.theme.Lux
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/** Animated seek bar styles the user can pick in Settings. The first two are free, the rest come with Ampli Pro. */
enum class SeekStyle(@StringRes val labelRes: Int, val pro: Boolean) {
    Classic(R.string.seek_classic, false),
    Squiggle(R.string.seek_squiggle, false),
    Aurora(R.string.seek_aurora, true),
    Heartbeat(R.string.seek_heartbeat, true),
    Neon(R.string.seek_neon, true),
    Gradient(R.string.seek_gradient, true),
    Pulse(R.string.seek_pulse, true),
    Liquid(R.string.seek_liquid, true),
    Ember(R.string.seek_ember, true),
    Equalizer(R.string.seek_equalizer, true),
}

/**
 * The Now Playing seek bar. Tap or drag anywhere to seek; [onSeek] reports the dragged
 * fraction and [onSeekFinished] fires on release. Animates only while [playing].
 */
@Composable
fun AnimatedSeekBar(
    progress: Float,
    playing: Boolean,
    style: SeekStyle,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    onSeek: ((Float) -> Unit)? = null,
    onSeekFinished: () -> Unit = {},
) {
    // Animation clock: runs while playing and holds still (without jumping) when paused.
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = -1L
        while (true) withFrameMillis { now ->
            if (last >= 0) clock.floatValue += (now - last) / 1000f
            last = now
        }
    }
    val time = clock.floatValue
    val seek by rememberUpdatedState(onSeek)
    val finished by rememberUpdatedState(onSeekFinished)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    var m = modifier.fillMaxWidth().height(height)
    if (onSeek != null) {
        m = m
            .pointerInput(Unit) {
                detectTapGestures { seek?.invoke((it.x / size.width).coerceIn(0f, 1f)); finished() }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { finished() },
                    onDragCancel = { finished() },
                ) { change, _ -> seek?.invoke((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    }
    Canvas(m) { drawSeek(style, progress.coerceIn(0f, 1f), time, track) }
}

private val Rose = Ampli.Rose
private val Blush = Ampli.Blush
private val Slate = Ampli.Slate

/** Stable pseudo-random number in 0..1 for an integer seed, so frames never flicker randomly. */
private fun hash(n: Int): Float {
    val x = sin(n * 12.9898f) * 43758.547f
    return x - floor(x)
}

private fun DrawScope.drawSeek(style: SeekStyle, p: Float, t: Float, track: Color) {
    val pad = 10.dp.toPx()
    val w = size.width - 2 * pad
    val x = pad + w * p
    val y = size.height / 2
    val gradient = Brush.horizontalGradient(listOf(Slate, Rose), startX = pad, endX = x.coerceAtLeast(pad + 1))

    fun baseLine(width: Float, fill: Brush = SolidColor(Rose)) {
        drawLine(track, Offset(pad, y), Offset(size.width - pad, y), width, StrokeCap.Round)
        drawLine(fill, Offset(pad, y), Offset(x, y), width, StrokeCap.Round)
    }

    when (style) {
        SeekStyle.Classic -> {
            // Luxury look: a thin gold line and a gold knob with a soft warm glow.
            baseLine(3.dp.toPx(), Brush.horizontalGradient(listOf(Lux.Gold, Lux.GoldDeep), startX = pad, endX = x.coerceAtLeast(pad + 1)))
            drawCircle(Lux.Glow.copy(alpha = 0.22f), 13.dp.toPx(), Offset(x, y))
            drawCircle(Lux.GoldDeep, 7.dp.toPx(), Offset(x, y))
            drawCircle(Color.White, 2.5.dp.toPx(), Offset(x, y))
        }

        SeekStyle.Squiggle -> {
            drawLine(track, Offset(x, y), Offset(size.width - pad, y), 4.dp.toPx(), StrokeCap.Round)
            val path = Path().apply {
                moveTo(pad, y)
                var xx = pad
                val amp = 4.dp.toPx(); val len = 7.dp.toPx()
                while (xx <= x) { lineTo(xx, y + sin(xx / len - t * 8) * amp); xx += 2f }
            }
            drawPath(path, Rose, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawRoundRect(Blush, Offset(x - 3.dp.toPx(), y - 11.dp.toPx()), Size(6.dp.toPx(), 22.dp.toPx()), CornerRadius(3.dp.toPx()))
        }

        SeekStyle.Aurora -> {
            drawLine(track, Offset(pad, y), Offset(size.width - pad, y), 8.dp.toPx(), StrokeCap.Round)
            listOf(Rose to 0f, Slate to 2f, Blush to 4f).forEach { (c, o) ->
                val path = Path().apply {
                    moveTo(pad, y + 6.dp.toPx())
                    var xx = pad
                    while (xx <= x) { lineTo(xx, y + sin(xx / 30.dp.toPx() * 3f + t * 1.5f + o) * 9.dp.toPx()); xx += 4f }
                    lineTo(x, y + 6.dp.toPx()); close()
                }
                drawPath(path, Brush.verticalGradient(listOf(c.copy(alpha = 0f), c.copy(alpha = 0.55f), c.copy(alpha = 0f)), startY = y - 14.dp.toPx(), endY = y + 10.dp.toPx()))
            }
            drawLine(Rose, Offset(pad, y), Offset(x, y), 8.dp.toPx(), StrokeCap.Round)
        }

        SeekStyle.Heartbeat -> {
            drawLine(track, Offset(x, y), Offset(size.width - pad, y), 2.dp.toPx())
            val u = 1.dp.toPx()
            val path = Path().apply {
                moveTo(pad, y)
                var xx = pad
                while (xx <= x) {
                    val ph = (((xx / u - t * 60) % 60) + 60) % 60
                    val dy = when {
                        ph < 4 -> -ph * 4
                        ph < 8 -> -16 + (ph - 4) * 7
                        ph < 11 -> 12 - (ph - 8) * 4
                        else -> 0f
                    }
                    lineTo(xx, y + dy * u); xx += u
                }
            }
            drawPath(path, Rose, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(Blush, 4.dp.toPx(), Offset(x, y))
        }

        SeekStyle.Neon -> {
            drawLine(track, Offset(pad, y), Offset(size.width - pad, y), 2.dp.toPx(), StrokeCap.Round)
            val flicker = if (hash(floor(t * 20).toInt()) < 0.04f) 0.35f else 1f
            listOf(16.dp to 0.12f, 10.dp to 0.25f, 6.dp to 0.8f).forEach { (lw, a) ->
                drawLine(Rose.copy(alpha = a * flicker), Offset(pad, y), Offset(x, y), lw.toPx(), StrokeCap.Round)
            }
            drawLine(Color.White.copy(alpha = flicker), Offset(pad, y), Offset(x, y), 2.dp.toPx(), StrokeCap.Round)
        }

        SeekStyle.Gradient -> {
            val h = 12.dp.toPx()
            drawRoundRect(track, Offset(pad, y - h / 2), Size(w, h), CornerRadius(h / 2))
            val shift = (t * 0.25f % 1f) * w * 2
            val flow = Brush.linearGradient(
                listOf(Slate, Rose, Blush, Rose, Slate),
                start = Offset(pad - w + shift, 0f), end = Offset(pad + shift, 0f),
                tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
            )
            drawRoundRect(flow, Offset(pad, y - h / 2), Size(x - pad, h), CornerRadius(h / 2))
        }

        SeekStyle.Pulse -> {
            val beat = exp(-((t * 2) % 1f) * 5)
            val h = (10 + 10 * beat).dp.toPx()
            drawRoundRect(track, Offset(pad, y - 5.dp.toPx()), Size(w, 10.dp.toPx()), CornerRadius(5.dp.toPx()))
            drawRoundRect(Rose.copy(alpha = 0.25f * beat), Offset(pad - 3, y - h / 2 - 3), Size(x - pad + 6, h + 6), CornerRadius(h / 2 + 3))
            drawRoundRect(gradient, Offset(pad, y - h / 2), Size(x - pad, h), CornerRadius(h / 2))
        }

        SeekStyle.Liquid -> {
            val h = size.height - 16.dp.toPx()
            val top = (size.height - h) / 2
            val capsule = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(pad, top, size.width - pad, top + h, CornerRadius(h / 2)))
            }
            drawPath(capsule, track)
            clipPath(capsule) {
                val liquid = Path().apply {
                    moveTo(pad, top + h)
                    var yy = top + h
                    while (yy >= top) { lineTo(x + sin(yy / 5.dp.toPx() + t * 5) * 5.dp.toPx(), yy); yy -= 2f }
                    lineTo(pad, top); close()
                }
                drawPath(liquid, gradient)
            }
        }

        SeekStyle.Ember -> {
            val ly = size.height - 12.dp.toPx()
            drawLine(track, Offset(pad, ly), Offset(size.width - pad, ly), 4.dp.toPx(), StrokeCap.Round)
            drawLine(gradient, Offset(pad, ly), Offset(x, ly), 4.dp.toPx(), StrokeCap.Round)
            val rise = ly - 6.dp.toPx()
            for (k in 0 until 40) {
                val life = (t * (0.6f + hash(k) * 0.6f) + hash(k + 99)) % 1f
                val px = pad + hash(k + 7) * (x - pad) + sin(life * 12 + k) * 3.dp.toPx()
                val py = ly - life * rise
                val color = when { life < 0.3f -> Color(0xFFFDBA74); life < 0.6f -> Rose; else -> Slate }
                drawCircle(color.copy(alpha = (1 - life) * 0.85f), (1.5f + hash(k + 3) * 2.5f).dp.toPx() * (1 - life) + 1, Offset(px, py))
            }
        }

        SeekStyle.Equalizer -> {
            val n = 46
            val bw = w / n
            for (i in 0 until n) {
                val f = (i + 0.5f) / n
                val played = f < p
                val amp = abs(sin(t * 7 + i * 1.7f) * sin(t * 3.1f + i))
                val h = if (played) (0.25f + 0.75f * amp) * (size.height - 10.dp.toPx()) else 4.dp.toPx()
                drawRoundRect(
                    if (played) gradient else SolidColor(track),
                    Offset(pad + i * bw + 1, size.height - 5.dp.toPx() - h),
                    Size(bw - 2, h),
                    CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

private fun SolidColor(c: Color): Brush = androidx.compose.ui.graphics.SolidColor(c)
