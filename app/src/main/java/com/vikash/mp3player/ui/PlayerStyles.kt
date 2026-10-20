package com.vikash.mp3player.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Lyrics
import com.vikash.mp3player.data.LyricsRepository
import com.vikash.mp3player.data.PlayerStyle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The big picture at the top of Now Playing, in the style the user picked. Every style moves only
 * while music plays (and not in Lite mode), and none of them is ever woken while the screen is off.
 */
@Composable
fun PlayerPicture(
    style: PlayerStyle,
    artworkUri: Uri?,
    songId: String,
    title: String,
    artist: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    intro: () -> Float = { 1f },
) {
    val progress = { positionMs.toFloat() / durationMs.coerceAtLeast(1) }
    when (style) {
        PlayerStyle.Record -> RecordPlayerView(artworkUri, title, artist, isPlaying, progress, modifier, intro)
        PlayerStyle.Cassette -> CassetteView(artworkUri, title, artist, isPlaying, progress, modifier)
        PlayerStyle.Boombox -> BoomboxView(artworkUri, title, isPlaying, modifier)
        PlayerStyle.Jukebox -> JukeboxView(artworkUri, title, artist, isPlaying, modifier)
        PlayerStyle.ReelToReel -> ReelToReelView(artworkUri, isPlaying, progress, modifier)
        PlayerStyle.Neon -> NeonRingsView(artworkUri, isPlaying, modifier)
        PlayerStyle.Lyrics -> LyricsFirstView(songId, positionMs, onSeek, modifier) {
            // No timed lyrics for this song: show the record player instead.
            RecordPlayerView(artworkUri, title, artist, isPlaying, progress, Modifier.fillMaxSize(), intro)
        }
        PlayerStyle.Radio -> OldRadioView(artworkUri, title, artist, isPlaying, progress, modifier)
        PlayerStyle.ClickWheel -> ClickWheelView(
            artworkUri, title, artist, isPlaying, positionMs, durationMs, onSeek, onTogglePlay, onNext, onPrevious, modifier,
        )
    }
}

/** Seconds of music played while this view is open; stands still on pause and in Lite mode. */
@Composable
private fun rememberPlayClock(isPlaying: Boolean): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    val lite = LocalLiteMode.current
    LaunchedEffect(isPlaying, lite) {
        if (!isPlaying || lite) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t.floatValue += ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
            }
        }
    }
    return t
}

/** A soft 0..1 pulse at about 120 beats a minute. */
private fun beat(t: Float, phase: Float = 0f) = 0.5f + 0.5f * sin((t * 2f + phase) * 2f * PI.toFloat())

private val ArtFallback = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFE11D48), Color(0xFF4C1D95)))

private fun DrawScope.drawArt(art: Painter?, topLeft: Offset, sz: Size, clip: Path) {
    clipPath(clip) {
        if (art != null) translate(topLeft.x, topLeft.y) { with(art) { draw(sz) } }
        else drawRect(ArtFallback, topLeft, sz)
    }
}

private fun roundRect(topLeft: Offset, sz: Size, r: Float) = Path().apply { addRoundRect(RoundRect(Rect(topLeft, sz), CornerRadius(r))) }
private fun circle(c: Offset, r: Float) = Path().apply { addOval(Rect(c, r)) }

private fun DrawScope.label(
    text: TextMeasurer, s: String, topLeft: Offset, width: Float, sizeSp: Int, color: Color,
    bold: Boolean = false, center: Boolean = false,
) {
    if (s.isEmpty() || width <= 0f) return
    drawText(
        text, s, topLeft,
        TextStyle(
            color = color, fontSize = sizeSp.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        ),
        overflow = TextOverflow.Ellipsis, maxLines = 1, size = Size(width, sizeSp.sp.toPx() * 1.6f),
    )
}

