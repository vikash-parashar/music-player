package com.vikash.mp3player.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.vikash.mp3player.R
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * One-time festival greeting shown on app open during a festival window.
 * Diwali 2026: 8 Nov (shown 8–12 Nov, local time), once per install.
 */
object FestivalGreeting {
    private const val KEY = "festival_seen_diwali_2026"

    private fun inWindow(now: Calendar = Calendar.getInstance()): Boolean {
        val y = now.get(Calendar.YEAR)
        val m = now.get(Calendar.MONTH)
        val d = now.get(Calendar.DAY_OF_MONTH)
        return y == 2026 && m == Calendar.NOVEMBER && d in 8..12
    }

    fun shouldShow(c: Context): Boolean =
        inWindow() && !c.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markSeen(c: Context) {
        c.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

private val Gold = Color(0xFFFFC857)
private val Cream = Color(0xFFF6E7D2)
private val Peach = Color(0xFFFFD9A0)
private val Sparks = listOf(
    Color(0xFFFFC857), Color(0xFFFF5E7E), Color(0xFF7BDFF2),
    Color(0xFFF7AEF8), Color(0xFFB8F2E6), Color(0xFFFF9F1C),
)

private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Color, val twinkle: Boolean)
private class Rocket(val x: Float, var y: Float, val vy: Float, val targetY: Float, val color: Color)
private class Lantern(var x: Float, var y: Float, val speed: Float, val phase: Float, val size: Float)
private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)
private class Glitter(var x: Float, var y: Float, val v: Float, val phase: Float)

/** Smooth 0..1 progress of [t] between [start] and [start] + [dur]. */
private fun prog(t: Float, start: Float, dur: Float) = ((t - start) / dur).coerceIn(0f, 1f)

