package com.vikash.mp3player.ui.skin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/*
 * More controls for the Pro app styles, after the Uiverse picks the owner chose:
 *  - AmpliLoader: five glass rings ripple out around the Ampli note while something loads;
 *  - HelpBubble: yellow-to-red "?" that wobbles and shows a "Help" tooltip while pressed;
 *  - GlowToggle: round key that lights up cyan when on (shuffle, repeat);
 *  - SkinCheckbox: round dip that flips over green with a tick.
 * Classic keeps the Material versions.
 */

// ---------- loader ----------

@Composable
fun SkinLoader(modifier: Modifier = Modifier) {
    if (LocalSkin.current.isClassic) CircularProgressIndicator(modifier) else AmpliLoader(modifier)
}

@Composable
fun AmpliLoader(modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val s = LocalSkin.current
    val t = rememberInfiniteTransition(label = "loader")
    // Inner ring first, each outer ring 0.2 s later, like a ripple.
    val scales = (0 until 5).map { i ->
        t.animateFloat(
            1f, 1f,
            infiniteRepeatable(
                keyframes { durationMillis = 2000; 1f at 0 using FastOutSlowInEasing; 1.3f at 1000 using FastOutSlowInEasing; 1f at 2000 },
                initialStartOffset = StartOffset(i * 200),
            ),
            label = "ring$i",
        )
    }
    val flash by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes { durationMillis = 2000; 0f at 0; 1f at 1000; 0f at 2000 }), label = "flash")
    val flashColor = if (s.dark || s.style == SurfaceStyle.Glass) Color.White else s.tactileAccent.let { if (it.luminanceLike() > 0.8f) MaterialTheme.colorScheme.primary else it }
    val logo = androidx.compose.ui.graphics.lerp(Color.Gray, flashColor, flash)
    val ringFill = Brush.verticalGradient(listOf(Color(0x33646464), Color(0x33323232)))
    Box(modifier.size(size).wrapContentSize(), contentAlignment = Alignment.Center) {
        // Outermost first so inner rings sit on top.
        for (i in 4 downTo 0) {
            val inset = 0.4f - i * 0.1f
            val d = size * (1f - inset * 2f)
            val sc = scales[i].value
            val lift = (sc - 1f) / 0.3f
            val edge = Color(0xFF646464).copy(alpha = 1f - i * 0.2f)
            Box(
                Modifier.zIndex((5 - i).toFloat()).size(d).scale(sc)
                    .dropShadow(CircleShape, Color.Black.copy(alpha = 0.3f), offsetY = 10.dp + 20.dp * lift, blur = 10.dp + 10.dp * lift)
                    .background(ringFill, CircleShape)
                    .drawBehind {
                        // CSS border-top on a circle: a thin arc across the top.
                        drawArc(edge, 200f, 140f, false, style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
                    },
            )
        }
        Icon(Icons.Default.MusicNote, null, Modifier.zIndex(10f).size(size * 0.28f), tint = logo)
    }
}

private fun Color.luminanceLike(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ---------- help bubble with tooltip ----------

private val hot = Brush.linearGradient(listOf(Color(0xFFFFE53B), Color(0xFFFF2525)))

/** Round "?" key. Pressing shows the tooltip and wobbles the mark; a tap opens help. */
@Composable
fun HelpBubble(tooltip: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberSkinHaptic()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val tipAlpha by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 300 else 600), label = "tip")
    val tipShift by animateFloatAsState(if (pressed) 0f else 1f, tween(300), label = "tipShift")
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        // jello-vertical
        val steps = listOf(0.75f to 1.25f, 1.25f to 0.75f, 0.85f to 1.15f, 1.05f to 0.95f, 0.95f to 1.05f, 1f to 1f)
        for ((x, y) in steps) {
            launch { sx.animateTo(x, tween(70)) }
            sy.animateTo(y, tween(70))
        }
    }
    Box(modifier.size(40.dp), contentAlignment = Alignment.CenterEnd) {
        // The tooltip floats to the left of the key without pushing the header around.
        if (tipAlpha > 0f) Box(
            Modifier.wrapContentWidth(Alignment.End, unbounded = true)
                .offset(x = -(46.dp + 12.dp * tipShift)).alpha(tipAlpha)
                .background(hot, RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        ) { Text(tooltip, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp) }
        Box(
            Modifier.size(40.dp)
                .dropShadow(CircleShape, Color.Black.copy(alpha = 0.15f), offsetY = 10.dp, blur = 10.dp)
                .background(hot, CircleShape)
                .clickable(source, indication = null, role = Role.Button) { haptic(); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.QuestionMark, tooltip,
                Modifier.size(20.dp).graphicsLayer { scaleX = sx.value; scaleY = sy.value },
                tint = Color.White,
            )
        }
    }
}

