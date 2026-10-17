package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.VisualizerTap
import com.vikash.mp3player.ui.theme.Ampli
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Ampli Pro full-screen visualiser: mirrored bars or a pulsing ring that move with the music. */
@Composable
fun VisualizerScreen(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    DisposableEffect(Unit) {
        VisualizerTap.acquire()
        onDispose { VisualizerTap.release() }
    }
    val levels by VisualizerTap.levels.collectAsState()
    var style by rememberSaveable { mutableIntStateOf(0) }
    // Lite mode: plain bars on a flat background, no turning ring, gradients or cross-fades.
    val lite = LocalLiteMode.current
    val angleState: State<Float> = if (lite) remember { mutableFloatStateOf(0f) } else
        rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(24_000), RepeatMode.Restart), label = "angle")
    val angle by angleState

    Box(
        Modifier
            .fillMaxSize()
            .background(if (lite) SolidColor(Color.Black) else Brush.verticalGradient(listOf(Color.Black, Ampli.Night, Color(0xFF2A0F1C))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTogglePlay),
    ) {
        if (lite) {
            Canvas(Modifier.fillMaxSize()) {
                val n = levels.size
                val gap = size.width / n
                val barW = gap * 0.62f
                val bottom = size.height * 0.62f
                levels.forEachIndexed { i, v ->
                    val h = (v * size.height * 0.4f).coerceAtLeast(3f)
                    drawRect(Ampli.Rose, Offset(i * gap + (gap - barW) / 2f, bottom - h), Size(barW, h))
                }
            }
        } else Crossfade(targetState = style, label = "style") { s ->
            Canvas(Modifier.fillMaxSize()) {
                val colors = listOf(Ampli.Rose, Color(0xFFFB7185), Ampli.Blush)
                if (s == 0) {
                    // Mirrored bars around the middle line.
                    val n = levels.size
                    val gap = size.width / n
                    val barW = gap * 0.62f
                    val mid = size.height / 2f
                    levels.forEachIndexed { i, v ->
                        val h = (v * size.height * 0.38f).coerceAtLeast(3f)
                        drawRoundRect(
                            brush = Brush.verticalGradient(colors.reversed() + colors, startY = mid - h, endY = mid + h),
                            topLeft = Offset(i * gap + (gap - barW) / 2f, mid - h),
                            size = Size(barW, h * 2f),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                        )
                    }
                } else {
                    // Ring: spokes grow out from a slowly turning circle.
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = min(size.width, size.height) * 0.22f
                    val bass = levels.take(6).average().toFloat()
                    drawCircle(Brush.radialGradient(listOf(Ampli.Rose.copy(alpha = 0.45f), Color.Transparent), c, r * 1.8f), r * (1.6f + bass * 0.5f), c)
                    rotate(angle, c) {
                        val n = levels.size * 2
                        for (i in 0 until n) {
                            val v = levels[if (i < levels.size) i else n - 1 - i]
                            val a = (i.toFloat() / n) * 2f * Math.PI.toFloat()
                            val len = 6f + v * r * 1.3f
                            val start = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
                            val end = Offset(c.x + cos(a) * (r + len), c.y + sin(a) * (r + len))
                            drawLine(colors[i % colors.size], start, end, strokeWidth = 7f, cap = StrokeCap.Round)
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(artist, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                    tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(12.dp),
                )
            }
            Box(Modifier.weight(1f))
            if (lite) Text(
                stringResource(R.string.lite_visualizer_note),
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            ) else Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                listOf(R.string.visualizer_bars, R.string.visualizer_ring).forEachIndexed { i, label ->
                    FilterChip(
                        selected = style == i,
                        onClick = { style = i },
                        label = { Text(stringResource(label)) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color.White,
                            selectedContainerColor = Ampli.Rose,
                            selectedLabelColor = Color.White,
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.visualizer_tap_hint),
                color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
