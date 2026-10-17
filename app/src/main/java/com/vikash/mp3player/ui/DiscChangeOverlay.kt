package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val DURATION_MS = 5200

/**
 * Classic CD change, about 5 seconds: the song's cover turns into a disc, a robot arm picks it up,
 * drops it on the player, the tonearm lowers and the disc spins up. The song is already playing;
 * this only plays on top. Tap anywhere or press Back to skip.
 */
@Composable
fun DiscChangeOverlay(song: Song, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
        delay(250)
        done()
    }
    BackHandler { done() }

    val context = LocalContext.current
    val art = rememberAsyncImagePainter(
        remember(song.artworkUri) { ImageRequest.Builder(context).data(song.artworkUri).size(256).build() },
    )
    val artState by art.state.collectAsState()
    val artPainter: Painter? = art.takeIf { song.artworkUri != null && artState is AsyncImagePainter.State.Success }
    val logo = painterResource(R.drawable.ic_launcher_foreground)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black, Ampli.Night, Color(0xFF2A0F1C))))
            .clickable(remember { MutableInteractionSource() }, indication = null) { done() },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawScene(progress.value, artPainter, logo) }

        val t = progress.value
        val textAlpha = ease(seg(t, 0.80f, 0.92f))
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.alpha(textAlpha), horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun ease(x: Float) = FastOutSlowInEasing.transform(x)
private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun lerp(a: Offset, b: Offset, f: Float) = Offset(lerp(a.x, b.x, f), lerp(a.y, b.y, f))

private val Steel = Color(0xFFB8BCC8)
private val SteelDark = Color(0xFF6B7080)

private fun DrawScope.drawScene(t: Float, art: Painter?, logo: Painter) {
    val w = size.width
    val h = size.height
    val r = min(w * 0.19f, h * 0.13f)
    val p1 = Offset(w * 0.28f, h * 0.36f) // pick-up spot
    val p2 = Offset(w * 0.58f, h * 0.62f) // platter
    val start = Offset(w * 0.5f, h * 0.95f)
    val base = Offset(w * 0.86f, h * 0.10f)
    val rest = Offset(w * 0.70f, h * 0.26f)
    val reach = hypot(base.x - p2.x, base.y - p2.y)
    val l1 = reach * 0.56f
    val l2 = reach * 0.56f

    // Timeline.
    val deckIn = ease(seg(t, 0f, 0.14f))
    val appear = ease(seg(t, 0f, 0.16f))
    val morph = ease(seg(t, 0.10f, 0.22f))
    val armIn = ease(seg(t, 0.12f, 0.30f))
    val grip = ease(seg(t, 0.30f, 0.36f))
    val carry = ease(seg(t, 0.38f, 0.62f))
    val release = ease(seg(t, 0.62f, 0.67f))
    val armOut = ease(seg(t, 0.67f, 0.82f))
    val tone = ease(seg(t, 0.70f, 0.80f))
    val spinT = seg(t, 0.76f, 1f)
    val spin = 900f * spinT * spinT

    // CD player deck slides up.
    val deckShift = (1f - deckIn) * h * 0.3f
    translate(top = deckShift) {
        drawDeck(p2, r, spinning = spinT > 0f, alpha = deckIn)
    }

    // Disc position and lift.
    val discPos = when {
        t < 0.38f -> lerp(start, p1, appear)
        else -> lerp(p1, p2, carry)
    }
    val lift = if (t in 0.38f..0.62f) sin(PI.toFloat() * carry) else 0f
    val discScale = lerp(0.6f, 1f, appear) * (1f + 0.10f * lift)
    val dr = r * discScale

    // Shadow under the disc grows and shifts while it is carried.
    drawCircle(
        Color.Black.copy(alpha = 0.35f * appear),
        radius = dr * (1f + 0.06f * lift),
        center = discPos + Offset(r * (0.08f + 0.22f * lift), r * (0.10f + 0.30f * lift)),
    )
    // The cover square rounds into a disc.
    if (morph < 1f) drawCover(discPos, dr, morph, art, logo, alpha = appear * (1f - morph))
    if (morph > 0f) drawDisc(discPos, dr, spin, art, logo, alpha = morph)

    // Tonearm lowers onto the disc.
    drawTonearm(p2, r, tone, alpha = deckIn, shift = deckShift)

    // Robot arm: rest -> pick-up -> carry -> platter -> rest.
    val target = when {
        t < 0.30f -> lerp(rest, p1, armIn)
        t < 0.38f -> p1
        t < 0.67f -> discPos
        else -> lerp(p2, rest, armOut)
    }
    val fingerR = when {
        t < 0.30f -> 1.3f * r
        t < 0.62f -> lerp(1.3f * r, 1.02f * r, grip) * (1f + 0.10f * lift)
        else -> lerp(1.02f * r, 1.3f * r, release)
    }
    val armAlpha = ease(seg(t, 0.04f, 0.14f)) * (1f - ease(seg(t, 0.86f, 0.96f)))
    drawRobotArm(base, target, l1, l2, r, fingerR, armAlpha)
}

