package com.vikash.mp3player.ui

import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.vikash.mp3player.ui.skin.LocalSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Motion Pack (picked by Vikash on 12 Nov 2026 from https://claude.ai/artifact/PKonAUPUJ99qrwJGRMU8HG):
 * swipe gestures, cover colours, haptics, the heart burst and the loading shimmer.
 */

/** Light taps for play, pause, seek and the heart; off when the skin's haptics are switched off. */
class Haptics internal constructor(private val view: android.view.View, private val on: Boolean) {
    fun tick() { if (on) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun confirm() {
        if (!on) return
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val on = LocalSkin.current.hapticsOn
    return remember(view, on) { Haptics(view, on) }
}

/**
 * Swipe sideways for the next or previous song (the card slides out and the new one slides in
 * from the other side), swipe up to open the player. A plain tap still reaches the card's own click.
 */
fun Modifier.swipeToSkip(onNext: () -> Unit, onPrevious: () -> Unit, onUp: (() -> Unit)? = null): Modifier = composed {
    val x = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val lite = LocalLiteMode.current
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val up by rememberUpdatedState(onUp)
    var m = this
        .graphicsLayer {
            translationX = x.value
            alpha = 1f - (abs(x.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.6f)
        }
        .pointerInput(Unit) {
            val width = size.width.toFloat()
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (abs(x.value) > width * 0.28f) {
                            val dir = if (x.value < 0) -1f else 1f
                            haptics.tick()
                            if (lite) x.snapTo(0f) else {
                                x.animateTo(dir * width, tween(160))
                                x.snapTo(-dir * width * 0.6f)
                            }
                            if (dir < 0) next() else previous()
                            x.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                        } else x.animateTo(0f, spring())
                    }
                },
                onDragCancel = { scope.launch { x.animateTo(0f, spring()) } },
            ) { change, drag ->
                change.consume()
                scope.launch { x.snapTo(x.value + drag) }
            }
        }
    if (onUp != null) {
        m = m.pointerInput(Unit) {
            var dy = 0f
            detectVerticalDragGestures(
                onDragStart = { dy = 0f },
                onDragEnd = { if (dy < -48.dp.toPx()) up?.invoke() },
            ) { change, drag -> change.consume(); dy += drag }
        }
    }
    m
}

/**
 * Pull down to close: the screen follows the finger, shrinking and rounding a little, and closes
 * past a quarter of the height (or a quick flick); otherwise it springs back.
 */
fun Modifier.dragDownToClose(enabled: Boolean, onClose: () -> Unit): Modifier = composed {
    if (!enabled) return@composed this
    val y = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(onClose)
    this
        .graphicsLayer {
            translationY = y.value
            val k = (y.value / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
            scaleX = 1f - k * 0.12f
            scaleY = 1f - k * 0.12f
            shape = androidx.compose.foundation.shape.RoundedCornerShape((k * 120).dp.coerceAtMost(28.dp))
            clip = k > 0f
        }
        .pointerInput(Unit) {
            val h = size.height.toFloat()
            detectVerticalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (y.value > h * 0.25f) close() else y.animateTo(0f, spring())
                    }
                },
                onDragCancel = { scope.launch { y.animateTo(0f, spring()) } },
            ) { change, drag ->
                if (y.value + drag > 0f) {
                    change.consume()
                    scope.launch { y.snapTo((y.value + drag).coerceAtLeast(0f)) }
                }
            }
        }
}

/** A soft colour from the cover (the vibrant or muted swatch), faded smoothly from song to song. */
@Composable
fun rememberCoverTint(uri: Uri?, fallback: Color): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(fallback) }
    LaunchedEffect(uri, fallback) {
        target = if (uri == null) fallback else withContext(Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context).data(uri).size(96).allowHardware(false).build()
                val bmp = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                    ?: return@runCatching fallback
                val p = Palette.from(bmp).maximumColorCount(12).generate()
                val rgb = p.vibrantSwatch?.rgb ?: p.mutedSwatch?.rgb ?: p.dominantSwatch?.rgb ?: return@runCatching fallback
                Color(rgb)
            }.getOrDefault(fallback)
        }
    }
    val tint by animateColorAsState(target, tween(if (LocalLiteMode.current) 0 else 900), label = "coverTint")
    return tint
}

/** A gentle wash of [tint] fading from the top, behind the player. */
fun Modifier.coverWash(tint: Color, strength: Float = 0.30f): Modifier = drawBehind {
    // tint.alpha scales it, so the transparent fallback (no cover) draws nothing.
    val k = tint.alpha * strength
    if (k <= 0.005f) return@drawBehind
    drawRect(Brush.verticalGradient(listOf(tint.copy(alpha = k), tint.copy(alpha = k * 0.35f), Color.Transparent)))
}

/** The favourite heart's pop: eight small dots fly out when [burst] changes. */
@Composable
fun HeartBurst(burst: Int, color: Color, modifier: Modifier = Modifier) {
    if (LocalLiteMode.current) return
    val t = remember { Animatable(1f) }
    LaunchedEffect(burst) {
        if (burst == 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    if (t.value >= 1f) return
    Box(modifier.drawBehind {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension * (0.25f + 0.5f * t.value)
        for (i in 0 until 8) {
            val a = i / 8f * 2f * Math.PI.toFloat()
            drawCircle(color.copy(alpha = 1f - t.value), radius = 3.dp.toPx() * (1f - t.value * 0.5f), center = c + Offset(cos(a) * r, sin(a) * r))
        }
    })
}

/** Shimmer for a placeholder block while the library loads. */
fun Modifier.shimmer(base: Color, shine: Color): Modifier = composed {
    if (LocalLiteMode.current) return@composed background(base)
    val p by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "shimmerX",
    )
    drawBehind {
        drawRect(base)
        drawRect(Brush.linearGradient(listOf(base, shine, base), start = Offset(size.width * p - size.width, 0f), end = Offset(size.width * p, 0f)))
    }
}