/** A reel or cassette hub: [teeth] spokes turned by [angle]. */
private fun DrawScope.hub(c: Offset, r: Float, angle: Float, color: Color, teeth: Int = 6) {
    drawCircle(color, r, c, style = Stroke(r * 0.22f))
    rotate(angle, c) {
        for (i in 0 until teeth) {
            val a = (i * 360f / teeth) * PI.toFloat() / 180f
            drawLine(color, c + Offset(cos(a), sin(a)) * (r * 0.35f), c + Offset(cos(a), sin(a)) * (r * 0.85f), r * 0.16f, StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- Cassette

@Composable
private fun CassetteView(uri: Uri?, title: String, artist: String, isPlaying: Boolean, progress: () -> Float, modifier: Modifier) {
    val art = rememberStickerPainter(uri, 320)
    val spin = LocalDiscAngle.current
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        val w = min(size.width * 0.94f, size.height / 0.64f)
        val h = w * 0.64f
        val o = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        drawRoundRect(Color(0xFF2B2230), o, Size(w, h), CornerRadius(w * 0.05f))
        drawRoundRect(Color(0xFF3A2F40), o + Offset(w * 0.03f, h * 0.04f), Size(w * 0.94f, h * 0.92f), CornerRadius(w * 0.04f))
        // Label: the cover on the left, the song on the right.
        val lo = o + Offset(w * 0.07f, h * 0.08f)
        val ls = Size(w * 0.86f, h * 0.36f)
        drawRoundRect(Color(0xFFFFF7ED), lo, ls, CornerRadius(w * 0.02f))
        val artSide = ls.height * 0.84f
        val ao = lo + Offset(ls.height * 0.08f, ls.height * 0.08f)
        drawArt(art, ao, Size(artSide, artSide), roundRect(ao, Size(artSide, artSide), w * 0.015f))
        val tx = ao.x + artSide + w * 0.03f
        val tw = lo.x + ls.width - tx - w * 0.02f
        label(text, title, Offset(tx, lo.y + ls.height * 0.14f), tw, 15, Color(0xFF1C1622), bold = true)
        label(text, artist, Offset(tx, lo.y + ls.height * 0.52f), tw, 12, Color(0xFF6B6375))
        // Window with the two reels; tape moves from the left reel to the right one.
        val wo = o + Offset(w * 0.22f, h * 0.52f)
        val ws = Size(w * 0.56f, h * 0.28f)
        drawRoundRect(Color(0xFF120C16), wo, ws, CornerRadius(ws.height / 2f))
        val p = progress().coerceIn(0f, 1f)
        val maxR = ws.height * 0.46f
        val lc = Offset(wo.x + ws.height * 0.55f, wo.y + ws.height / 2f)
        val rc = Offset(wo.x + ws.width - ws.height * 0.55f, wo.y + ws.height / 2f)
        drawCircle(Color(0xFF5B4636), maxR * (0.45f + 0.55f * (1f - p)), lc)
        drawCircle(Color(0xFF5B4636), maxR * (0.45f + 0.55f * p), rc)
        val a = if (isPlaying) spin.value * 2f else spin.value
        hub(lc, maxR * 0.42f, a, Color(0xFFF5F2F6))
        hub(rc, maxR * 0.42f, a, Color(0xFFF5F2F6))
        // Screws and the bottom trapezoid.
        listOf(Offset(0.05f, 0.08f), Offset(0.95f, 0.08f), Offset(0.05f, 0.92f), Offset(0.95f, 0.92f)).forEach {
            drawCircle(Color(0xFF6B5F70), w * 0.012f, o + Offset(it.x * w, it.y * h))
        }
        val trap = Path().apply {
            moveTo(o.x + w * 0.25f, o.y + h); lineTo(o.x + w * 0.3f, o.y + h * 0.86f)
            lineTo(o.x + w * 0.7f, o.y + h * 0.86f); lineTo(o.x + w * 0.75f, o.y + h); close()
        }
        drawPath(trap, Color(0xFF1F1823))
    }
}

// ---------------------------------------------------------------- Boombox

@Composable
private fun BoomboxView(uri: Uri?, title: String, isPlaying: Boolean, modifier: Modifier) {
    val art = rememberStickerPainter(uri, 320)
    val clock = rememberPlayClock(isPlaying)
    val text = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = min(size.width * 0.96f, size.height / 0.7f)
        val h = w * 0.62f
        val o = Offset((size.width - w) / 2f, (size.height - h) / 2f + h * 0.06f)
        // Handle.
        drawRoundRect(Color(0xFF374151), o + Offset(w * 0.25f, -h * 0.14f), Size(w * 0.5f, h * 0.18f), CornerRadius(h * 0.09f), style = Stroke(h * 0.05f))
        drawRoundRect(Color(0xFF1F2937), o, Size(w, h), CornerRadius(w * 0.06f))
        val t = clock.value
        val sr = h * 0.33f
        listOf(Offset(o.x + w * 0.2f, o.y + h * 0.55f) to 0f, Offset(o.x + w * 0.8f, o.y + h * 0.55f) to 0.25f).forEach { (c, ph) ->
            val k = 1f + 0.06f * beat(t, ph)
            drawCircle(Color(0xFF4B5563), sr * 1.06f, c)
            drawCircle(Color(0xFF111111), sr * k, c)
            drawCircle(Color(0xFF374151), sr * 0.62f * k, c)
            drawCircle(Color(0xFF111111), sr * 0.5f * k, c)
            drawCircle(accent.copy(alpha = 0.9f), sr * 0.14f * k, c)
        }
        // Middle display: the cover with the song name and a little level meter.
        val ds = Size(w * 0.24f, h * 0.5f)
        val dob = Offset(o.x + (w - ds.width) / 2f, o.y + h * 0.14f)
        drawRoundRect(Color(0xFF0B1220), dob, ds, CornerRadius(w * 0.015f))
        val side = ds.width * 0.82f
        val ao = Offset(dob.x + (ds.width - side) / 2f, dob.y + ds.width * 0.09f)
        drawArt(art, ao, Size(side, side), roundRect(ao, Size(side, side), w * 0.01f))
        label(text, title, Offset(dob.x + w * 0.01f, ao.y + side + h * 0.02f), ds.width - w * 0.02f, 10, Color(0xFFE5E7EB), center = true)
        for (i in 0 until 8) {
            val bh = h * 0.12f * (0.25f + 0.75f * beat(t * 1.3f, i * 0.17f))
            drawRect(accent, Offset(dob.x + w * 0.012f + i * ds.width / 8.3f, o.y + h * 0.9f - bh), Size(ds.width / 12f, bh))
        }
        // Buttons along the top.
        for (i in 0 until 4) drawRoundRect(Color(0xFF9CA3AF), o + Offset(w * (0.36f + i * 0.08f), -h * 0.025f), Size(w * 0.05f, h * 0.05f), CornerRadius(4f))
    }
}

// ---------------------------------------------------------------- Jukebox

@Composable
private fun JukeboxView(uri: Uri?, title: String, artist: String, isPlaying: Boolean, modifier: Modifier) {
    val art = rememberStickerPainter(uri, 320)
    val clock = rememberPlayClock(isPlaying)
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        val h = min(size.height * 0.96f, size.width / 0.72f)
        val w = h * 0.72f
        val o = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        val r = w / 2f
        val body = Path().apply {
            moveTo(o.x, o.y + r); arcTo(Rect(o, Size(w, w)), 180f, 180f, false)
            lineTo(o.x + w, o.y + h); lineTo(o.x, o.y + h); close()
        }
        drawPath(body, Brush.verticalGradient(listOf(Color(0xFFF59E0B), Color(0xFFB45309)), o.y, o.y + h))
        val t = clock.value
        // Light tubes round the arch, hue walking while it plays.
        for (i in 0 until 3) {
            val inset = w * (0.04f + i * 0.035f)
            val hue = ((t * 40f + i * 50f) % 360f)
            drawArc(
                Color.hsv(hue, 0.7f, 1f), 180f, 180f, false, o + Offset(inset, inset), Size(w - 2 * inset, w - 2 * inset),
                style = Stroke(w * 0.018f),
            )
        }
        // Window with bubbles.
        val wi = w * 0.16f
        val win = Path().apply {
            val wr = Rect(o + Offset(wi, wi), Size(w - 2 * wi, w - 2 * wi))
            moveTo(wr.left, wr.center.y); arcTo(wr, 180f, 180f, false)
            lineTo(wr.right, o.y + h * 0.56f); lineTo(wr.left, o.y + h * 0.56f); close()
        }
        drawPath(win, Color(0xFF1C1917))
        clipPath(win) {
            for (i in 0 until 9) {
                val x = o.x + wi + (w - 2 * wi) * ((i * 0.37f) % 1f)
                val y = o.y + h * 0.56f - ((t * 40f * density + i * 47f * density) % (h * 0.5f))
                drawCircle(Color(0xFFFDE68A).copy(alpha = 0.7f), w * 0.018f, Offset(x, y))
            }
        }
        // The record slot: cover and song.
        val so = o + Offset(w * 0.12f, h * 0.6f)
        val ss = Size(w * 0.76f, h * 0.22f)
        drawRoundRect(Color(0xFF1C1917), so, ss, CornerRadius(w * 0.03f))
        val side = ss.height * 0.8f
        val ao = so + Offset(ss.height * 0.1f, ss.height * 0.1f)
        drawArt(art, ao, Size(side, side), roundRect(ao, Size(side, side), w * 0.02f))
        val tx = ao.x + side + w * 0.03f
        label(text, title, Offset(tx, so.y + ss.height * 0.18f), so.x + ss.width - tx - w * 0.03f, 14, Color(0xFFFDE68A), bold = true)
        label(text, artist, Offset(tx, so.y + ss.height * 0.55f), so.x + ss.width - tx - w * 0.03f, 11, Color(0xFFE7E5E4))
        // Grille.
        for (i in 0 until 4) drawLine(Color(0xFF78350F), o + Offset(w * 0.18f, h * (0.88f + i * 0.03f)), o + Offset(w * 0.82f, h * (0.88f + i * 0.03f)), 3f)
    }
}

// ---------------------------------------------------------------- Reel to reel

@Composable
private fun ReelToReelView(uri: Uri?, isPlaying: Boolean, progress: () -> Float, modifier: Modifier) {
    val art = rememberStickerPainter(uri, 240)
    val spin = LocalDiscAngle.current
    val clock = rememberPlayClock(isPlaying)
    Canvas(modifier) {
        val w = min(size.width * 0.96f, size.height / 0.8f)
        val h = w * 0.8f
        val o = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE7E5E4), Color(0xFFA8A29E)), o.y, o.y + h), o, Size(w, h), CornerRadius(w * 0.04f))
        val p = progress().coerceIn(0f, 1f)
        val rr = w * 0.21f
        val lc = o + Offset(w * 0.27f, h * 0.32f)
        val rc = o + Offset(w * 0.73f, h * 0.32f)
        listOf(lc to (1f - p), rc to p).forEach { (c, amount) ->
            drawCircle(Color(0xFF292524), rr * (0.42f + 0.5f * amount), c)
            drawCircle(Color(0xFF78716C), rr, c, style = Stroke(w * 0.008f))
            rotate(spin.value * 1.4f, c) {
                for (k in 0 until 3) {
                    val a = k * 120f * PI.toFloat() / 180f
                    drawCircle(Color(0xFFD6D3D1), rr * 0.2f, c + Offset(cos(a), sin(a)) * (rr * 0.55f))
                }
            }
            drawCircle(Color(0xFF57534E), rr * 0.16f, c)
        }
        // Tape path past the head.
        val head = o + Offset(w * 0.5f, h * 0.66f)
        drawLine(Color(0xFF292524), lc + Offset(-rr * 0.2f, rr * 0.9f), head + Offset(-w * 0.08f, 0f), 3f)
        drawLine(Color(0xFF292524), rc + Offset(rr * 0.2f, rr * 0.9f), head + Offset(w * 0.08f, 0f), 3f)
        val hs = Size(w * 0.2f, h * 0.12f)
        val ho = head - Offset(hs.width / 2f, hs.height * 0.2f)
        drawArt(art, ho, hs, roundRect(ho, hs, w * 0.015f))
        // VU meters.
        val t = clock.value
        listOf(0.18f, 0.62f).forEachIndexed { i, fx ->
            val mo = o + Offset(w * fx, h * 0.8f)
            val ms = Size(w * 0.2f, h * 0.14f)
            drawRoundRect(Color(0xFFFEF3C7), mo, ms, CornerRadius(w * 0.01f))
            val level = if (isPlaying) 0.3f + 0.6f * beat(t * 1.7f, i * 0.3f) else 0f
            val pivot = mo + Offset(ms.width / 2f, ms.height * 0.95f)
            val ang = (-130f + 80f * level) * PI.toFloat() / 180f
            drawLine(Color(0xFFB91C1C), pivot, pivot + Offset(cos(ang), sin(ang)) * (ms.height * 0.8f), 3f, StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- Neon rings

@Composable
private fun NeonRingsView(uri: Uri?, isPlaying: Boolean, modifier: Modifier) {
    val art = rememberStickerPainter(uri, 512)
    val spin = LocalDiscAngle.current
    val clock = rememberPlayClock(isPlaying)
    Canvas(modifier) {
        val c = center
        val r = min(size.width, size.height) * 0.28f
        val t = clock.value
        val rings = listOf(Color(0xFF22D3EE) to 0f, Color(0xFFA855F7) to 0.5f, Color(0xFFF472B6) to 0.25f)
        rings.forEachIndexed { i, (col, ph) ->
            val rad = r * (1.18f + i * 0.2f) + r * 0.05f * beat(t, ph)
            for (g in 4 downTo 1) drawCircle(col.copy(alpha = 0.08f * (5 - g)), rad, c, style = Stroke(g * 6f))
            drawCircle(col, rad, c, style = Stroke(3f))
        }
        rotate(spin.value * 0.3f, c) { drawArt(art, c - Offset(r, r), Size(2 * r, 2 * r), circle(c, r)) }
    }
}

// ---------------------------------------------------------------- Lyrics first

/** Big karaoke lines when the song has timed lyrics; [fallback] otherwise. Tap a line to jump to it. */
@Composable
private fun LyricsFirstView(songId: String, positionMs: Long, onSeek: (Long) -> Unit, modifier: Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    var lyrics by remember(songId) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(songId) { lyrics = runCatching { LyricsRepository.load(context, songId) }.getOrNull() }
    val timed = lyrics?.takeIf { it.synced }?.lines?.filter { it.timeMs != null && it.text.isNotBlank() }.orEmpty()
    Box(modifier, contentAlignment = Alignment.Center) {
        if (timed.isEmpty()) {
            fallback()
            return@Box
        }
        val idx = timed.indexOfLast { (it.timeMs ?: 0L) <= positionMs }.coerceAtLeast(0)
        AnimatedContent(
            targetState = idx,
            transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut()) },
            label = "lyric",
        ) { i ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                timed.getOrNull(i - 1)?.let { LyricLineText(it.text, false) { onSeek(it.timeMs ?: 0L) } }
                LyricLineText(timed[i].text, true) { onSeek(timed[i].timeMs ?: 0L) }
                timed.getOrNull(i + 1)?.let { LyricLineText(it.text, false) { onSeek(it.timeMs ?: 0L) } }
                timed.getOrNull(i + 2)?.let { LyricLineText(it.text, false) { onSeek(it.timeMs ?: 0L) } }
            }
        }
    }
}