private fun DrawScope.drawDeck(c: Offset, r: Float, spinning: Boolean, alpha: Float) {
    val deckCenter = c + Offset(r * 0.35f, r * 0.05f)
    val dw = r * 3.1f
    val dh = r * 2.8f
    val tl = deckCenter - Offset(dw / 2f, dh / 2f)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF2C2733), Color(0xFF16131B)), startY = tl.y, endY = tl.y + dh),
        topLeft = tl, size = Size(dw, dh), cornerRadius = CornerRadius(r * 0.22f), alpha = alpha,
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.08f), topLeft = tl, size = Size(dw, dh),
        cornerRadius = CornerRadius(r * 0.22f), style = Stroke(width = 2f), alpha = alpha,
    )
    // Platter.
    drawCircle(Color(0xFF0B0A0E), radius = r * 1.14f, center = c, alpha = alpha)
    drawCircle(Color.White.copy(alpha = 0.06f), radius = r * 1.14f, center = c, style = Stroke(3f), alpha = alpha)
    drawCircle(SteelDark, radius = r * 0.07f, center = c, alpha = alpha)
    // Power LED.
    val led = tl + Offset(dw - r * 0.28f, dh - r * 0.28f)
    val ledColor = if (spinning) Color(0xFF22C55E) else Ampli.Rose
    drawCircle(ledColor.copy(alpha = 0.35f), radius = r * 0.12f, center = led, alpha = alpha)
    drawCircle(ledColor, radius = r * 0.055f, center = led, alpha = alpha)
}

private fun DrawScope.circlePath(c: Offset, radius: Float) =
    Path().apply { addOval(Rect(c, radius)) }

private fun DrawScope.drawPainterIn(painter: Painter, center: Offset, side: Float, alpha: Float) {
    translate(center.x - side / 2f, center.y - side / 2f) {
        with(painter) { draw(Size(side, side), alpha = alpha) }
    }
}

private fun DrawScope.drawCover(c: Offset, r: Float, morph: Float, art: Painter?, logo: Painter, alpha: Float) {
    val side = r * 1.8f
    val corner = lerp(r * 0.12f, side / 2f, morph)
    val rect = Rect(c, side / 2f)
    val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner))) }
    clipPath(path) {
        drawRect(Ampli.Gradient, topLeft = rect.topLeft, size = rect.size, alpha = alpha)
        if (art != null) drawPainterIn(art, c, side, alpha) else drawPainterIn(logo, c, side, alpha)
    }
}

private fun DrawScope.drawDisc(c: Offset, r: Float, spin: Float, art: Painter?, logo: Painter, alpha: Float) {
    // Iridescent body; the sheen stays still while the label turns, like light on a real CD.
    drawCircle(
        Brush.sweepGradient(
            listOf(
                Color(0xFFD9DCE6), Color(0xFFFBCFE8), Color(0xFFA5F3FC), Color(0xFFE9D5FF),
                Color(0xFFFDE68A), Color(0xFFD9DCE6), Color(0xFFBAE6FD), Color(0xFFD9DCE6),
            ),
            center = c,
        ),
        radius = r, center = c, alpha = alpha,
    )
    for (i in 1..6) {
        drawCircle(
            Color.Black.copy(alpha = 0.06f), radius = r * (0.56f + i * 0.07f), center = c,
            style = Stroke(width = 1.2f), alpha = alpha,
        )
    }
    // Spinning label with the cover art.
    rotate(spin, pivot = c) {
        clipPath(circlePath(c, r * 0.5f)) {
            drawCircle(Ampli.Gradient, radius = r * 0.5f, center = c, alpha = alpha)
            if (art != null) drawPainterIn(art, c, r, alpha) else drawPainterIn(logo, c, r, alpha)
        }
        // A small mark so the spin is easy to see even on plain art.
        drawLine(
            Color.White.copy(alpha = 0.55f * alpha),
            start = c + Offset(0f, -r * 0.62f), end = c + Offset(0f, -r * 0.92f),
            strokeWidth = r * 0.025f, cap = StrokeCap.Round,
        )
    }
    // Fixed shine streaks.
    drawArc(
        Color.White.copy(alpha = 0.35f * alpha), startAngle = 200f, sweepAngle = 40f, useCenter = false,
        topLeft = c - Offset(r * 0.82f, r * 0.82f), size = Size(r * 1.64f, r * 1.64f), style = Stroke(r * 0.06f, cap = StrokeCap.Round),
    )
    drawArc(
        Color.White.copy(alpha = 0.22f * alpha), startAngle = 20f, sweepAngle = 30f, useCenter = false,
        topLeft = c - Offset(r * 0.74f, r * 0.74f), size = Size(r * 1.48f, r * 1.48f), style = Stroke(r * 0.04f, cap = StrokeCap.Round),
    )
    // Centre hole.
    drawCircle(Color.White.copy(alpha = 0.7f), radius = r * 0.16f, center = c, style = Stroke(r * 0.03f), alpha = alpha)
    drawCircle(Color(0xFF0B0A0E), radius = r * 0.11f, center = c, alpha = alpha)
    drawCircle(Color.White.copy(alpha = 0.25f), radius = r, center = c, style = Stroke(2f), alpha = alpha)
}

