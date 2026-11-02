package com.vikash.mp3player.ui.skin

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Now Playing transport as one sheet of paper (owner's ask, 2026-11-02): shuffle, previous,
 * play/pause, next and repeat are not five separate buttons but bumps pressed up out of a single
 * card, the same colour as the card, lit from the top left. Pressing a key pushes it back into
 * the sheet.
 */

private val LocalPaper = compositionLocalOf { Color.White }
private val ease = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Composable
private fun paperColor(): Color {
    val s = LocalSkin.current
    val surface = MaterialTheme.colorScheme.surface
    return when {
        s.style == SurfaceStyle.Raised -> s.card
        s.dark -> lerp(surface, Color.White, 0.07f)
        else -> lerp(surface, s.tactile, 0.3f)
    }
}

private fun light(paper: Color, dark: Boolean) = lerp(paper, Color.White, if (dark) 0.10f else 0.9f)
private fun shade(paper: Color, dark: Boolean) = lerp(paper, Color.Black, if (dark) 0.55f else 0.2f)

/** The single sheet the keys rise out of. */
@Composable
fun PaperPanel(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val s = LocalSkin.current
    val paper = paperColor()
    val shape = RoundedCornerShape(30.dp)
    CompositionLocalProvider(LocalPaper provides paper) {
        Row(
            modifier
                .fillMaxWidth()
                .dropShadow(shape, Color.Black.copy(alpha = if (s.dark) 0.45f else 0.14f), offsetY = 8.dp, blur = 20.dp)
                .background(paper, shape)
                .innerShadow(shape, light(paper, s.dark), offsetY = 1.5.dp, blur = 1.dp)
                .padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * A key moulded out of the paper. [accent] makes it the purple play key; [active] lights the icon
 * and a small dot (shuffle / repeat on).
 */
@Composable
fun PaperKey(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    size: Dp,
    round: Boolean = false,
    accent: Boolean = false,
    active: Boolean = false,
    role: Role = Role.Button,
) {
    val s = LocalSkin.current
    val paper = LocalPaper.current
    val haptic = rememberSkinHaptic()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pairHaptics = SkinPressHaptics(pressed)
    val down by animateFloatAsState(if (pressed) 1f else 0f, tween(260, easing = ease), label = "paperKey")
    val shape: Shape = if (round) CircleShape else RoundedCornerShape(size / 3.2f)
    val up = 1f - down
    val lift = 7.dp * up
    val face = if (accent) Brush.linearGradient(listOf(lerp(s.tactileAccent, Color.White, 0.22f), lerp(s.tactileAccent, Color.Black, 0.12f)))
    else Brush.linearGradient(listOf(lerp(paper, Color.White, if (s.dark) 0.05f else 0.5f), lerp(paper, Color.Black, if (s.dark) 0.08f else 0.04f)))
    val ink = when {
        accent -> s.onTactileAccent
        active -> MaterialTheme.colorScheme.primary
        else -> s.tactileInk
    }
    Box(
        Modifier
            .size(size)
            // Raised: light falls on the top-left rim, shade under the bottom-right.
            .dropShadow(shape, shade(paper, s.dark).copy(alpha = up), lift, lift, 14.dp)
            .dropShadow(shape, light(paper, s.dark).copy(alpha = up), -lift, -lift, 14.dp)
            .then(if (accent) Modifier.dropShadow(shape, s.tactileAccent.copy(alpha = 0.35f * up), offsetY = 6.dp, blur = 16.dp) else Modifier)
            .background(face, shape)
            // Pushed back into the sheet: the shading flips inside.
            .innerShadow(shape, shade(paper, s.dark).copy(alpha = 0.9f * down), 4.dp, 4.dp, 8.dp)
            .innerShadow(shape, light(paper, s.dark).copy(alpha = down), (-4).dp, (-4).dp, 8.dp)
            .clickable(source, indication = null, role = role) { if (!pairHaptics) haptic(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(size * if (accent) 0.42f else 0.4f).scale(1f - 0.06f * down), tint = ink)
        if (active && !accent) Box(
            Modifier.align(Alignment.BottomCenter).offset(y = (-5).dp).size(4.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}