@Composable
fun DiwaliGreetingScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val haptic = LocalHapticFeedback.current
    var time by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val rnd = remember { Random(8112026) }
    val stars = remember { mutableListOf<Star>() }
    val lanterns = remember { mutableListOf<Lantern>() }
    val glitter = remember { mutableListOf<Glitter>() }
    val sparks = remember { mutableListOf<Spark>() }
    val rockets = remember { mutableListOf<Rocket>() }

    LaunchedEffect(canvasSize) {
        if (canvasSize == Size.Zero) return@LaunchedEffect
        val w = canvasSize.width
        val h = canvasSize.height
        if (stars.isEmpty()) {
            repeat(90) { stars += Star(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.6f, 0.5f + rnd.nextFloat() * 1.8f, rnd.nextFloat() * 6f) }
            repeat(9) { lanterns += Lantern(rnd.nextFloat() * w, h + rnd.nextFloat() * h * 0.6f, 0.6f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 6f, 0.6f + rnd.nextFloat() * 0.6f) }
            repeat(40) { glitter += Glitter(rnd.nextFloat() * w, rnd.nextFloat() * h, 0.7f + rnd.nextFloat() * 1.4f, rnd.nextFloat() * 6f) }
        }
        val start = withFrameNanos { it }
        var last = start
        var lastRocket = 0f
        var dioLit = 0
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 16_666_667f).coerceIn(0.2f, 3f) // in 60 fps frames
            last = now
            val t = (now - start) / 1_000_000_000f
            val px = w / 360f // scale tuned for ~360 dp wide screens

            // Diyas light one by one, with a light tick each.
            val litNow = ((t - 0.6f) / 0.22f).toInt().coerceIn(0, 7)
            if (t > 0.6f && litNow > dioLit) { dioLit = litNow; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

            if (t > 0.8f) for (l in lanterns) {
                l.y -= l.speed * px * 1.2f * dt
                l.x += sin(t + l.phase) * 0.4f * dt
                if (l.y < -60f) { l.y = h + 40f; l.x = rnd.nextFloat() * w }
            }
            for (g in glitter) { g.y += g.v * px * dt; if (g.y > h) { g.y = -5f; g.x = rnd.nextFloat() * w } }

            val gap = if (t < 4f) 0.45f else 0.9f
            if (t > 1.4f && t - lastRocket > gap) {
                lastRocket = t
                rockets += Rocket(w * (0.15f + rnd.nextFloat() * 0.7f), h * 0.62f, -(9f + rnd.nextFloat() * 3f) * px, h * (0.1f + rnd.nextFloat() * 0.22f), Sparks[rnd.nextInt(Sparks.size)])
            }
            val itR = rockets.iterator()
            while (itR.hasNext()) {
                val r = itR.next()
                r.y += r.vy * dt
                if (r.y <= r.targetY) {
                    itR.remove()
                    val second = Sparks[rnd.nextInt(Sparks.size)]
                    for (i in 0 until 60) {
                        val a = 2 * PI * i / 60
                        val s = (3f + rnd.nextFloat() * 4.5f) * px
                        sparks += Spark(r.x, r.y, (cos(a) * s).toFloat(), (sin(a) * s).toFloat(), 90f + rnd.nextFloat() * 30f, if (i % 3 == 0) second else r.color, rnd.nextFloat() < 0.3f)
                    }
                }
            }
            val itS = sparks.iterator()
            while (itS.hasNext()) {
                val p = itS.next()
                p.x += p.vx * dt; p.y += p.vy * dt
                p.vy += 0.06f * px * dt
                val drag = 0.975f.pow(dt)
                p.vx *= drag; p.vy *= drag
                p.life -= dt
                if (p.life <= 0f) itS.remove()
            }
            time = t
        }
    }

    val t = time
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07040C))
            .onSizeChanged { canvasSize = it.toSize() }
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val px = w / 360f

            drawRect(Brush.verticalGradient(0f to Color(0xFF07040C), 0.45f to Color(0xFF1C0B26), 0.8f to Color(0xFF4A1422), 1f to Color(0xFF6B1D1A)))
            drawRect(Brush.radialGradient(listOf(Color(0xFFFF8C1E).copy(alpha = 0.35f * prog(t, 0f, 2f)), Color.Transparent), center = Offset(w / 2, h * 0.92f), radius = w * 0.9f))

            for (s in stars) drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * abs(sin(t * 1.5f + s.phase))), s.r * px, Offset(s.x, s.y))

            if (t > 0.8f) for (l in lanterns) {
                val z = l.size * px * 10f
                drawCircle(Brush.radialGradient(listOf(Color(0x73FFAA3C), Color.Transparent), center = Offset(l.x, l.y), radius = z * 3f), z * 3f, Offset(l.x, l.y))
                val body = Path().apply {
                    moveTo(l.x - z * 0.8f, l.y - z); lineTo(l.x + z * 0.8f, l.y - z)
                    lineTo(l.x + z * 0.6f, l.y + z); lineTo(l.x - z * 0.6f, l.y + z); close()
                }
                drawPath(body, Color(0xFFFF9E3D))
                drawRect(Color(0xFFFFE08A), Offset(l.x - z * 0.3f, l.y + z * 0.2f), Size(z * 0.6f, z * 0.6f))
            }

            for (r in rockets) {
                drawRect(Color(0x80FFDC96), Offset(r.x - px, r.y), Size(2 * px, 26 * px))
                drawCircle(r.color, 3 * px, Offset(r.x, r.y))
            }
            for (p in sparks) {
                val a = (p.life / 60f).coerceAtMost(1f) * (if (p.twinkle) 0.5f + 0.5f * sin(p.life) else 1f)
                drawCircle(p.color.copy(alpha = a.coerceIn(0f, 1f)), 2.6f * px, Offset(p.x, p.y), blendMode = BlendMode.Plus)
            }
            if (t > 3f) for (g in glitter) drawRect(Color(0xFFFFE7A3).copy(alpha = 0.7f * abs(sin(t * 3 + g.phase))), Offset(g.x, g.y), Size(2 * px, 2 * px))

            rangoli(Offset(w / 2, h), w * 0.42f, prog(t, 0.3f, 1.8f), t * 0.08f)
            for (i in 0 until 7) {
                val lit = prog(t, 0.6f + i * 0.22f, 0.33f)
                val x = w * (i + 0.5f) / 7
                val y = h * 0.86f + abs(i - 3) * 2f * px
                diya(Offset(x, y), px * 0.95f, lit, t)
            }
            toran(t, prog(t, 0f, 0.6f))
        }

        val titleIn = prog(t, 2.2f, 0.7f)
        val pop = if (titleIn < 1f) 0.7f + 0.3f * (1f - (1f - titleIn).pow(3)) * (1f + 0.15f * sin(titleIn * PI.toFloat())) else 1f
        val shine = ((t - 3.3f) / 3f).let { it - floor(it) }
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            Text("AMPLI", color = Peach.copy(alpha = prog(t, 3.6f, 1f)), fontSize = 11.sp, letterSpacing = 5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "शुभ दीपावली",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFB8741A), Color(0xFFFFE7A3), Color(0xFFFFC857), Color(0xFFFFF6D8), Color(0xFFFFC857), Color(0xFFB8741A)),
                        start = Offset(-600f + 1600f * shine, 0f), end = Offset(200f + 1600f * shine, 0f),
                    ),
                    shadow = Shadow(Color(0x99FF9F1C), blurRadius = 28f),
                    fontSize = 54.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                ),
                modifier = Modifier.graphicsLayer { alpha = titleIn; scaleX = pop; scaleY = pop },
            )
            Spacer(Modifier.height(8.dp))
            FadeUp(prog(t, 2.9f, 1f)) { Text("HAPPY DIWALI", color = Peach, fontSize = 14.sp, letterSpacing = 6.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(14.dp))
            FadeUp(prog(t, 3.3f, 1f)) {
                Text(
                    stringResource(R.string.diwali_msg), color = Cream, fontSize = 15.sp, lineHeight = 24.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            FadeUp(prog(t, 3.6f, 1f)) { Text(stringResource(R.string.diwali_sig), color = Color(0xFFE0B98A), fontSize = 13.sp) }
            Spacer(Modifier.height(22.dp))
            val btnIn = prog(t, 4f, 0.8f)
            val pulse = if (t > 4.8f) ((t - 4.8f) / 2f).let { it - floor(it) } else 0f
            FadeUp(btnIn) {
                Box(contentAlignment = Alignment.Center) {
                    if (pulse > 0f) Box(
                        Modifier
                            .graphicsLayer { scaleX = 1f + pulse * 0.25f; scaleY = 1f + pulse * 0.5f; alpha = (1f - pulse) * 0.6f }
                            .clip(RoundedCornerShape(50)).background(Gold)
                            .padding(horizontal = 30.dp, vertical = 14.dp),
                    ) { Text(stringResource(R.string.diwali_cta), color = Color.Transparent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.verticalGradient(listOf(Color(0xFFFFD36E), Color(0xFFF08A24))))
                            .clickable(enabled = btnIn > 0.5f, onClick = onDone)
                            .padding(horizontal = 30.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.diwali_cta), color = Color(0xFF3A1405), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun FadeUp(p: Float, content: @Composable () -> Unit) {
    Box(Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 30f }) { content() }
}

private fun DrawScope.diya(at: Offset, s: Float, lit: Float, t: Float) {
    translate(at.x, at.y) {
        if (lit > 0f) {
            val c = Offset(0f, -14 * s)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB428).copy(alpha = 0.55f * lit), Color.Transparent), center = c, radius = 60 * s), 60 * s, c)
            val f = (1f + sin(t * 12 + at.x) * 0.08f) * lit
            val top = -6 * s - 32 * s * f
            val flame = Path().apply {
                moveTo(0f, top)
                cubicTo(10 * s, -6 * s + (top + 6 * s) * 0.5f, 8 * s, -6 * s, 0f, -6 * s)
                cubicTo(-8 * s, -6 * s, -10 * s, -6 * s + (top + 6 * s) * 0.5f, 0f, top)
                close()
            }
            drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFF6A00), Color(0xFFFFC23A), Color(0xFFFFF7C2)), startY = top, endY = -6 * s))
        }
        val cup = Path().apply {
            moveTo(-24 * s, -6 * s)
            quadraticTo(0f, 22 * s, 24 * s, -6 * s)
            lineTo(30 * s, -10 * s)
            quadraticTo(0f, -2 * s, -30 * s, -10 * s)
            close()
        }
        drawPath(cup, Brush.verticalGradient(listOf(Color(0xFFE8743A), Color(0xFF7A2410)), startY = -10 * s, endY = 14 * s))
        for (i in -2..2) drawCircle(Color(0xFFFFD36E), 1.6f * s, Offset(i * 8 * s, 2 * s))
    }
}