@Composable
private fun LyricLineText(text: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clickable(onClick = onClick),
        textAlign = TextAlign.Center,
        style = if (current) MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
            else MaterialTheme.typography.titleMedium,
        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

// ---------------------------------------------------------------- Old radio

@Composable
private fun OldRadioView(uri: Uri?, title: String, artist: String, isPlaying: Boolean, progress: () -> Float, modifier: Modifier) {
    val art = rememberStickerPainter(uri, 320)
    val spin = LocalDiscAngle.current
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        val w = min(size.width * 0.96f, size.height / 0.78f)
        val h = w * 0.72f
        val o = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF9A3412), Color(0xFF5B2108)), o.y, o.y + h), o, Size(w, h), CornerRadius(w * 0.07f))
        drawRoundRect(Color(0xFF7C2D12), o + Offset(w * 0.03f, h * 0.04f), Size(w * 0.94f, h * 0.92f), CornerRadius(w * 0.05f), style = Stroke(w * 0.01f))
        // Tuning dial: the needle walks across it as the song plays.
        val dO = o + Offset(w * 0.08f, h * 0.1f)
        val dS = Size(w * 0.84f, h * 0.24f)
        drawRoundRect(Color(0xFFFEF3C7), dO, dS, CornerRadius(w * 0.02f))
        for (i in 0..20) {
            val x = dO.x + dS.width * (0.05f + 0.9f * i / 20f)
            drawLine(Color(0xFF7C2D12), Offset(x, dO.y + dS.height * 0.62f), Offset(x, dO.y + dS.height * (if (i % 5 == 0) 0.9f else 0.78f)), 2f)
        }
        label(text, title, dO + Offset(dS.width * 0.05f, dS.height * 0.08f), dS.width * 0.9f, 13, Color(0xFF7C2D12), bold = true, center = true)
        val nx = dO.x + dS.width * (0.05f + 0.9f * progress().coerceIn(0f, 1f))
        drawLine(Color(0xFFDC2626), Offset(nx, dO.y + dS.height * 0.05f), Offset(nx, dO.y + dS.height * 0.95f), 4f, StrokeCap.Round)
        // Speaker grille on the left, cover badge on the right.
        val gO = o + Offset(w * 0.08f, h * 0.4f)
        val gS = Size(w * 0.5f, h * 0.48f)
        drawRoundRect(Color(0xFF451A03), gO, gS, CornerRadius(w * 0.02f))
        for (i in 1 until 9) drawLine(Color(0xFFB45309), Offset(gO.x + w * 0.02f, gO.y + gS.height * i / 9f), Offset(gO.x + gS.width - w * 0.02f, gO.y + gS.height * i / 9f), 3f)
        val bc = o + Offset(w * 0.78f, h * 0.55f)
        val br = w * 0.12f
        drawArt(art, bc - Offset(br, br), Size(2 * br, 2 * br), circle(bc, br))
        drawCircle(Color(0xFFFBBF24), br, bc, style = Stroke(w * 0.012f))
        label(text, artist, Offset(o.x + w * 0.62f, bc.y + br + h * 0.02f), w * 0.32f, 10, Color(0xFFFDE68A), center = true)
        // Two knobs, turning slowly while it plays.
        listOf(0.18f, 0.48f).forEach { fx ->
            val kc = o + Offset(w * fx, h * 0.94f)
            drawCircle(Color(0xFFFBBF24), w * 0.035f, kc)
            rotate(if (isPlaying) spin.value * 0.5f else 0f, kc) { drawLine(Color(0xFF78350F), kc, kc - Offset(0f, w * 0.03f), 4f, StrokeCap.Round) }
        }
    }
}

