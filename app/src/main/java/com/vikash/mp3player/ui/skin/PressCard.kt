package com.vikash.mp3player.ui.skin

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * One paper style for the Pro looks (owner, 2026-11-02): every card is the same sheet of paper as
 * the Now Playing controls, and pressing anywhere on it pushes the whole sheet in. Buttons inside
 * rise out of the paper (see PaperControls.kt) and sink together with their card.
 */

/** True while the card around this content is held down, so buttons inside sink with it. */
val LocalCardPressed = compositionLocalOf { false }

private val ease = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Watches touches without taking them, so taps still reach the buttons and rows inside. A finger
 * that rests for a moment sinks the card; a quick tap gives a short dip; a scroll never sinks it.
 */
@Composable
private fun Modifier.watchPress(pressed: MutableState<Boolean>): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            var moved = false
            val ended = withTimeoutOrNull(90L) {
                var done = false
                while (!done) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed) done = true
                    else if ((c.position - down.position).getDistance() > slop) { moved = true; done = true }
                }
                true
            }
            if (ended == null) {
                pressed.value = true
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed || (c.position - down.position).getDistance() > slop * 2) break
                }
                pressed.value = false
            } else if (!moved) {
                scope.launch { pressed.value = true; delay(120); pressed.value = false }
            }
        }
    }
}

/**
 * Sinking frame without a face, for content that draws its own (a story cover): a soft shadow
 * that goes away and a slight shrink while held. Does nothing in Classic.
 */
@Composable
fun Modifier.skinPressFrame(shape: Shape, pressed: MutableState<Boolean> = remember { mutableStateOf(false) }): Modifier {
    val s = LocalSkin.current
    if (s.isClassic) return this
    val down by animateFloatAsState(if (pressed.value) 1f else 0f, tween(220, easing = ease), label = "cardPress")
    val up = 1f - down
    return this
        .watchPress(pressed)
        .graphicsLayer { val k = 1f - 0.025f * down; scaleX = k; scaleY = k }
        .dropShadow(shape, Color.Black.copy(alpha = (if (s.dark) 0.45f else 0.2f) * up), offsetY = 8.dp * up, blur = 18.dp)
}

/** The paper sheet every card uses in the Pro looks; held down, the whole sheet is pushed in. */
@Composable
fun Modifier.skinPressSurface(
    shape: Shape = LocalSkin.current.shape,
    pressed: MutableState<Boolean> = remember { mutableStateOf(false) },
): Modifier {
    val s = LocalSkin.current
    if (s.isClassic) return this.skinSurface(shape)
    val paper = paperColor()
    val down by animateFloatAsState(if (pressed.value) 1f else 0f, tween(220, easing = ease), label = "cardPress")
    val up = 1f - down
    return this
        .watchPress(pressed)
        .dropShadow(shape, Color.Black.copy(alpha = (if (s.dark) 0.45f else 0.14f) * up), offsetY = 8.dp * up, blur = 20.dp)
        .background(paper, shape)
        .innerShadow(shape, paperLight(paper, s.dark).copy(alpha = up), offsetY = 1.5.dp, blur = 1.dp)
        .innerShadow(shape, paperShade(paper, s.dark).copy(alpha = 0.8f * down), 4.dp, 4.dp, 10.dp)
        .innerShadow(shape, paperLight(paper, s.dark).copy(alpha = down), (-4).dp, (-4).dp, 10.dp)
}
