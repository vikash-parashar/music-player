package com.vikash.mp3player.ui.skin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * Controls that change with the look. In Classic they are the normal Material ones, so the free
 * app looks exactly as before. In the Pro looks:
 *  - buttons are tactile push keys: a square base with a domed key that sinks in when pressed;
 *  - switches are the 3D pill with a sliding two-dimple knob;
 *  - cards take the look's surface (soft shadow, glass, thick border...).
 * Files import these under the Material names (e.g. `SkinCard as Card`), so every screen follows.
 */

// ---------- cards ----------

@Composable
fun SkinCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val s = LocalSkin.current
    if (s.isClassic) {
        Card(modifier, content = content)
        return
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(modifier.skinSurface(s.shape), content = content)
    }
}

// ---------- push buttons ----------

private val pill = RoundedCornerShape(percent = 50)

/** The square base the key sits in (inset, like a socket). */
@Composable
private fun Modifier.tactileBase(shape: Shape): Modifier {
    val s = LocalSkin.current
    if (s.style == SurfaceStyle.Raised) return this
        .background(s.tactile, shape)
        .innerShadow(shape, s.shadowDark.copy(alpha = 0.75f), 3.dp, 3.dp, 7.dp)
        .innerShadow(shape, s.shadowLight, (-3).dp, (-3).dp, 7.dp)
    return this
        .background(s.tactile, shape)
        .innerShadow(shape, Color.Black.copy(alpha = 0.25f), blur = 12.dp, spread = 2.dp)
        .innerShadow(shape, Color.White.copy(alpha = if (s.dark) 0.25f else 0.9f), offsetY = 1.5.dp, blur = 1.dp)
        .innerShadow(shape, Color.Black.copy(alpha = 0.25f), offsetY = (-1.5).dp, blur = 1.dp)
}

/** The domed key: lifted with a soft shadow, sunk in with inset shading while pressed. */
@Composable
private fun Modifier.tactileDome(shape: Shape, tint: Color, pressed: Boolean): Modifier {
    val s = LocalSkin.current
    if (s.style == SurfaceStyle.Raised) {
        // Moulded key: convex sheen when up, flips to a concave dip when pressed.
        val light = androidx.compose.ui.graphics.lerp(tint, Color.White, if (s.dark) 0.06f else 0.35f)
        val shade = androidx.compose.ui.graphics.lerp(tint, Color.Black, if (s.dark) 0.18f else 0.08f)
        return if (pressed) this
            .background(Brush.linearGradient(listOf(shade, light)), shape)
            .innerShadow(shape, s.shadowDark, 4.dp, 4.dp, 9.dp)
            .innerShadow(shape, s.shadowLight, (-4).dp, (-4).dp, 9.dp)
        else this
            .dropShadow(shape, s.shadowDark, 6.dp, 6.dp, 12.dp)
            .dropShadow(shape, s.shadowLight, (-6).dp, (-6).dp, 12.dp)
            .background(Brush.linearGradient(listOf(light, shade)), shape)
    }
    val hi = if (s.dark) 0.25f else 1f
    val base = this
        .dropShadow(shape, Color.Black.copy(alpha = if (pressed) 0.35f else 0.45f), offsetY = 5.dp, blur = 9.dp, spread = (-2).dp)
        .dropShadow(shape, Color.White.copy(alpha = 0.6f * hi), offsetY = (-3).dp, blur = 5.dp)
        .background(tint, shape)
    return if (pressed) base
        .innerShadow(shape, Color.White.copy(alpha = 0.9f * hi), offsetY = (-3).dp, blur = 10.dp, spread = 1.dp)
        .innerShadow(shape, Color.Black.copy(alpha = 0.4f), offsetY = 3.dp, blur = 9.dp)
    else base
        .innerShadow(shape, Color.Black.copy(alpha = 0.2f), offsetY = (-1).dp, blur = 2.dp)
        .innerShadow(shape, Color.White.copy(alpha = 0.8f * hi), blur = 2.dp, spread = 1.dp)
        .innerShadow(shape, Color.White.copy(alpha = 0.2f * hi), offsetY = 8.dp, blur = 12.dp)
}

@Composable
private fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    accent: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val s = LocalSkin.current
    val haptic = rememberSkinHaptic()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pairHaptics = SkinPressHaptics(pressed)
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "key")
    val baseShape = RoundedCornerShape(if (s.corner < 8.dp) 6.dp else if (s.style == SurfaceStyle.Raised) 18.dp else 12.dp)
    val tint = if (accent) s.tactileAccent else s.tactile
    val ink = if (accent) s.onTactileAccent else s.tactileInk
    Box(
        modifier
            .defaultMinSize(minHeight = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .tactileBase(baseShape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) { if (!pairHaptics) haptic(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().padding(5.dp).scale(press).tactileDome(pill, tint, pressed))
        CompositionLocalProvider(
            LocalContentColor provides ink,
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        ) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** Filled button: tactile accent key in the Pro looks. Custom colours (e.g. red Delete) stay Material. */
@Composable
fun SkinButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalSkin.current.isClassic || colors != null) {
        androidx.compose.material3.Button(onClick, modifier, enabled, colors = colors ?: ButtonDefaults.buttonColors(), content = content)
    } else TactileButton(onClick, modifier, enabled, accent = true, content = content)
}

@Composable
fun SkinOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalSkin.current.isClassic) OutlinedButton(onClick, modifier, enabled, content = content)
    else TactileButton(onClick, modifier, enabled, accent = false, content = content)
}

