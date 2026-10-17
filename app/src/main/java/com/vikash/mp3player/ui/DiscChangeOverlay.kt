package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
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
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val DURATION_MS = 5400

/**
 * A classic portable CD player, about 5 seconds: OPEN is pressed, the lid swings up, the song's
 * CD drops onto the spindle, the lid snaps shut, the glass display below shows the song and
 * the disc spins up under the smoked lid. The song is already playing; this only plays on top.
 * Tap anywhere or press Back to skip.
 */
@Composable
fun DiscChangeOverlay(song: Song, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
        delay(300)
        done()
    }
    BackHandler { done() }

    val art = rememberStickerPainter(song.artworkUri, 640)
    val logo = painterResource(R.drawable.ic_launcher_foreground)
    val text = rememberTextMeasurer()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF241A22), Color(0xFF08070B))))
            .clickable(remember { MutableInteractionSource() }, indication = null) { done() },
    ) {
        val status = DiscStatus(
            ready = stringResource(R.string.disc_status_ready),
            opening = stringResource(R.string.disc_status_opening),
            reading = stringResource(R.string.disc_status_reading),
            playing = stringResource(R.string.player_now_playing),
        )
        Canvas(Modifier.fillMaxSize()) { drawPlayer(progress.value, art, logo, text, song.title, status) }

        val t = progress.value
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.alpha(easeOut(seg(t, 0.84f, 0.96f))), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.player_now_playing).uppercase(),
                    color = Ampli.Rose, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                )
                Text(
                    song.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Text(
                    song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                stringResource(R.string.disc_tap_skip),
                color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** 0..1 progress of [t] inside the window [a]..[b]. */
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeIn(x: Float) = x * x * x
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOutBack(x: Float): Float {
    val c1 = 1.5f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}
private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

private val SilverHi = Color(0xFFF4F5F8)
private val Silver = Color(0xFFCDD1DA)
private val SilverLo = Color(0xFFA9AEB9)

private fun DrawScope.roundRect(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r))

private fun DrawScope.drawPlayer(
    t: Float, art: Painter?, logo: Painter, text: TextMeasurer, title: String, status: DiscStatus, live: LiveState? = null,
) {
    val w = size.width
    val h = size.height
    val rb = live?.rb?.invoke(w, h) ?: min(w * 0.42f, h * 0.25f)
    val rise = easeOut(seg(t, 0f, 0.12f))
    val shake = if (t > 0.70f && t < 0.74f) sin((t - 0.70f) * 400f) * (1f - seg(t, 0.70f, 0.74f)) * rb * 0.012f else 0f
    val cx = w / 2f
    val cy = (live?.cyFraction ?: 0.43f) * h + (1f - rise) * h * 0.25f + shake
    val c = Offset(cx, cy)
    val a = rise

    // Floor shadow.
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f * a), Color.Transparent), Offset(cx, cy + rb * 0.28f), rb * 1.35f),
        topLeft = Offset(cx - rb * 1.3f, cy + rb * 0.28f - rb * 1.25f), size = Size(rb * 2.6f, rb * 2.5f),
    )

    // Body: brushed silver with a darker edge.
    drawCircle(
        Brush.verticalGradient(listOf(Color(0xFF8F95A1), Color(0xFF5B606B)), cy - rb, cy + rb),
        radius = rb, center = Offset(cx, cy + rb * 0.04f), alpha = a,
    )
    drawCircle(
        Brush.radialGradient(0f to SilverHi, 0.6f to Silver, 1f to SilverLo, center = Offset(cx - rb * 0.3f, cy - rb * 0.4f), radius = rb * 1.3f),
        radius = rb * 0.985f, center = c, alpha = a,
    )
    for (k in 0 until 14) drawCircle(Color.White.copy(alpha = 0.18f * a), radius = rb * (0.9f + k * 0.006f), center = c, style = Stroke(1f))

    // Disc bay, laser sled and spindle.
    drawCircle(Brush.radialGradient(listOf(Color(0xFF2B2E36), Color(0xFF14161B)), c, rb * 0.86f), radius = rb * 0.86f, center = c, alpha = a)
    drawCircle(Color.Black.copy(alpha = 0.6f * a), radius = rb * 0.86f, center = c, style = Stroke(rb * 0.02f))
    rotate(140f, pivot = c) {
        roundRect(Color(0xFF0C0D10).copy(alpha = a), cx + rb * 0.2f, cy - rb * 0.09f, rb * 0.6f, rb * 0.18f, rb * 0.05f)
        drawRect(Color(0xFF3A3F4A).copy(alpha = a), Offset(cx + rb * 0.22f, cy - rb * 0.012f), Size(rb * 0.56f, rb * 0.024f))
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFC4B5FD), Color(0xFF4C1D95), Color(0xFF111111)), Offset(cx + rb * 0.47f, cy - rb * 0.01f), rb * 0.06f),
            radius = rb * 0.055f, center = Offset(cx + rb * 0.48f, cy), alpha = a,
        )
    }

    // Disc spin: speeds up over the last quarter.
    val s = seg(t, 0.74f, 1f)
    val spinSeconds = DURATION_MS / 1000f * 0.26f
    val spin = live?.angle ?: (400f * spinSeconds * s * s * s / 3f)

    drawCircle(Color(0xFF9AA0AB).copy(alpha = a), radius = rb * 0.13f, center = c)
    drawCircle(Color(0xFFE5E7EB).copy(alpha = a), radius = rb * 0.085f, center = c)
    for (k in 0 until 3) {
        val an = k * 2.094f + spin * PI.toFloat() / 180f
        drawCircle(Color(0xFF6B7280).copy(alpha = a), radius = rb * 0.018f, center = Offset(cx + cos(an) * rb * 0.105f, cy + sin(an) * rb * 0.105f))
    }

    // Lid opens 0.14-0.30 with a little bounce, closes 0.56-0.70.
    val open = easeOutBack(seg(t, 0.14f, 0.30f)) * (1f - easeIn(seg(t, 0.56f, 0.70f)))
    val lidDeg = open * 112f
    val r = rb * 0.80f
    // The CD drops in 0.26-0.50 from close to the camera onto the spindle.
    val drop = easeInOut(seg(t, 0.26f, 0.50f))
    val settle = if (t > 0.50f && t < 0.56f) 1f - sin(seg(t, 0.50f, 0.56f) * PI.toFloat()) * 0.015f else 1f
    val hgt = 1f - drop
    val discY = lerp(cy - h * 0.62f, cy, drop)
    val scale = lerp(1.45f, 1f, drop) * settle
    val drawDisc = {
        if (t >= 0.26f) {
            if (hgt > 0.001f) {
                drawCircle(
                    Color.Black.copy(alpha = 0.55f * (1f - hgt * 0.7f) * a),
                    radius = r * (1f + hgt * 0.25f),
                    center = Offset(cx + rb * 0.04f + hgt * rb * 0.25f, cy + rb * 0.06f + hgt * rb * 0.35f),
                )
            }
            drawCd(Offset(cx, discY), r * scale, spin - hgt * 25f, art, logo, a)
        }
    }
    if (t >= 0.52f) drawDisc()
    drawLid(c, rb, lidDeg, a, text)
    if (t < 0.52f) drawDisc()

    drawFrontDisplay(cx, cy, rb, t, a, title, status, text, live)
}

