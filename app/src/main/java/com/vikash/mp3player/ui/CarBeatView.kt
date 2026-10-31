package com.vikash.mp3player.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.VisualizerTap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp

/** The ten light shows for the car on Now Playing. Free users get Equalizer wall; Pro picks any. */
enum class CarLights(@StringRes val label: Int) {
    EQ_WALL(R.string.car_lights_eq),
    HEADLIGHTS(R.string.car_lights_headlights),
    HAZARD(R.string.car_lights_hazard),
    UNDERGLOW(R.string.car_lights_underglow),
    LIGHT_BAR(R.string.car_lights_bar),
    RIPPLES(R.string.car_lights_ripples),
    BEAMS(R.string.car_lights_beams),
    BOUNCE(R.string.car_lights_bounce),
    CLUB(R.string.car_lights_club),
    CALM(R.string.car_lights_calm);

    companion object {
        private const val KEY = "car_lights"
        private fun prefs(c: Context) = c.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)

        fun current(c: Context, isPro: Boolean): CarLights =
            if (!isPro) EQ_WALL else entries.firstOrNull { it.name == prefs(c).getString(KEY, null) } ?: EQ_WALL

        fun save(c: Context, v: CarLights) = prefs(c).edit().putString(KEY, v.name).apply()
    }
}

// Spots on car_hero.webp (1280 x 714), as fractions of its width and height.
private val HeadR = Offset(0.336f, 0.508f)
private val HeadL = Offset(0.11f, 0.50f)
private val Tail = Offset(0.70f, 0.43f)
private val WheelF = Offset(0.442f, 0.63f)
private val WheelR = Offset(0.684f, 0.58f)
private val Amber = Color(0xFFFFB347)
private val Crimson = Color(0xFFA31C1B)
private val Neon = Color(0xFF2BFF9A)
private val Cool = Color(0xFFDCEBFF)
private val ClubColors = listOf(Color(0xFFFF3CAC), Color(0xFF2BD2FF), Color(0xFFFFD23C), Color(0xFF7B5CFF), Neon)

/**
 * The car with lights that follow the beat. Beats come from the audio tap (a jump in bass well
 * above its recent average); when it hears nothing, a steady 112 BPM pulse stands in.
 */
