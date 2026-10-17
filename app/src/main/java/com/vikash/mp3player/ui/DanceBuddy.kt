package com.vikash.mp3player.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.VisualizerTap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Car mode's dancing buddy. Each buddy has nine drawn poses:
 * 0 stand, 1 cheer, 2 point left, 3 point right, 4 clap with a knee up, 5 star jump,
 * 6 hands on hips, 7 bhangra, 8 disco point.
 */
enum class Buddy(val key: String, @StringRes val label: Int, val frames: List<Int>) {
    GIRL("girl", R.string.buddy_girl, listOf(R.drawable.buddy_girl_0, R.drawable.buddy_girl_1, R.drawable.buddy_girl_2, R.drawable.buddy_girl_3, R.drawable.buddy_girl_4, R.drawable.buddy_girl_5, R.drawable.buddy_girl_6, R.drawable.buddy_girl_7, R.drawable.buddy_girl_8)),
    BOY("boy", R.string.buddy_boy, listOf(R.drawable.buddy_boy_0, R.drawable.buddy_boy_1, R.drawable.buddy_boy_2, R.drawable.buddy_boy_3, R.drawable.buddy_boy_4, R.drawable.buddy_boy_5, R.drawable.buddy_boy_6, R.drawable.buddy_boy_7, R.drawable.buddy_boy_8)),
    CAT("cat", R.string.buddy_cat, listOf(R.drawable.buddy_cat_0, R.drawable.buddy_cat_1, R.drawable.buddy_cat_2, R.drawable.buddy_cat_3, R.drawable.buddy_cat_4, R.drawable.buddy_cat_5, R.drawable.buddy_cat_6, R.drawable.buddy_cat_7, R.drawable.buddy_cat_8)),
    DOG("dog", R.string.buddy_dog, listOf(R.drawable.buddy_dog_0, R.drawable.buddy_dog_1, R.drawable.buddy_dog_2, R.drawable.buddy_dog_3, R.drawable.buddy_dog_4, R.drawable.buddy_dog_5, R.drawable.buddy_dog_6, R.drawable.buddy_dog_7, R.drawable.buddy_dog_8));

    companion object {
        private const val PREFS = "ampli_prefs"
        private const val KEY = "car_buddy"
        /** The saved pick; null means the driver chose "Off". */
        fun load(context: Context): Buddy? {
            val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, GIRL.key)
            return entries.firstOrNull { it.key == v }
        }
        fun save(context: Context, buddy: Buddy?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, buddy?.key ?: "off").apply()
        }
    }
}

/** Steps, eight beats each; the routine changes every eight beats. */
private val ROUTINES = arrayOf(
    intArrayOf(2, 3, 2, 3, 2, 3, 1, 1),
    intArrayOf(1, 4, 1, 7, 1, 4, 5, 7),
    intArrayOf(6, 8, 6, 8, 7, 1, 7, 1),
    intArrayOf(4, 5, 4, 5, 2, 3, 8, 1),
)
private const val POSE_MS = 1000L
private const val FADE_MS = 90L

/**
 * Draws [buddy] dancing. While [isPlaying] it takes a new step on each beat, found from the bass in
 * the player's audio tap (no microphone). If no beat is heard for a while it keeps a steady step so
 * it never freezes mid-song. Paused: stands and breathes. When [songKey] changes it jumps into the
 * star pose for a second, then dances again.
 */