@Composable
fun SkinTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalSkin.current.isClassic) FilledTonalButton(onClick, modifier, enabled, content = content)
    else TactileButton(onClick, modifier, enabled, accent = false, content = content)
}

/** Square push key with a round dome, for the player's transport buttons. */
@Composable
fun TactileIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    size: Dp = 54.dp,
    accent: Boolean = false,
    tint: Color? = null,
    /** 0..1: a soft accent light around the key (Neumorphic play key while music plays). */
    glow: Float = 0f,
) {
    val s = LocalSkin.current
    val haptic = rememberSkinHaptic()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pairHaptics = SkinPressHaptics(pressed)
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "key")
    val baseShape = RoundedCornerShape(if (s.corner < 8.dp) 6.dp else size / 3.2f)
    val accentColor = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(size)
            .tactileBase(baseShape)
            .clickable(source, indication = null, role = Role.Button) { if (!pairHaptics) haptic(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (glow > 0f && !pressed) Box(
            Modifier.size(size * 0.7f)
                .dropShadow(CircleShape, accentColor.copy(alpha = 0.40f * glow), blur = 18.dp, spread = 2.dp)
                .dropShadow(CircleShape, accentColor.copy(alpha = 0.18f * glow), spread = 3.dp),
        )
        Box(Modifier.size(size * 0.7f).scale(press).tactileDome(CircleShape, if (accent) s.tactileAccent else s.tactile, pressed))
        Icon(
            icon, contentDescription,
            Modifier.size(size * if (accent) 0.4f else 0.36f).scale(press),
            tint = tint ?: if (accent) s.onTactileAccent else s.tactileInk,
        )
    }
}

// ---------- 3D switch ----------

private val snap = CubicBezierEasing(1f, 0f, 1f, 1f)

@Composable
fun SkinSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalSkin.current.isClassic) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
        return
    }
    Switch3D(checked, onCheckedChange, modifier, enabled)
}

/**
 * The pill switch: a sunk-in track with a lit (accent) half and a grey half, and a two-dimple knob
 * that snaps across and covers one of them. The covered half falls into shadow.
 */
