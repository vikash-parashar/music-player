package com.vikash.mp3player.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The six looks for the Speed and pitch bars. Free users get Minimal line; Pro can pick any. */
enum class SpeedBarStyle(@StringRes val label: Int) {
    MINIMAL(R.string.bar_style_minimal),
    RULER(R.string.bar_style_ruler),
    EMBER(R.string.bar_style_ember),
    STEPS(R.string.bar_style_steps),
    GROOVE(R.string.bar_style_groove),
    KNOBS(R.string.bar_style_knobs);

    companion object {
        private const val KEY = "speed_bar_style"
        private fun prefs(c: Context) = c.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)

        /** The style to draw: the saved pick for Pro, always Minimal line for free users. */
        fun current(c: Context, isPro: Boolean): SpeedBarStyle =
            if (!isPro) MINIMAL else entries.getOrElse(prefs(c).getInt(KEY, 0)) { MINIMAL }

        fun save(c: Context, style: SpeedBarStyle) = prefs(c).edit().putInt(KEY, style.ordinal).apply()
    }
}

private const val MIN = 0.5f
private const val MAX = 2f
private val Gold = Color(0xFFF59E0B)
private val Ember = Color(0xFFFF7A3C)
private val Glow = Color(0xFFFF5A4E)

private fun frac(v: Float) = ((v - MIN) / (MAX - MIN)).coerceIn(0f, 1f)
private fun snap05(v: Float) = (Math.round(v.coerceIn(MIN, MAX) * 20f) / 20f)
private fun fromFrac(f: Float) = snap05(MIN + f.coerceIn(0f, 1f) * (MAX - MIN))

/** One bar for a 0.5x to 2x value, drawn in [style]. Values snap to 0.05 steps. */
@Composable
fun SpeedBar(style: SpeedBarStyle, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    when (style) {
        SpeedBarStyle.RULER -> RulerBar(value, onChange, modifier)
        SpeedBarStyle.KNOBS -> KnobBar(value, onChange, modifier)
        else -> TrackBar(style, value, onChange, modifier)
    }
}

@Composable
private fun TrackBar(style: SpeedBarStyle, value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val track = ink.copy(alpha = 0.12f)
    val latest by rememberUpdatedState(onChange)
    val f = frac(value)
    Box(modifier.fillMaxWidth()) {
        if (style == SpeedBarStyle.EMBER) {
            // Value bubble that rides above the knob.
            Text(
                "%.2fx".format(value),
                color = surface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0))
                        val pad = 18.dp.toPx()
                        val cx = pad + (c.maxWidth - 2 * pad) * f
                        layout(c.maxWidth, p.height) {
                            p.place((cx - p.width / 2f).roundToInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0)), 0)
                        }
                    }
                    .background(ink, RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = if (style == SpeedBarStyle.EMBER) 22.dp else 0.dp)
                .height(44.dp)
                .pointerInput(Unit) {
                    val pad = 18.dp.toPx()
                    detectTapGestures { latest(fromFrac((it.x - pad) / (size.width - 2 * pad))) }
                }
                .pointerInput(Unit) {
                    val pad = 18.dp.toPx()
                    detectHorizontalDragGestures { ch, _ ->
                        ch.consume()
                        latest(fromFrac((ch.position.x - pad) / (size.width - 2 * pad)))
                    }
                },
        ) {
            val pad = 18.dp.toPx()
            val w = size.width - 2 * pad
            val cy = size.height / 2
            val kx = pad + w * f
            when (style) {
                SpeedBarStyle.EMBER -> {
                    val h = 12.dp.toPx()
                    drawRoundRect(track, Offset(pad, cy - h / 2), Size(w, h), CornerRadius(h / 2))
                    if (kx > pad) drawRoundRect(
                        Brush.horizontalGradient(listOf(Gold, Ember, Glow), startX = pad, endX = pad + w),
                        Offset(pad, cy - h / 2), Size(kx - pad, h), CornerRadius(h / 2),
                    )
                    drawCircle(Ember.copy(alpha = 0.25f), 17.dp.toPx(), Offset(kx, cy + 2.dp.toPx()))
                    drawCircle(Color.White, 14.dp.toPx(), Offset(kx, cy))
                    drawCircle(Ember, 12.5.dp.toPx(), Offset(kx, cy), style = Stroke(3.dp.toPx()))
                }
                SpeedBarStyle.STEPS -> {
                    val n = 31
                    val gap = 3.dp.toPx()
                    val sw = (w - gap * (n - 1)) / n
                    val cur = (f * (n - 1)).roundToInt()
                    for (i in 0 until n) {
                        val h = if (i == cur) 34.dp.toPx() else 22.dp.toPx()
                        val c = when {
                            i == cur -> Glow
                            i < cur -> ink
                            else -> track
                        }
                        drawRoundRect(c, Offset(pad + i * (sw + gap), cy - h / 2), Size(sw, h), CornerRadius(4.dp.toPx()))
                    }
                }
                SpeedBarStyle.GROOVE -> {
                    val h = 16.dp.toPx()
                    // Pressed-in groove: darker at the top edge, lighter at the bottom.
                    drawRoundRect(
                        Brush.verticalGradient(listOf(ink.copy(alpha = 0.22f), ink.copy(alpha = 0.05f)), startY = cy - h / 2, endY = cy + h / 2),
                        Offset(pad, cy - h / 2), Size(w, h), CornerRadius(h / 2),
                    )
                    val fh = 8.dp.toPx()
                    if (kx > pad) drawRoundRect(Ember.copy(alpha = 0.85f), Offset(pad + 4.dp.toPx(), cy - fh / 2), Size((kx - pad - 4.dp.toPx()).coerceAtLeast(0f), fh), CornerRadius(fh / 2))
                    drawCircle(Color.Black.copy(alpha = 0.22f), 16.dp.toPx(), Offset(kx + 3.dp.toPx(), cy + 3.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = 0.35f), 16.dp.toPx(), Offset(kx - 2.dp.toPx(), cy - 2.dp.toPx()))
                    drawCircle(surface, 16.dp.toPx(), Offset(kx, cy))
                    drawCircle(Ember, 5.dp.toPx(), Offset(kx, cy))
                }
                else -> { // MINIMAL
                    val h = 4.dp.toPx()
                    drawRoundRect(track, Offset(pad, cy - h / 2), Size(w, h), CornerRadius(h / 2))
                    drawRoundRect(ink, Offset(pad, cy - h / 2), Size(kx - pad, h), CornerRadius(h / 2))
                    val mx = pad + w * frac(1f)
                    drawLine(ink.copy(alpha = 0.4f), Offset(mx, cy - 7.dp.toPx()), Offset(mx, cy + 7.dp.toPx()), 2.dp.toPx())
                    drawCircle(Glow.copy(alpha = 0.18f), 18.dp.toPx(), Offset(kx, cy))
                    drawCircle(ink, 12.dp.toPx(), Offset(kx, cy))
                }
            }
        }
    }
}