/** Dark glass strip under the player: status, song title, live bars and a thin progress line. */
private fun DrawScope.drawFrontDisplay(
    cx: Float, cy: Float, rb: Float, t: Float, a: Float, title: String, status: DiscStatus, text: TextMeasurer, live: LiveState?,
) {
    val phase = live?.phase ?: (t * DURATION_MS / 1000f)
    val pw = rb * 1.56f
    val ph = rb * 0.40f
    val px = cx - pw / 2f
    val py = cy + rb * 0.80f
    val pill = CornerRadius(ph / 2f)
    drawRoundRect(Color.Black.copy(alpha = 0.45f * a), Offset(px, py + rb * 0.04f), Size(pw, ph), pill)
    drawRoundRect(Color(0xF0120F18).copy(alpha = 0.94f * a), Offset(px, py), Size(pw, ph), pill)
    drawRoundRect(
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.5f to Color.Transparent, startY = py, endY = py + ph),
        Offset(px, py), Size(pw, ph), pill, alpha = a,
    )
    drawRoundRect(Color.White.copy(alpha = 0.10f * a), Offset(px, py), Size(pw, ph), pill, style = Stroke(1f))

    val playing = live?.playing ?: (t >= 0.72f)
    // Badge: rose circle with a play mark that pulses, or a spinner while the disc loads.
    val bc = Offset(px + ph / 2f, py + ph / 2f)
    val br = ph * 0.32f
    val pulse = if (playing) 1f + 0.06f * sin(phase * 11f) else 1f
    drawCircle(
        Brush.linearGradient(listOf(Color(0xFFF43F5E), Ampli.RoseDeep), bc - Offset(br, br), bc + Offset(br, br)),
        radius = br * pulse, center = bc, alpha = a,
    )
    if (playing || t < 0.12f || live != null) {
        drawPath(
            Path().apply {
                moveTo(bc.x - br * 0.28f, bc.y - br * 0.4f); lineTo(bc.x + br * 0.42f, bc.y); lineTo(bc.x - br * 0.28f, bc.y + br * 0.4f); close()
            },
            Color.White.copy(alpha = a),
        )
    } else {
        val start = (phase * 7.4f * 180f / PI.toFloat()) % 360f
        drawArc(
            Color.White.copy(alpha = a), startAngle = start, sweepAngle = 240f, useCenter = false,
            topLeft = bc - Offset(br * 0.48f, br * 0.48f), size = Size(br * 0.96f, br * 0.96f),
            style = Stroke(br * 0.16f, cap = StrokeCap.Round),
        )
    }
    // Status and title.
    val tx = px + ph
    val maxW = (pw - ph - rb * 0.30f).toInt().coerceAtLeast(1)
    val label = when {
        live != null -> if (live.playing) status.playing else status.paused
        t < 0.12f -> status.ready
        t < 0.56f -> status.opening
        t < 0.72f -> status.reading
        else -> status.playing
    }
    val statusLayout = text.measure(
        label.uppercase(),
        TextStyle(color = Color(0xFFFB7185).copy(alpha = a), fontSize = (rb * 0.058f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = (rb * 0.012f).toSp()),
        maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = maxW),
    )
    val titleLayout = text.measure(
        title,
        TextStyle(color = Color.White.copy(alpha = a), fontSize = (rb * 0.088f).toSp(), fontWeight = FontWeight.SemiBold),
        maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = maxW),
    )
    val textTop = py + (ph - statusLayout.size.height - titleLayout.size.height) / 2f
    drawText(statusLayout, topLeft = Offset(tx, textTop))
    drawText(titleLayout, topLeft = Offset(tx, textTop + statusLayout.size.height))
    // Live bars on the right.
    for (k in 0 until 4) {
        val v = if (playing) 0.35f + 0.65f * kotlin.math.abs(sin(phase * (7f + k * 1.7f) + k)) else 0.18f
        val bh = ph * 0.42f * v
        val bx = px + pw - rb * 0.26f + k * rb * 0.05f
        drawRoundRect(
            (if (playing) Ampli.Rose else Color.White.copy(alpha = 0.25f)).copy(alpha = (if (playing) 1f else 0.25f) * a),
            Offset(bx, py + ph * 0.71f - bh), Size(rb * 0.028f, bh), CornerRadius(rb * 0.014f),
        )
    }
    // Thin progress line: fills while the disc is read, then shows the song starting.
    val lineW = pw - ph
    val fill = when {
        live != null -> live.progress.coerceIn(0f, 1f)
        t < 0.56f -> 0f
        t < 0.72f -> seg(t, 0.56f, 0.72f)
        else -> 0.02f + 0.03f * seg(t, 0.72f, 1f)
    }
    val ly = py + ph - rb * 0.035f
    drawRoundRect(Color.White.copy(alpha = 0.08f * a), Offset(px + ph / 2f, ly), Size(lineW, rb * 0.012f), CornerRadius(rb * 0.006f))
    if (fill > 0f) {
        drawRoundRect(
            Brush.horizontalGradient(listOf(Ampli.RoseDeep, Color(0xFFFB7185)), px, px + pw),
            Offset(px + ph / 2f, ly), Size(lineW * fill, rb * 0.012f), CornerRadius(rb * 0.006f), alpha = a,
        )
    }
}