private fun DrawScope.drawTonearm(c: Offset, r: Float, lower: Float, alpha: Float, shift: Float) {
    val pivot = c + Offset(r * 1.18f, -r * 1.08f + shift)
    val len = r * 1.38f
    val angle = Math.toRadians(lerp(92f, 112f, lower).toDouble()).toFloat()
    val tip = pivot + Offset(cos(angle) * len, sin(angle) * len)
    // The head casts a shadow that tightens as it touches down.
    val hover = (1f - ease(seg(lower, 0.75f, 1f))) * r * 0.12f
    drawLine(Color.Black.copy(alpha = 0.35f * alpha), pivot + Offset(hover, hover), tip + Offset(hover * 2f, hover * 2f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
    drawCircle(SteelDark, radius = r * 0.17f, center = pivot, alpha = alpha)
    drawCircle(Steel, radius = r * 0.10f, center = pivot, alpha = alpha)
    drawLine(Steel.copy(alpha = alpha), pivot, tip, strokeWidth = r * 0.06f, cap = StrokeCap.Round)
    drawLine(Ampli.Rose.copy(alpha = alpha), tip, tip + Offset(cos(angle) * r * 0.12f, sin(angle) * r * 0.12f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
}

private fun DrawScope.drawRobotArm(base: Offset, target: Offset, l1: Float, l2: Float, r: Float, fingerR: Float, alpha: Float) {
    if (alpha <= 0f) return
    val dx = target.x - base.x
    val dy = target.y - base.y
    val d = hypot(dx, dy).coerceIn(kotlin.math.abs(l1 - l2) + 1f, l1 + l2 - 1f)
    val theta = atan2(dy, dx)
    val cosA = ((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f)
    val shoulder = theta + acos(cosA)
    val elbow = base + Offset(cos(shoulder) * l1, sin(shoulder) * l1)
    val shadow = Offset(r * 0.18f, r * 0.24f)
    val linkW = r * 0.22f

    // Shadow first, then the steel links.
    drawLine(Color.Black.copy(alpha = 0.3f * alpha), base + shadow, elbow + shadow, strokeWidth = linkW, cap = StrokeCap.Round)
    drawLine(Color.Black.copy(alpha = 0.3f * alpha), elbow + shadow, target + shadow, strokeWidth = linkW * 0.8f, cap = StrokeCap.Round)
    drawLine(
        Brush.linearGradient(listOf(Steel, SteelDark), base, elbow), base, elbow,
        strokeWidth = linkW, cap = StrokeCap.Round, alpha = alpha,
    )
    drawLine(
        Brush.linearGradient(listOf(Steel, SteelDark), elbow, target), elbow, target,
        strokeWidth = linkW * 0.8f, cap = StrokeCap.Round, alpha = alpha,
    )
    // Joints.
    drawCircle(Color(0xFF2C2733), radius = r * 0.30f, center = base, alpha = alpha)
    drawCircle(Steel, radius = r * 0.18f, center = base, alpha = alpha)
    drawCircle(Color(0xFF2C2733), radius = r * 0.20f, center = elbow, alpha = alpha)
    drawCircle(Ampli.Rose, radius = r * 0.09f, center = elbow, alpha = alpha)

    // Gripper: three fingers close on the disc edge.
    val dir = atan2(target.y - elbow.y, target.x - elbow.x)
    for (k in 0..2) {
        val a = dir + (k * 2f * PI.toFloat() / 3f) + PI.toFloat() / 3f
        val tipIn = target + Offset(cos(a) * fingerR, sin(a) * fingerR)
        val tipOut = target + Offset(cos(a) * (fingerR + r * 0.16f), sin(a) * (fingerR + r * 0.16f))
        drawLine(SteelDark.copy(alpha = 0.9f * alpha), target, tipIn, strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        drawLine(Steel.copy(alpha = alpha), tipIn, tipOut, strokeWidth = r * 0.12f, cap = StrokeCap.Round)
    }
    drawCircle(Ampli.RoseDeep, radius = r * 0.17f, center = target, alpha = alpha)
    drawCircle(Ampli.Rose, radius = r * 0.10f, center = target, alpha = alpha)
}
