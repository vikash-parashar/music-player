package com.vikash.mp3player.ui.skin

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Whole-card push effect for the Pro looks (style "D", picked by the owner 2026-11-02): every card
 * is built like the push buttons. A grey base shape sits behind it, the card face rides on top with
 * a thick bottom edge, and pressing anywhere on the card sinks the face into the base. Buttons
 * inside the card sit in the same grey base and sink together with the card.
 */

/** True while the card around this content is held down, so buttons inside sink with it. */
val LocalCardPressed = compositionLocalOf { false }

private val EDGE = 5.dp
private val ease = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** The raised grey base shape that cards and buttons sit in. */
@Composable
fun Modifier.pressBase(shape: Shape): Modifier {
    val s = LocalSkin.current
    val top = lerp(s.tactile, Color.White, if (s.dark) 0.06f else 0.35f)
    val bottom = lerp(s.tactile, Color.Black, if (s.dark) 0.14f else 0.07f)
    return this
        .dropShadow(shape, Color.Black.copy(alpha = if (s.dark) 0.35f else 0.13f), offsetY = 2.dp, blur = 4.dp)
        .background(Brush.verticalGradient(listOf(top, bottom)), shape)
        .innerShadow(shape, Color.White.copy(alpha = if (s.dark) 0.14f else 0.9f), offsetY = 1.dp, blur = 1.dp)
}

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
 * Base + sinking frame without a face: the caller draws the face (a cover image, a tile...).
 * Does nothing in Classic.
 */
@Composable
fun Modifier.skinPressFrame(shape: Shape, pressed: MutableState<Boolean> = remember { mutableStateOf(false) }): Modifier {
    val s = LocalSkin.current
    if (s.isClassic) return this
    val down by animateFloatAsState(if (pressed.value) 1f else 0f, tween(220, easing = ease), label = "cardPress")
    val edge = lerp(s.tactile, Color.Black, if (s.dark) 0.35f else 0.16f)
    return this
        .pressBase(shape)
        .watchPress(pressed)
        .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 5.dp + EDGE)
        .graphicsLayer { translationY = EDGE.toPx() * down }
        .dropShadow(shape, edge.copy(alpha = 1f - down), offsetY = EDGE * (1f - down))
        .dropShadow(shape, Color.Black.copy(alpha = (if (s.dark) 0.3f else 0.12f) * (1f - down)), offsetY = EDGE + 2.dp, blur = 6.dp)
}

/** The look's card surface on a sinking frame: the whole card behaves like one big push button. */
@Composable
fun Modifier.skinPressSurface(
    shape: Shape = LocalSkin.current.shape,
    pressed: MutableState<Boolean> = remember { mutableStateOf(false) },
): Modifier {
    if (LocalSkin.current.isClassic) return this.skinSurface(shape)
    return this.skinPressFrame(shape, pressed).skinSurface(shape)
}
