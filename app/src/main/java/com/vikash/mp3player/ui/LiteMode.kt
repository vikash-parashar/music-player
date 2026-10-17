package com.vikash.mp3player.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * True while Lite mode is on (Settings, on by default on phones with about 3 GB of RAM or less).
 * Looping animations rest and transitions are shorter. Provided by MainActivity from AppPrefs.
 */
val LocalLiteMode = compositionLocalOf { false }

/** Length of screen transitions in Lite mode. */
const val LITE_ANIM_MS = 150

/** Full-screen pages sliding up from the bottom; quicker in Lite mode. */
@Composable
fun screenEnter(): EnterTransition =
    if (LocalLiteMode.current) slideInVertically(tween(LITE_ANIM_MS)) { it } else slideInVertically { it }

@Composable
fun screenExit(): ExitTransition =
    if (LocalLiteMode.current) slideOutVertically(tween(LITE_ANIM_MS)) { it } else slideOutVertically { it }

/** Full-screen pages that fade in and out; quicker in Lite mode. */
@Composable
fun screenFadeIn(): EnterTransition = if (LocalLiteMode.current) fadeIn(tween(LITE_ANIM_MS)) else fadeIn()

@Composable
fun screenFadeOut(): ExitTransition = if (LocalLiteMode.current) fadeOut(tween(LITE_ANIM_MS)) else fadeOut()