private fun DrawScope.rangoli(center: Offset, r0: Float, p: Float, rot: Float) {
    if (p <= 0f) return
    val alpha = (p * 1.3f).coerceAtMost(1f)
    translate(center.x, center.y) {
        rotate(rot * 57.2958f, pivot = Offset.Zero) {
            val rings = listOf(Triple(16, Color(0xFFFF3D7F), r0), Triple(12, Color(0xFFFFC857), r0 * 0.78f), Triple(8, Color(0xFF2EC4B6), r0 * 0.56f), Triple(8, Color(0xFF9B5DE5), r0 * 0.36f))
            rings.forEachIndexed { k, (n, col, r) ->
                val shown = minOf(n, (n * p * 1.4f).toInt())
                for (i in 0 until shown) {
                    rotate(360f * i / n + k * 11.5f, pivot = Offset.Zero) {
                        drawOval(col.copy(alpha = 0.92f * alpha), Offset(-r * 0.16f, -r * 0.55f - r * 0.45f), Size(r * 0.32f, r * 0.9f))
                    }
                }
            }
            drawCircle(Color(0xFFFFF2C2).copy(alpha = alpha), r0 * 0.14f, Offset.Zero)
            for (i in 0 until 24) {
                val a = 2 * PI * i / 24
                drawCircle(Color(0xFFFFE7A3).copy(alpha = alpha), r0 * 0.035f, Offset((cos(a) * r0 * 1.05f).toFloat(), (sin(a) * r0 * 1.05f).toFloat()))
            }
        }
    }
}