@Composable
fun Switch3D(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val s = LocalSkin.current
    val haptic = rememberSkinHaptic()
    val w = 60.dp
    val h = 24.dp
    val knobX by animateDpAsState(if (checked) w * 0.40f else w * 0.05f, tween(600, easing = snap), label = "knob")
    val litShade by animateFloatAsState(if (checked) 0f else 1f, tween(600, easing = snap), label = "lit")
    val greyShade by animateFloatAsState(if (checked) 1f else 0f, tween(600, easing = snap), label = "grey")
    val hue = s.accentHue
    val lit = Brush.verticalGradient(
        0.10f to Color.hsl((hue + 20f) % 360f, 0.95f, 0.80f),
        0.30f to Color.hsl((hue + 20f) % 360f, 1f, 0.60f),
        0.60f to Color.hsl(hue, 0.90f, 0.50f),
        0.75f to Color.hsl(hue, 0.90f, 0.60f),
        1f to Color.hsl(hue, 0.90f, 0.50f),
    )
    val grey = Brush.verticalGradient(
        0f to Color.hsl(220f, 0.2f, 0.95f), 0.6f to Color.hsl(220f, 0.2f, 0.65f),
        0.7f to Color.hsl(220f, 0.2f, 0.70f), 1f to Color.hsl(220f, 0.2f, 0.65f),
    )
    val dark15 = Color.hsl(220f, 0.2f, 0.15f)
    val dark45 = Color.hsl(220f, 0.2f, 0.45f)
    val leftShape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    val rightShape = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    val toggle = if (onCheckedChange != null) Modifier.toggleable(
        value = checked, enabled = enabled, role = Role.Switch,
        interactionSource = remember { MutableInteractionSource() }, indication = null,
    ) { haptic(); onCheckedChange(it) } else Modifier
    Box(
        modifier
            .size(w, h)
            .alpha(if (enabled) 1f else 0.5f)
            .background(s.tactile, pill)
            .innerShadow(pill, Color.Black.copy(alpha = 0.18f), 4.dp, 4.dp, 4.dp)
            .innerShadow(pill, Color.White.copy(alpha = if (s.dark) 0.15f else 0.75f), (-4).dp, (-4).dp, 4.dp)
            .then(toggle),
    ) {
        Box(
            Modifier.offset(x = w * 0.10f, y = h * 0.20f).size(w * 0.40f, h * 0.60f)
                .background(lit, leftShape)
                .innerShadow(leftShape, dark15.copy(alpha = 0.6f), blur = 2.dp)
                .innerShadow(leftShape, dark15.copy(alpha = litShade), 7.dp, 7.dp, 4.dp)
                .innerShadow(leftShape, dark45.copy(alpha = litShade), 7.dp, 7.dp, 6.dp),
        )
        Box(
            Modifier.offset(x = w * 0.50f, y = h * 0.20f).size(w * 0.40f, h * 0.60f)
                .background(grey, rightShape)
                .innerShadow(rightShape, dark15.copy(alpha = 0.6f), blur = 2.dp)
                .innerShadow(rightShape, dark15.copy(alpha = greyShade), 7.dp, 7.dp, 4.dp)
                .innerShadow(rightShape, dark45.copy(alpha = greyShade), 7.dp, 7.dp, 6.dp),
        )
        val knobShadow = Color.hsl(220f, 0.18f, 0.5f)
        Box(
            Modifier.offset(x = knobX, y = h * 0.10f).size(w * 0.55f, h * 0.80f)
                .dropShadow(pill, knobShadow.copy(alpha = 0.8f), 1.dp, 1.dp, 1.dp)
                .dropShadow(pill, knobShadow.copy(alpha = 0.4f), 3.dp, 6.dp, 4.dp)
                .dropShadow(pill, knobShadow.copy(alpha = 0.5f), 6.dp, 9.dp, 10.dp)
                .background(Brush.linearGradient(0.4f to Color.hsl(220f, 0.2f, 0.95f), 0.7f to Color.hsl(220f, 0.2f, 0.65f)), pill),
        ) {
            val dimple = Modifier.size(w * 0.55f * 0.41f, h * 0.80f * 0.8f)
            Box(
                dimple.offset(x = w * 0.55f * 0.05f, y = h * 0.08f).clip(CircleShape)
                    .background(Brush.linearGradient(0.2f to Color.hsl(220f, 0.2f, 0.85f), 0.8f to Color.hsl(220f, 0.2f, 0.95f)))
                    .innerShadow(CircleShape, Color.hsl(220f, 0.2f, 0.85f), 1.dp, 1.dp, 2.dp),
            )
            Box(
                dimple.offset(x = w * 0.55f * 0.54f, y = h * 0.08f).clip(CircleShape)
                    .background(Brush.linearGradient(0.2f to Color.hsl(220f, 0.2f, 0.75f), 0.8f to Color.hsl(220f, 0.2f, 0.95f)))
                    .innerShadow(CircleShape, Color.hsl(220f, 0.2f, 0.70f), 1.dp, 1.dp, 3.dp),
            )
        }
    }
}

// ---------- + button that stretches into a label ----------

/**
 * Round accent button with a +. Phones have no hover, so the first tap stretches it to show the
 * label and the second tap does the action. It folds back by itself after a few seconds.
 */
@Composable
fun ExpandFab(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSkin.current
    val haptic = rememberSkinHaptic()
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (open) { delay(3500); open = false } }
    val corner by animateDpAsState(if (open) 40.dp else 23.dp, tween(300), label = "corner")
    val shape = RoundedCornerShape(corner)
    Row(
        modifier
            .height(46.dp)
            .defaultMinSize(minWidth = 46.dp)
            .dropShadow(shape, Color.Black.copy(alpha = 0.2f), 2.dp, 2.dp, 10.dp)
            .clip(shape)
            .background(s.tactileAccent)
            .clickable(role = Role.Button) {
                haptic()
                if (open) { open = false; onClick() } else open = true
            }
            .animateContentSize(tween(300))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = s.onTactileAccent)
        AnimatedVisibility(open, enter = fadeIn(tween(300)) + expandHorizontally(tween(300)), exit = fadeOut(tween(200)) + shrinkHorizontally(tween(300))) {
            Row {
                Spacer(Modifier.width(10.dp))
                Text(label, color = s.onTactileAccent, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

/** Square-socket play/pause key for the Pro looks (the accent dome glows while playing). */
@Composable
fun TactilePlayButton(isPlaying: Boolean, size: Dp, playIcon: ImageVector, pauseIcon: ImageVector, description: String, onClick: () -> Unit) {
    val s = LocalSkin.current
    // Neumorphic: a rose light breathes around the key while music plays.
    val glow = if (s.style == SurfaceStyle.Raised && isPlaying && !com.vikash.mp3player.ui.LocalLiteMode.current) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "led").animateFloat(
            0.45f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween(1200), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "ledGlow",
        ).value
    } else 0f
    TactileIconButton(if (isPlaying) pauseIcon else playIcon, description, onClick, size = size, accent = true, glow = glow)
}