/** Localised labels for the front display. */
private class DiscStatus(val ready: String, val opening: String, val reading: String, val playing: String, val paused: String = playing)

/** What the resting player shows on Now Playing. */
private class LiveState(
    val angle: Float,
    val playing: Boolean,
    val phase: Float,
    val progress: Float,
    val cyFraction: Float,
    val rb: (Float, Float) -> Float,
)

/**
 * Now Playing as the same CD player, lid closed: the song's CD turns under the smoked lid and
 * the glass display shows Now playing or Paused, the title, live bars and the song progress.
 */
@Composable
fun CdPlayerView(artworkUri: android.net.Uri?, title: String, isPlaying: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val angle = LocalDiscAngle.current
    val art = rememberStickerPainter(artworkUri, 640)
    val logo = painterResource(R.drawable.ic_launcher_foreground)
    val text = rememberTextMeasurer()
    val clock = rememberInfiniteTransition(label = "cdPlayer")
    val phase by clock.animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing)), label = "phase")
    val status = DiscStatus(
        ready = "", opening = "", reading = "",
        playing = stringResource(R.string.player_now_playing),
        paused = stringResource(R.string.disc_status_paused),
    )
    Canvas(modifier) {
        val glow = 0.10f + if (isPlaying) 0.18f else 0f
        drawCircle(
            Brush.radialGradient(listOf(Ampli.Rose.copy(alpha = glow), Color.Transparent), Offset(size.width / 2f, size.height * 0.44f), size.minDimension * 0.6f),
            radius = size.minDimension * 0.6f, center = Offset(size.width / 2f, size.height * 0.44f),
        )
        drawPlayer(
            1f, art, logo, text, title, status,
            LiveState(angle.value, isPlaying, phase, progress, 0.44f) { w, h -> min(w * 0.40f, h * 0.40f) },
        )
    }
}