/** Radio-dial look: the scale slides under a fixed red needle. Drag left to go up. */
@Composable
private fun RulerBar(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val latest by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(Unit) {
                val step = 10.dp.toPx() // pixels per 0.05x
                var acc = current
                detectHorizontalDragGestures(onDragStart = { acc = current }) { ch, dx ->
                    ch.consume()
                    acc = (acc - dx / step * 0.05f).coerceIn(MIN, MAX)
                    latest(snap05(acc))
                }
            }
            .pointerInput(Unit) {
                val step = 10.dp.toPx()
                detectTapGestures { latest(snap05(current + ((it.x - size.width / 2f) / step).roundToInt() * 0.05f)) }
            },
    ) {
        val step = 10.dp.toPx()
        val cx = size.width / 2
        val bottom = size.height - 8.dp.toPx()
        for (i in 0..30) {
            val v = MIN + i * 0.05f
            val x = cx + (v - value) / 0.05f * step
            if (x < 0 || x > size.width) continue
            val fade = (1f - abs(x - cx) / cx).coerceIn(0f, 1f)
            val big = i % 5 == 0
            val h = if (big) 24.dp.toPx() else 12.dp.toPx()
            drawLine(ink.copy(alpha = (if (big) 0.9f else 0.55f) * fade), Offset(x, bottom - h), Offset(x, bottom), 2.dp.toPx())
        }
        drawLine(Glow.copy(alpha = 0.3f), Offset(cx, 0f), Offset(cx, size.height), 9.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Glow, Offset(cx, 0f), Offset(cx, size.height), 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** A round knob: turn it (drag around it) to set the value, 270 degrees of travel. */
@Composable
private fun KnobBar(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val latest by rememberUpdatedState(onChange)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(112.dp)
                .pointerInput(Unit) {
                    fun set(p: Offset) {
                        val deg = Math.toDegrees(atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())).toFloat()
                        var a = (deg - 135f + 720f) % 360f
                        if (a > 270f) a = if (a > 315f) 0f else 270f
                        latest(fromFrac(a / 270f))
                    }
                    detectDragGestures(onDragStart = { set(it) }) { ch, _ -> ch.consume(); set(ch.position) }
                },
        ) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            val tl = Offset(inset, inset)
            drawArc(ink.copy(alpha = 0.12f), 135f, 270f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(Gold, Ember, Glow, Gold)),
                135f, 270f * frac(value), false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val ang = Math.toRadians((135f + 270f * frac(value)).toDouble())
            val r = arcSize.width / 2
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(ink, 7.dp.toPx(), Offset(c.x + r * cos(ang).toFloat(), c.y + r * sin(ang).toFloat()))
        }
        Text("%.2fx".format(value), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = CarTitleFont)
    }
}