@Composable
fun DanceBuddy(buddy: Buddy, isPlaying: Boolean, songKey: String?, modifier: Modifier = Modifier) {
    val images: List<ImageBitmap> = buddy.frames.map { ImageBitmap.imageResource(it) }
    val playing by rememberUpdatedState(isPlaying)

    // Beat clock, all in elapsed-realtime millis.
    val beats = remember { mutableIntStateOf(0) }
    val lastBeat = remember { mutableLongStateOf(0L) }
    val interval = remember { mutableLongStateOf(600L) }
    val poseUntil = remember { mutableLongStateOf(0L) }
    val enterAt = remember(buddy) { mutableLongStateOf(now()) }
    val firstSong = remember { songKey }
    LaunchedEffect(songKey) { if (songKey != firstSong || poseUntil.longValue != 0L) poseUntil.longValue = now() + POSE_MS }

    DisposableEffect(Unit) {
        VisualizerTap.listening = true
        onDispose { VisualizerTap.listening = false }
    }
    // Beat finder: a jump in bass energy well above its recent average.
    LaunchedEffect(Unit) {
        var avg = 0f
        VisualizerTap.levels.collect { lv ->
            var bass = 0f
            for (i in 0 until 6) bass += lv[i]
            bass /= 6f
            val t = now()
            val gap = t - lastBeat.longValue
            if (playing && bass > 0.3f && bass > avg * 1.25f && gap > 280) beat(t, beats, lastBeat, interval)
            avg = avg * 0.9f + bass * 0.1f
        }
    }
    // Fallback step when the tap hears nothing (quiet intro, or audio the tap can't read).
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val t = now()
            if (playing && t - lastBeat.longValue > 650) beat(t, beats, lastBeat, interval, steady = true)
        }
    }
    // Redraw every frame while anything moves.
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { tick.longValue = it } }

    val shown = remember { mutableIntStateOf(0) }
    val previous = remember { mutableIntStateOf(0) }
    val swapAt = remember { mutableLongStateOf(0L) }

    Canvas(modifier) {
        tick.longValue // read so the canvas redraws each frame
        val t = now()
        var lift = 0f
        var squash = 0f
        var lean = 0f
        val target: Int
        val posing = t < poseUntil.longValue
        if (posing) {
            val k = 1f - (poseUntil.longValue - t) / POSE_MS.toFloat()
            target = 5
            lift = if (k < 0.45f) sin(PI.toFloat() * k / 0.45f) * 40f else 0f
            squash = if (k > 0.45f && k < 0.52f) 0.1f else 0f
        } else if (!playing) {
            target = 0
            squash = sin(t / 1000f * 2.2f) * 0.01f
        } else {
            val n = beats.intValue
            target = ROUTINES[(n / 8) % ROUTINES.size][n % 8]
            val ph = ((t - lastBeat.longValue) / interval.longValue.toFloat()).coerceIn(0f, 1f)
            lift = sin(PI.toFloat() * ph) * 9f
            squash = exp(-ph * 9f) * 0.07f
            lean = sin(PI.toFloat() * n + PI.toFloat() * ph) * 0.035f
        }
        if (target != shown.intValue) { previous.intValue = shown.intValue; shown.intValue = target; swapAt.longValue = t }

        val img = images[shown.intValue]
        val prev = images[previous.intValue]
        val d = density
        val sc = min((size.height - 24 * d) / img.height, size.width * 0.86f / img.width)
        val w = img.width * sc
        val h = img.height * sc
        val gx = size.width / 2f
        val gy = size.height - 10 * d
        // Hop in from the side when the buddy appears.
        val ent = ((t - enterAt.longValue) / 900f).coerceIn(0f, 1f)
        val ex = (1f - ent) * size.width * 0.7f
        val hop = if (ent < 1f) abs(sin(ent * PI.toFloat() * 3f)) * 22f else 0f
        val up = (lift + hop) * d

        drawOval(
            Brush.radialGradient(listOf(Color(0xFFE11D48).copy(alpha = if (playing) 0.3f else 0.12f), Color.Transparent), Offset(gx, gy), size.width * 0.45f),
            topLeft = Offset(gx - size.width * 0.45f, gy - 22 * d), size = Size(size.width * 0.9f, 44 * d),
        )
        val shadow = 1f - min(0.5f, up / (90 * d))
        drawOval(Color.Black.copy(alpha = 0.5f * shadow), Offset(gx + ex - w * 0.3f * shadow, gy - 7 * d * shadow), Size(w * 0.6f * shadow, 14 * d * shadow))

        val fade = ((t - swapAt.longValue) / FADE_MS.toFloat()).coerceIn(0f, 1f)
        withTransform({
            translate(gx + ex, gy - up)
            rotateRad(lean, Offset.Zero)
            scale(1f + squash * 0.5f, 1f - squash, Offset.Zero)
        }) {
            val dst = IntSize(w.roundToInt(), h.roundToInt())
            val at = IntOffset((-w / 2f).roundToInt(), (-h).roundToInt())
            if (fade < 1f) drawImage(prev, dstOffset = at, dstSize = dst, alpha = 1f - fade)
            drawImage(img, dstOffset = at, dstSize = dst, alpha = if (fade < 1f) fade else 1f)
        }
    }
}

private fun now() = android.os.SystemClock.elapsedRealtime()

private fun beat(
    t: Long,
    beats: androidx.compose.runtime.MutableIntState,
    lastBeat: androidx.compose.runtime.MutableLongState,
    interval: androidx.compose.runtime.MutableLongState,
    steady: Boolean = false,
) {
    val gap = t - lastBeat.longValue
    if (!steady && gap in 280..1200) interval.longValue = (interval.longValue * 0.7f + gap * 0.3f).toLong()
    if (steady) interval.longValue = 650L
    lastBeat.longValue = t
    beats.intValue = beats.intValue + 1
}