/** The lid hinges at the top: squashing it around the hinge gives the swing; past 90 degrees we see its underside. */
private fun DrawScope.drawLid(c: Offset, rb: Float, deg: Float, a: Float, text: TextMeasurer) {
    val r = rb * 0.9f
    val hinge = Offset(c.x, c.y - r)
    val k = cos(deg * PI.toFloat() / 180f).let { if (it == 0f) 0.001f else it }
    val under = k < 0f
    withTransform({ scale(1f, k, pivot = hinge) }) {
        drawCircle((if (under) Color(0xEB1E2128) else Color(0x801C2028)).copy(alpha = (if (under) 0.92f else 0.5f) * a), radius = r, center = c)
        drawCircle((if (under) Color(0xFF7A808C) else Color(0xFFE2E5EB)).copy(alpha = a), radius = r - rb * 0.022f, center = c, style = Stroke(rb * 0.045f))
        drawCircle(Color.Black.copy(alpha = 0.35f * a), radius = r - rb * 0.05f, center = c, style = Stroke(1f))
        if (!under) {
            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r - rb * 0.05f)) }) {
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.28f), 0.45f to Color.White.copy(alpha = 0.04f),
                        0.5f to Color.White.copy(alpha = 0.16f), 0.56f to Color.Transparent,
                        start = Offset(c.x - r, c.y - r), end = Offset(c.x + r * 0.2f, c.y + r * 0.3f),
                    ),
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2f, r * 2f), alpha = a,
                )
            }
            val name = text.measure("AMPLI", TextStyle(color = Color.White.copy(alpha = 0.55f * a), fontSize = (rb * 0.075f).toSp(), fontWeight = FontWeight.Bold))
            drawText(name, topLeft = Offset(c.x - name.size.width / 2f, c.y + r * 0.62f - name.size.height / 2f))
            val sub = text.measure("COMPACT DISC  ·  ESP 40 SEC", TextStyle(color = Color.White.copy(alpha = 0.55f * a), fontSize = (rb * 0.045f).toSp()))
            drawText(sub, topLeft = Offset(c.x - sub.size.width / 2f, c.y + r * 0.74f - sub.size.height / 2f))
        }
    }
    drawRoundRect(Color(0xFF7D838F).copy(alpha = a), Offset(c.x - rb * 0.28f, hinge.y - rb * 0.035f), Size(rb * 0.56f, rb * 0.07f), CornerRadius(rb * 0.03f))
}