// ---------- glowing round toggle ----------

private val Cyan = Color(0xFF97F3FF)

/** Round key that glows cyan when on (off: dark grey with a sunk-in edge). */
@Composable
fun GlowToggle(checked: Boolean, onToggle: () -> Unit, icon: ImageVector, contentDescription: String?, size: Dp = 46.dp) {
    val haptic = rememberSkinHaptic()
    val on by animateFloatAsState(if (checked) 1f else 0f, tween(300), label = "glow")
    val bg by animateColorAsState(if (checked) Color(0xFF92B4B8) else Color(0xFF636363), tween(300), label = "glowBg")
    val edge by animateColorAsState(if (checked) Color.White else Color(0xFF7E7E7E), tween(300), label = "glowEdge")
    val ink by animateColorAsState(if (checked) Color.White else Color(0xFF303030), tween(300), label = "glowInk")
    Box(
        Modifier.size(size)
            .dropShadow(CircleShape, Cyan.copy(alpha = 0.55f * on), blur = 20.dp)
            .dropShadow(CircleShape, Cyan.copy(alpha = 0.35f * on), blur = 40.dp)
            .background(bg, CircleShape)
            .innerShadow(CircleShape, Color(0xFF020202).copy(alpha = 1f - on), blur = 3.dp)
            .innerShadow(CircleShape, Cyan.copy(alpha = on), blur = 10.dp)
            .border(2.dp, edge, CircleShape)
            .toggleable(
                value = checked, role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { haptic(); onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (on > 0f) Icon(icon, null, Modifier.size(size * 0.42f).alpha(on * 0.6f).scale(1.15f), tint = Cyan)
        Icon(icon, contentDescription, Modifier.size(size * 0.4f), tint = ink)
    }
}

// ---------- round checkbox ----------

@Composable
fun SkinCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    if (LocalSkin.current.isClassic) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
        return
    }
    val haptic = rememberSkinHaptic()
    val flip by animateFloatAsState(if (checked) 360f else 0f, tween(400), label = "flip")
    val bg by animateColorAsState(if (checked) Color(0xFF32CD32) else Color(0xFFCCCCCC), tween(400), label = "ckBg")
    val tick by animateFloatAsState(if (checked) 1f else 0f, tween(250, delayMillis = if (checked) 150 else 0), label = "tick")
    val toggle = if (onCheckedChange != null) Modifier.toggleable(
        value = checked, role = Role.Checkbox,
        interactionSource = remember { MutableInteractionSource() }, indication = null,
    ) { haptic(); onCheckedChange(it) } else Modifier
    Box(
        modifier.padding(6.dp).size(30.dp)
            .graphicsLayer { rotationX = flip; cameraDistance = 12f * density }
            .then(if (checked) Modifier.dropShadow(CircleShape, Color.Black.copy(alpha = 0.2f), 3.dp, 3.dp, 3.dp) else Modifier)
            .background(bg, CircleShape)
            .then(
                if (!checked) Modifier
                    .innerShadow(CircleShape, Color(0xFFB3B3B3), 8.dp, 8.dp, 8.dp)
                    .innerShadow(CircleShape, Color.White, (-8).dp, (-8).dp, 8.dp)
                else Modifier,
            )
            .then(toggle),
    ) {
        if (tick > 0f) Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val path = Path().apply {
                moveTo(w * 0.28f, w * 0.52f)
                lineTo(w * 0.44f, w * 0.68f)
                lineTo(w * 0.74f, w * 0.34f)
            }
            drawPath(path, Color.Black.copy(alpha = 0.3f * tick), style = Stroke(w * 0.1f, cap = StrokeCap.Round), alpha = 1f)
            translate(-w * 0.03f, -w * 0.03f) {
                drawPath(path, Color.White.copy(alpha = tick), style = Stroke(w * 0.1f, cap = StrokeCap.Round))
            }
        }
    }
}
