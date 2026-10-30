package com.vikash.mp3player.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.ui.theme.Lux
import com.vikash.mp3player.ui.skin.skinSurface

/** True when the current theme is a dark one (judged from the background, so Pro themes work too). */
@Composable
internal fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.4f

/**
 * Frosted card used by the floating dock and the mini player: near-opaque white by day, smoked
 * glass by night, a hairline edge and a soft shadow underneath.
 */
@Composable
internal fun Modifier.luxGlass(shape: Shape, elevation: Dp = 14.dp): Modifier {
    // Pro app styles draw the dock and mini player in their own surface (soft shadow, glass, border...).
    if (!com.vikash.mp3player.ui.skin.LocalSkin.current.isClassic) return this.skinSurface(shape)
    val dark = isDarkTheme()
    val glass = if (dark) Color(0xFF1C1816).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.97f)
    val edge = if (dark) Color.White.copy(alpha = 0.07f) else Color(0x1014100C)
    return this
        .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.22f))
        .clip(shape)
        .background(glass)
        .border(1.dp, edge, shape)
}

/** The warm coral glow drawn behind whatever is active. */
internal fun luxGlowBrush(strength: Float = 1f): Brush = Brush.radialGradient(
    0f to Lux.Glow.copy(alpha = 0.50f * strength),
    0.45f to Lux.Ember.copy(alpha = 0.20f * strength),
    1f to Color.Transparent,
)

/**
 * Round play/pause key in the luxury style: ink by day, ivory by night. While music plays a warm
 * glow breathes behind it; a tap presses it in slightly. Lite mode keeps it still.
 */
@Composable
fun LuxPlayButton(isPlaying: Boolean, size: Dp, onClick: () -> Unit) {
    if (!com.vikash.mp3player.ui.skin.LocalSkin.current.isClassic) {
        com.vikash.mp3player.ui.skin.TactilePlayButton(
            isPlaying, size, Icons.Default.PlayArrow, Icons.Default.Pause,
            stringResource(if (isPlaying) R.string.common_pause else R.string.common_play), onClick,
        )
        return
    }
    val lite = LocalLiteMode.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    val glowOn by animateFloatAsState(if (isPlaying && !lite) 1f else 0f, tween(400), label = "glowOn")
    val breathe = if (!lite && isPlaying) {
        rememberInfiniteTransition(label = "breathe")
            .animateFloat(1f, 1.18f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b").value
    } else 1f
    val ink = MaterialTheme.colorScheme.primary
    val onInk = MaterialTheme.colorScheme.onPrimary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (glowOn > 0f) Canvas(Modifier.requiredSize(size * 1.5f).scale(breathe)) {
            drawCircle(luxGlowBrush(glowOn * (1.3f - (breathe - 1f) * 2f)))
        }
        Box(
            Modifier.size(size).scale(press).clip(CircleShape).background(ink)
                .clickable(interactionSource = source, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (isPlaying) R.string.common_pause else R.string.common_play),
                tint = onInk,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}