// ---------------------------------------------------------------- Click wheel

/**
 * A classic pocket player. Slide a finger round the wheel to seek (one turn = 30 seconds); tap the
 * bottom for play/pause, the sides for previous/next, the centre for play/pause too.
 */
@Composable
private fun ClickWheelView(
    uri: Uri?, title: String, artist: String, isPlaying: Boolean, positionMs: Long, durationMs: Long,
    onSeek: (Long) -> Unit, onTogglePlay: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, modifier: Modifier,
) {
    val art = rememberStickerPainter(uri, 320)
    val text = rememberTextMeasurer()
    val pos by rememberUpdatedState(positionMs)
    val dur by rememberUpdatedState(durationMs)
    val accent = MaterialTheme.colorScheme.primary
    var geo by remember { mutableStateOf(Triple(Offset.Zero, 0f, 0f)) } // wheel centre, wheel radius, button radius
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val (c, r, inner) = geo
                    val d = p - c
                    val dist = d.getDistance()
                    when {
                        dist <= inner -> onTogglePlay()
                        dist <= r -> when {
                            d.y > kotlin.math.abs(d.x) -> onTogglePlay()
                            d.x < -kotlin.math.abs(d.y) -> onPrevious()
                            d.x > kotlin.math.abs(d.y) -> onNext()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                var target = 0L
                detectDragGestures(
                    onDragStart = { target = pos },
                    onDragEnd = { onSeek(target.coerceIn(0L, dur.coerceAtLeast(0L))) },
                ) { change, drag ->
                    val c = geo.first
                    val now = change.position - c
                    val before = now - drag
                    var delta = atan2(now.y, now.x) - atan2(before.y, before.x)
                    if (delta > PI) delta -= (2 * PI).toFloat()
                    if (delta < -PI) delta += (2 * PI).toFloat()
                    target += (delta / (2 * PI) * 30_000).toLong()
                    change.consume()
                }
            },
    ) {
        val h = min(size.height * 0.98f, size.width / 0.62f)
        val w = h * 0.62f
        val o = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1)), o.y, o.y + h), o, Size(w, h), CornerRadius(w * 0.12f))
        // Screen.
        val so = o + Offset(w * 0.08f, h * 0.05f)
        val ss = Size(w * 0.84f, h * 0.4f)
        drawRoundRect(Color(0xFF0F172A), so, ss, CornerRadius(w * 0.03f))
        val side = ss.height * 0.56f
        val ao = so + Offset((ss.width - side) / 2f, ss.height * 0.07f)
        drawArt(art, ao, Size(side, side), roundRect(ao, Size(side, side), w * 0.02f))
        label(text, title, Offset(so.x + w * 0.04f, ao.y + side + ss.height * 0.03f), ss.width - w * 0.08f, 13, Color.White, bold = true, center = true)
        label(text, artist, Offset(so.x + w * 0.04f, ao.y + side + ss.height * 0.15f), ss.width - w * 0.08f, 11, Color(0xFF94A3B8), center = true)
        val barY = so.y + ss.height * 0.92f
        drawLine(Color(0xFF334155), Offset(so.x + w * 0.06f, barY), Offset(so.x + ss.width - w * 0.06f, barY), 5f, StrokeCap.Round)
        val p = (positionMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        drawLine(accent, Offset(so.x + w * 0.06f, barY), Offset(so.x + w * 0.06f + (ss.width - w * 0.12f) * p, barY), 5f, StrokeCap.Round)
        // Wheel.
        val wc = o + Offset(w / 2f, h * 0.72f)
        val wr = w * 0.36f
        val inner = wr * 0.38f
        geo = Triple(wc, wr, inner)
        drawCircle(Color.White, wr, wc)
        drawCircle(Color(0xFFCBD5E1), wr, wc, style = Stroke(2f))
        drawCircle(Color(0xFFE2E8F0), inner, wc)
        val ink = Color(0xFF64748B)
        label(text, "MENU", wc + Offset(-wr * 0.5f, -wr * 0.86f), wr, 11, ink, bold = true, center = true)
        label(text, "⏮", wc + Offset(-wr * 0.96f, -wr * 0.12f), wr * 0.4f, 14, ink, center = true)
        label(text, "⏭", wc + Offset(wr * 0.56f, -wr * 0.12f), wr * 0.4f, 14, ink, center = true)
        label(text, if (isPlaying) "⏸" else "▶", wc + Offset(-wr * 0.2f, wr * 0.6f), wr * 0.4f, 14, ink, center = true)
    }
}
