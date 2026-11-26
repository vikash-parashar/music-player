package com.vikash.mp3player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.ui.skin.dropShadow
import kotlinx.coroutines.launch

/** A bottom tab. [selectedIcon] (usually the filled version) is shown while the tab is active. */
data class NavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

private val Tape = Color(0xFF1C1016)
private val Tape2 = Color(0xFF2A1820)
private val Gold = Color(0xFFC9A46A)
private val TabInk = Color(0xFFD9B3C4)
private val Rose = Color(0xFFE8487A)
private val Orange = Color(0xFFF0682A)

/**
 * The cassette dock (owner's pick, 2026-11-27: "racing drift"): a dark tape-coloured bar with a
 * thin gold edge. The open tab sits on a spinning rose-to-orange reel. Tapping another tab sends
 * the reel racing there: it overshoots a little and drifts back, spins fast (backwards when it
 * goes left, like rewind) and leaves a fading tyre mark under the bar. Same in every theme.
 * Lite mode keeps the reel still and skips the tyre mark.
 */
@Composable
fun CompactNavBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit) {
    val lite = LocalLiteMode.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(58.dp)
                .dropShadow(shape, Tape.copy(alpha = 0.45f), offsetY = 8.dp, blur = 16.dp)
                .background(Brush.verticalGradient(listOf(Tape2, Tape)), shape)
                .border(1.dp, Gold.copy(alpha = 0.55f), shape)
                .padding(horizontal = 7.dp),
        ) {
            val count = tabs.size.coerceAtLeast(1)
            val slotPx = with(density) { maxWidth.toPx() } / count
            val reel = 40.dp
            val reelPx = with(density) { reel.toPx() }
            fun centre(i: Int) = slotPx * i + slotPx / 2

            val x = remember { Animatable(centre(selected)) }
            var spinBoost by remember { mutableFloatStateOf(0f) } // extra turns/sec, signed
            val skid = remember { Animatable(0f) }
            var skidFrom by remember { mutableFloatStateOf(0f) }
            var skidTo by remember { mutableFloatStateOf(0f) }
            var last by remember { mutableIntStateOf(selected) }

            LaunchedEffect(selected, slotPx) {
                val from = x.value
                val to = centre(selected)
                if (last == selected || lite) { x.snapTo(to); last = selected; return@LaunchedEffect }
                last = selected
                val dir = if (to > from) 1f else -1f
                val over = to + dir * with(density) { 14.dp.toPx() }
                skidFrom = minOf(from, over); skidTo = maxOf(from, over)
                launch { skid.snapTo(0.55f); skid.animateTo(0f, tween(1000, easing = LinearEasing)) }
                spinBoost = 3f * dir
                x.animateTo(to, keyframes {
                    durationMillis = 620
                    over at 434 using FastOutSlowInEasing
                })
                spinBoost = 0f
            }

            // Reel spin: one turn every 3 s, much faster (and reversed going left) while racing.
            var angle by remember { mutableFloatStateOf(0f) }
            if (!lite) LaunchedEffect(Unit) {
                var prev = 0L
                while (true) withFrameNanos { t ->
                    if (prev != 0L) {
                        val dt = (t - prev) / 1_000_000_000f
                        angle = (angle + 360f * dt * (1f / 3f + spinBoost)) % 360f
                    }
                    prev = t
                }
            }

            // Tyre mark under the reel's path.
            if (skid.value > 0f) Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2 + 15.dp.toPx()
                drawLine(
                    Color.Black.copy(alpha = skid.value), Offset(skidFrom, y), Offset(skidTo, y),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())),
                )
            }

            // The reel.
            Canvas(
                Modifier
                    .offset(x = with(density) { (x.value - reelPx / 2).toDp() })
                    .align(Alignment.CenterStart)
                    .size(reel)
                    .rotate(angle),
            ) {
                val r = size.minDimension / 2
                drawCircle(Brush.sweepGradient(listOf(Rose, Orange, Rose)))
                drawCircle(Gold, r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                // Hub spokes, like a cassette reel.
                val hub = 9.dp.toPx()
                for (k in 0 until 6) drawArc(
                    Tape, startAngle = k * 60f, sweepAngle = 20f, useCenter = true,
                    topLeft = Offset(center.x - hub, center.y - hub),
                    size = androidx.compose.ui.geometry.Size(hub * 2, hub * 2),
                )
            }

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, tab ->
                    val on = i == selected
                    val tint by animateColorAsState(if (on) Color.White else TabInk, label = "tint")
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .semantics { contentDescription = tab.label; this.selected = on }
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (on) tab.selectedIcon else tab.icon, null, tint = tint,
                            modifier = Modifier.size(22.dp).scale(if (on) 0.72f else 1f),
                        )
                    }
                }
            }
        }
    }
}