private fun DrawScope.toran(t: Float, a: Float) {
    if (a <= 0f) return
    val w = size.width
    val px = w / 360f
    val n = 15
    val rope = Path()
    for (i in 0..40) {
        val x = w * i / 40
        val y = (18 + sin(i / 40f * PI.toFloat()) * 34 + sin(t * 2 + i * 0.3f) * 3) * px
        if (i == 0) rope.moveTo(x, y) else rope.lineTo(x, y)
    }
    drawPath(rope, Color(0xFF5C8A2E).copy(alpha = a), style = Stroke(3 * px))
    for (i in 0 until n) {
        val u = (i + 0.5f) / n
        val x = w * u
        val y = (18 + sin(u * PI.toFloat()) * 34) * px
        val sway = sin(t * 2.2f + i) * 0.12f * 57.2958f
        translate(x, y) {
            rotate(sway, pivot = Offset.Zero) {
                drawOval((if (i % 2 == 1) Color(0xFF3E7D2A) else Color(0xFF5AA33A)).copy(alpha = a), Offset(-8 * px, 4 * px), Size(16 * px, 44 * px))
                val marigold = if (i % 3 == 0) Color(0xFFFF8C1A) else Color(0xFFFFB000)
                for (k in 0 until 3) {
                    val c = Offset(0f, (50 + k * 15) * px)
                    drawCircle((if (k % 2 == 1) marigold else Color(0xFFFF6A00)).copy(alpha = a), 9 * px, c)
                    drawCircle(Color(0xFFFFD36E).copy(alpha = a), 4 * px, c)
                }
            }
        }
    }
}