@Composable
internal fun CarBeatView(isPlaying: Boolean, isPro: Boolean, onGetPro: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lite = LocalLiteMode.current
    var lights by remember(isPro) { mutableStateOf(CarLights.current(context, isPro)) }
    var menu by remember { mutableStateOf(false) }
    val car = ImageBitmap.imageResource(R.drawable.car_hero)

    val playing by rememberUpdatedState(isPlaying && !lite)
    val kick = remember { mutableFloatStateOf(0f) } // 0..1, jumps to 1 on a beat and fades
    val hat = remember { mutableFloatStateOf(0f) }
    val beats = remember { mutableIntStateOf(0) }
    val lastBeat = remember { mutableLongStateOf(0L) }
    val lastHeard = remember { mutableLongStateOf(0L) }
    val levels = remember { mutableStateOf(FloatArray(VisualizerTap.BARS)) }
    val ripples = remember { mutableStateListOf<Long>() }
    val clock = remember { mutableLongStateOf(0L) }
    val now = { android.os.SystemClock.elapsedRealtime() }

    fun onBeat(t: Long) {
        kick.floatValue = 1f
        beats.intValue++
        lastBeat.longValue = t
        ripples.add(t)
        if (ripples.size > 5) ripples.removeAt(0)
    }

    DisposableEffect(Unit) {
        VisualizerTap.acquire()
        onDispose { VisualizerTap.release() }
    }
    LaunchedEffect(Unit) {
        var avg = 0f
        var trebleAvg = 0f
        VisualizerTap.levels.collect { lv ->
            levels.value = lv
            var bass = 0f
            for (i in 0 until 6) bass += lv[i]
            bass /= 6f
            var treble = 0f
            for (i in 30 until 40) treble += lv[i]
            treble /= 10f
            val t = now()
            if (playing && bass > 0.3f && bass > avg * 1.25f && t - lastHeard.longValue > 280) {
                lastHeard.longValue = t
                val wait = outputDelayMs(context)
                launch { delay(wait); if (playing) onBeat(now()) }
            }
            if (playing && treble > 0.2f && treble > trebleAvg * 1.3f) hat.floatValue = 1f
            avg = avg * 0.9f + bass * 0.1f
            trebleAvg = trebleAvg * 0.9f + treble * 0.1f
        }
    }
    // Steady pulse when the tap hears no beat (quiet intro, or audio it can't read).
    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            val t = now()
            if (playing && t - lastHeard.longValue > 1500 && t - lastBeat.longValue > 535) {
                onBeat(t)
                launch { delay(267); hat.floatValue = 1f }
            }
        }
    }
    // Frame clock: only runs while music plays or a light is still fading out.
    LaunchedEffect(isPlaying, lite) {
        var last = 0L
        while (playing || kick.floatValue > 0.01f || hat.floatValue > 0.01f) {
            withFrameNanos { ns ->
                val dt = if (last == 0L) 0f else ((ns - last) / 1e9f).coerceAtMost(0.05f)
                last = ns
                kick.floatValue *= exp(-dt * 6f)
                hat.floatValue *= exp(-dt * 10f)
                clock.longValue = now()
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1280f / 714f)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val tick = clock.longValue // redraw each frame
                val k = kick.floatValue
                val h = hat.floatValue
                when (lights) {
                    CarLights.BOUNCE -> {
                        translate(top = -0.022f * size.height * k) {
                            scale(1f + 0.012f * k, pivot = Offset(size.width / 2, size.height * 0.8f)) { drawCar(car) }
                        }
                    }
                    CarLights.EQ_WALL -> {
                        drawCar(car)
                        drawEqWall(levels.value, k)
                        clipPath(carOutline()) { drawCar(car) } // the car stays in front of the bars
                    }
                    else -> drawCar(car)
                }
                when (lights) {
                    CarLights.HEADLIGHTS -> {
                        glow(HeadR, 0.11f, 0.05f, Cool, 0.15f + 0.85f * k, 0.8f + 0.6f * k)
                        glow(HeadL, 0.06f, 0.03f, Cool, 0.15f + 0.85f * k, 0.8f + 0.6f * k)
                        glow(Offset(0.30f, 0.52f), 0.35f, 0.2f, Cool, 0.35f * k)
                    }
                    CarLights.HAZARD -> {
                        val on = if (k > 0.35f) 1f else 0f
                        val left = beats.intValue % 2 == 0
                        glow(Offset(0.11f, 0.58f), 0.045f, 0.028f, Amber, if (left) on else 0f)
                        glow(Offset(0.35f, 0.57f), 0.045f, 0.028f, Amber, if (left) 0f else on)
                        glow(Offset(0.70f, 0.46f), 0.03f, 0.02f, Amber, if (left) 0f else on)
                        glow(Offset(0.22f, 0.58f), 0.3f, 0.15f, Amber, 0.2f * on)
                    }
                    CarLights.UNDERGLOW -> {
                        val c = Color.hsv((150f + beats.intValue * 23f) % 360f, 0.85f, 1f)
                        glow(Offset(0.41f, 0.75f), 0.33f * (0.9f + 0.25f * k), 0.09f, c, 0.25f + 0.75f * k)
                    }
                    CarLights.LIGHT_BAR -> {
                        val cx = size.width * 0.614f
                        val bw = size.width * 0.078f * (1f + 1.6f * k)
                        drawRect(
                            Brush.verticalGradient(listOf(Color(0xFFFF2A26), Crimson), endY = size.height * 0.68f),
                            Offset(cx - bw / 2, 0f), Size(bw, size.height * 0.68f), alpha = 0.1f + 0.8f * k, blendMode = BlendMode.Screen,
                        )
                        drawRect(
                            Brush.verticalGradient(listOf(Color(0x99FF281E), Color.Transparent), startY = size.height * 0.66f, endY = size.height * 0.96f),
                            Offset(cx - bw / 2, size.height * 0.66f), Size(bw, size.height * 0.3f), alpha = k, blendMode = BlendMode.Screen,
                        )
                    }
                    CarLights.RIPPLES -> {
                        val t = clock.longValue
                        ripples.forEach { start ->
                            val p = ((t - start) / 2000f).coerceIn(0f, 1f)
                            if (p >= 1f) return@forEach
                            val rw = size.width * (0.04f + 0.28f * p)
                            drawOval(
                                Neon.copy(alpha = 0.9f * (1f - p)),
                                Offset(size.width * 0.44f - rw, size.height * 0.80f - rw / 3f),
                                Size(rw * 2, rw * 2 / 3f), style = Stroke(2.dp.toPx()),
                            )
                        }
                    }
                    CarLights.BEAMS -> {
                        val beam = Path().apply {
                            moveTo(size.width * 0.336f, size.height * 0.49f)
                            lineTo(size.width * 0.336f, size.height * 0.53f)
                            lineTo(0f, size.height * 0.80f)
                            lineTo(0f, size.height * 0.25f)
                            close()
                        }
                        drawPath(
                            beam,
                            Brush.horizontalGradient(listOf(Color.Transparent, Cool.copy(alpha = 0.15f), Cool.copy(alpha = 0.6f)), endX = size.width * 0.336f),
                            alpha = 0.1f + 0.9f * k, blendMode = BlendMode.Screen,
                        )
                        glow(HeadR, 0.06f, 0.03f, Color.White, 0.3f + 0.7f * k)
                        glow(HeadL, 0.04f, 0.02f, Color.White, 0.3f + 0.7f * k)
                    }
                    CarLights.BOUNCE -> {
                        val dy = -0.022f * k
                        ring(WheelF.copy(y = WheelF.y + dy), 0.07f, Neon, k)
                        ring(WheelR.copy(y = WheelR.y + dy), 0.045f, Neon, k)
                    }
                    CarLights.CLUB -> {
                        val c = ClubColors[beats.intValue % ClubColors.size]
                        val x = 0.2f + ((beats.intValue * 37) % 60) / 100f
                        glow(Offset(x, 0.45f), 0.45f, 0.45f, c, 0.25f + 0.6f * k)
                        glow(HeadR, 0.08f, 0.036f, c, 0.2f + 0.8f * k)
                    }
                    CarLights.CALM -> {
                        glow(Tail, 0.06f, 0.043f, Color(0xFFFF3A2E), 0.25f + 0.75f * k)
                        glow(HeadR, 0.05f, 0.023f, Cool, 0.2f + 0.6f * h)
                    }
                    CarLights.EQ_WALL -> Unit
                }
            }
            AssistChip(
                onClick = { menu = true },
                label = { Text(stringResource(lights.label)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.45f), labelColor = Color.White),
                border = null,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            Box(Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 8.dp)) {
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    CarLights.entries.forEach { l ->
                        val locked = !isPro && l != CarLights.EQ_WALL
                        DropdownMenuItem(
                            text = { Text(stringResource(l.label) + if (locked) " · Pro" else "") },
                            onClick = {
                                menu = false
                                if (locked) onGetPro() else { lights = l; CarLights.save(context, l) }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCar(car: ImageBitmap) {
    drawImage(car, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
}

/** Soft elliptical light at [at] (fractions); [rx]/[ry] are fractions of the width. */
private fun DrawScope.glow(at: Offset, rx: Float, ry: Float, color: Color, alpha: Float, grow: Float = 1f) {
    if (alpha <= 0.01f) return
    val c = Offset(size.width * at.x, size.height * at.y)
    val r = size.width * rx * grow
    scale(scaleX = 1f, scaleY = (ry / rx).coerceAtLeast(0.01f), pivot = c) {
        drawCircle(
            Brush.radialGradient(listOf(color, color.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r),
            radius = r, center = c, alpha = alpha.coerceIn(0f, 1f), blendMode = BlendMode.Screen,
        )
    }
}

private fun DrawScope.ring(at: Offset, r: Float, color: Color, alpha: Float) {
    if (alpha <= 0.01f) return
    drawCircle(
        color, radius = size.width * r * 0.62f, center = Offset(size.width * at.x, size.height * at.y),
        alpha = alpha, style = Stroke(size.width * 0.008f), blendMode = BlendMode.Screen,
    )
}

/** Red bars rising behind the car, from the audio levels. */
private fun DrawScope.drawEqWall(levels: FloatArray, k: Float) {
    val n = 18
    val gap = size.width * 0.012f
    val bw = (size.width * 0.94f - gap * (n - 1)) / n
    val floor = size.height * 0.70f
    val per = levels.size / n
    for (i in 0 until n) {
        var v = 0f
        for (j in 0 until per) v = maxOf(v, levels.getOrElse(i * per + j) { 0f })
        val hgt = size.height * 0.58f * (0.08f + 0.92f * v.coerceIn(0f, 1f)) * (0.85f + 0.15f * k)
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFFF5A3C), Crimson), startY = floor - hgt, endY = floor),
            Offset(size.width * 0.03f + i * (bw + gap), floor - hgt), Size(bw, hgt), alpha = 0.85f, blendMode = BlendMode.Screen,
        )
    }
}

/** Rough outline of the car body, so the equalizer bars can sit behind it. */
private fun DrawScope.carOutline(): Path {
    val pts = listOf(
        0.09f to 0.53f, 0.12f to 0.47f, 0.28f to 0.39f, 0.39f to 0.31f, 0.52f to 0.295f, 0.62f to 0.30f,
        0.66f to 0.33f, 0.71f to 0.43f, 0.715f to 0.62f, 0.66f to 0.68f, 0.44f to 0.75f, 0.38f to 0.72f, 0.10f to 0.70f,
    )
    return Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(size.width * x, size.height * y) else lineTo(size.width * x, size.height * y) }
        close()
    }
}
