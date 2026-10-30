package com.vikash.mp3player.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import com.vikash.mp3player.ui.skin.SkinButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.vikash.mp3player.ui.skin.SkinOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.vikash.mp3player.R
import com.vikash.mp3player.data.LyricLine
import com.vikash.mp3player.data.LyricsRepository
import com.vikash.mp3player.data.RingtoneMaker
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.StatusVideoMaker
import com.vikash.mp3player.monetization.Ads
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.abs

private const val STATUS_BARS = 120
private const val WHATSAPP = "com.whatsapp"
private const val INSTAGRAM = "com.instagram.android"

/** Dark rose colours for the whole screen, whatever the app theme is. */
private val StatusColors = darkColorScheme(
    primary = Ampli.Rose,
    onPrimary = Color.White,
    secondaryContainer = Ampli.RoseDeep,
    onSecondaryContainer = Color.White,
    background = Ampli.Night,
    onBackground = Color.White,
    surface = Ampli.Night,
    onSurface = Color.White,
    surfaceVariant = Ampli.NightRaised,
    onSurfaceVariant = Color(0xFFD9C3CC),
    outline = Color(0xFF6B5560),
)

/**
 * WhatsApp / Instagram Status maker: pick up to 30 seconds of [song], see a 9:16 preview with a
 * spinning record (and the lyric line, if the user has lyrics), then make an MP4 and share it.
 * Free videos carry a small "Made with Ampli" mark; Pro, or one HD video unlocked by a rewarded
 * ad, has no mark and is 1080p.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusMakerScreen(song: Song, isPro: Boolean, onGetPro: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val maker = remember { RingtoneMaker(context.applicationContext) }
    var job by remember { mutableStateOf<Job?>(null) }
    BackHandler {
        if (job != null) job?.cancel() else onClose()
    }

    val duration = song.durationMs.coerceAtLeast(StatusVideoMaker.MIN_CLIP_MS)
    val maxLen = StatusVideoMaker.MAX_CLIP_MS
    val minLen = StatusVideoMaker.MIN_CLIP_MS
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(minOf(maxLen, duration)) }
    var waveform by remember { mutableStateOf<FloatArray?>(null) }
    var lyrics by remember { mutableStateOf<List<LyricLine>?>(null) }

    fun setStart(ms: Long) {
        val s = ms.coerceIn(0L, duration - minLen)
        startMs = s
        if (endMs - s > maxLen) endMs = s + maxLen
        if (endMs - s < minLen) endMs = s + minLen
    }
    fun setEnd(ms: Long) {
        val e = ms.coerceIn(minLen, duration)
        endMs = e
        if (e - startMs > maxLen) startMs = e - maxLen
        if (e - startMs < minLen) startMs = e - minLen
    }

    LaunchedEffect(song.uri) {
        val seed = song.id.hashCode()
        waveform = FloatArray(STATUS_BARS) { 0.3f + 0.5f * abs(((it * 7919 + seed) % 97) / 97f) }
        runCatching { maker.waveform(song.uri, STATUS_BARS) { waveform = it } }.onSuccess { waveform = it }
    }
    LaunchedEffect(song.id) {
        lyrics = runCatching { LyricsRepository.load(context.applicationContext, song.id) }.getOrNull()
            ?.takeIf { it.synced }?.lines
    }

    // Play the selection with a small player of our own, so the main queue isn't touched.
    val preview = remember {
        ExoPlayer.Builder(context).setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        ).build().apply {
            setMediaItem(MediaItem.fromUri(song.uri))
            prepare()
        }
    }
    DisposableEffect(preview) { onDispose { preview.release() } }
    var previewing by remember { mutableStateOf(false) }
    var playhead by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(previewing) {
        if (!previewing) { preview.pause(); playhead = -1; return@LaunchedEffect }
        preview.seekTo(startMs)
        preview.play()
        while (previewing) {
            val pos = preview.currentPosition
            playhead = pos
            if (pos >= endMs || !preview.playWhenReady) previewing = false
            delay(40)
        }
    }

    // Quality: 720p for everyone; 1080p for Pro, or once after watching a rewarded ad.
    var wantHd by remember { mutableStateOf(false) }
    var hdUnlocked by remember { mutableStateOf(false) }
    val showAdChip = Ads.rewardedConfigured && !isPro
    val useHd = wantHd && (isPro || hdUnlocked)
    val watermark = !(isPro || useHd)

    var progress by remember { mutableFloatStateOf(0f) }
    val shownProgress by animateFloatAsState(progress, tween(250), label = "statusProgress")
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<File?>(null) }
    val making = job != null

    val markText = stringResource(R.string.status_watermark)
    val clipLen = endMs - startMs

    MaterialTheme(colorScheme = StatusColors) {
        Surface(Modifier.fillMaxSize(), color = Ampli.Night, contentColor = Color.White) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { job?.cancel(); onClose() }) {
                        Icon(Icons.Default.Close, stringResource(R.string.status_close))
                    }
                    Text(
                        stringResource(R.string.status_title, song.title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(end = 48.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))

                StatusPreviewCard(
                    title = song.title,
                    artist = song.artist,
                    lyric = lyricAt(lyrics, if (playhead >= 0) playhead else startMs),
                    watermark = if (watermark) markText else null,
                )
                Spacer(Modifier.height(20.dp))

                Waveform(
                    bars = waveform,
                    startFraction = startMs.toFloat() / duration,
                    endFraction = endMs.toFloat() / duration,
                    playheadFraction = if (playhead >= 0) playhead.toFloat() / duration else null,
                    onMoveStart = { setStart((it * duration).toLong()) },
                    onMoveEnd = { setEnd((it * duration).toLong()) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val small = MaterialTheme.typography.bodySmall
                    val dim = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(stringResource(R.string.status_start, mss(startMs)), style = small, color = dim)
                    Text(
                        stringResource(R.string.status_length, ((clipLen + 500) / 1000).toInt()),
                        style = small, color = Ampli.Blush, fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.status_end, mss(endMs)), style = small, color = dim)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GradientPlayButton(previewing, 48.dp) { previewing = !previewing }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(if (previewing) R.string.status_previewing else R.string.status_preview))
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.status_quality),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useHd,
                        onClick = { wantHd = false },
                        enabled = !making,
                        label = { Text(stringResource(R.string.status_q_720)) },
                    )
                    FilterChip(
                        selected = useHd && isPro,
                        onClick = { if (isPro) wantHd = true else onGetPro() },
                        enabled = !making,
                        label = { Text(stringResource(R.string.status_q_1080_pro)) },
                    )
                    if (showAdChip) {
                        FilterChip(
                            selected = useHd && hdUnlocked,
                            enabled = !making,
                            onClick = {
                                if (hdUnlocked) {
                                    wantHd = true
                                } else {
                                    val activity = context.findActivity()
                                    if (activity == null) {
                                        Toast.makeText(context, R.string.status_ad_unavailable, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Ads.showRewarded(
                                            activity,
                                            onReward = {
                                                hdUnlocked = true
                                                wantHd = true
                                                Toast.makeText(context, R.string.status_hd_unlocked, Toast.LENGTH_SHORT).show()
                                            },
                                            onUnavailable = {
                                                Toast.makeText(context, R.string.status_ad_unavailable, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.status_q_hd_ad)) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.status_rights),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                if (making) {
                    LinearProgressIndicator(
                        progress = { shownProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Ampli.Rose,
                        trackColor = Ampli.NightRaised,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.status_making, (shownProgress * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.status_cancel)) }
                    }
                } else {
                    Button(
                        onClick = {
                            previewing = false
                            error = null
                            result = null
                            progress = 0f
                            val hd = useHd
                            val adUnlock = hd && !isPro
                            val look = StatusVideoMaker.Look(
                                title = song.title,
                                artist = song.artist,
                                lyrics = lyrics,
                                watermark = if (watermark) markText else null,
                            )
                            val quality = if (hd) StatusVideoMaker.Quality.HD1080 else StatusVideoMaker.Quality.HD720
                            val s = startMs
                            val e = endMs
                            job = scope.launch {
                                try {
                                    val file = StatusVideoMaker.make(context.applicationContext, song, s, e, quality, look) { p ->
                                        scope.launch { progress = p }
                                    }
                                    result = file
                                    if (adUnlock) { hdUnlocked = false; wantHd = false }
                                    shareStatus(context, file, null)
                                } catch (c: CancellationException) {
                                    // Cancelled by the user: nothing to report.
                                } catch (t: Throwable) {
                                    error = context.getString(R.string.status_error)
                                } finally {
                                    job = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ampli.Rose, contentColor = Color.White),
                    ) { Text(stringResource(R.string.status_make), fontWeight = FontWeight.SemiBold) }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                AnimatedVisibility(
                    visible = result != null && !making,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    val file = result
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.status_ready), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { if (file != null) shareStatus(context, file, WHATSAPP) },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.status_share_whatsapp), maxLines = 1) }
                            OutlinedButton(
                                onClick = { if (file != null) shareStatus(context, file, INSTAGRAM) },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.status_share_instagram), maxLines = 1) }
                            OutlinedButton(
                                onClick = { if (file != null) shareStatus(context, file, null) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.status_share_more), maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** The 9:16 card: what the video will look like. The record turns at the video's speed. */
@Composable
private fun StatusPreviewCard(title: String, artist: String, lyric: String?, watermark: String?) {
    // 120 degrees per second, like the made video. Lite mode shows the record still.
    val angleState: State<Float> = if (LocalLiteMode.current) remember { mutableFloatStateOf(0f) } else
        rememberInfiniteTransition(label = "statusSpin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
            label = "statusAngle",
        )
    val angle by angleState
    val description = stringResource(R.string.status_preview_desc)
    Box(
        Modifier.fillMaxWidth(0.62f).widthIn(max = 280.dp).aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3B0A1E), Ampli.NightRaised, Ampli.Night)))
            .semantics { contentDescription = description },
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.12f))
            Canvas(
                Modifier.fillMaxWidth(0.72f).aspectRatio(1f).graphicsLayer { rotationZ = angle },
            ) {
                val r = size.minDimension / 2
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Brush.radialGradient(listOf(Ampli.Rose.copy(alpha = 0.35f), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
                drawCircle(Color(0xFF0B0B0E), r, c)
                var g = r * 0.97f
                var k = 0
                while (g > r * 0.38f) {
                    drawCircle(Color.White.copy(alpha = if (k % 3 == 0) 0.19f else 0.09f), g, c, style = Stroke(1f))
                    g -= r * 0.035f
                    k++
                }
                drawCircle(Brush.verticalGradient(listOf(Ampli.Rose, Ampli.RoseDeep), c.y - r * 0.34f, c.y + r * 0.34f), r * 0.34f, c)
                drawCircle(Ampli.Night, r * 0.05f, c)
                drawCircle(Color.White, r * 0.025f, Offset(c.x, c.y + r * 0.2f))
                drawCircle(Color.White.copy(alpha = 0.8f), r * 0.03f, Offset(c.x, c.y - r * 0.18f))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (artist.isNotBlank()) {
                Text(
                    artist, color = Ampli.Blush, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Spacer(Modifier.weight(0.1f))
            Text(
                lyric.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(54.dp),
            )
            Spacer(Modifier.weight(0.12f))
        }
        AnimatedVisibility(
            visible = watermark != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(Ampli.Rose))
                Spacer(Modifier.width(4.dp))
                Text(watermark.orEmpty(), color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The synced lyric line playing at [ms], if any. */
private fun lyricAt(lines: List<LyricLine>?, ms: Long): String? {
    if (lines.isNullOrEmpty()) return null
    var found: String? = null
    for (l in lines) {
        val t = l.timeMs ?: continue
        if (t <= ms) found = l.text else break
    }
    return found?.takeIf { it.isNotBlank() }
}

private fun mss(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Shares the MP4 through our FileProvider. With [pkg] (WhatsApp, Instagram) it opens that app
 * directly, and falls back to the share sheet when it isn't installed.
 */
private fun shareStatus(context: Context, file: File, pkg: String?) {
    val uri = runCatching {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }.getOrElse {
        Toast.makeText(context, R.string.status_no_app, Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (pkg != null) {
        try {
            context.startActivity(Intent(send).setPackage(pkg))
            return
        } catch (e: ActivityNotFoundException) {
            // Not installed: use the share sheet.
        } catch (e: SecurityException) {
            // Can't open it directly: use the share sheet.
        }
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.status_share_title))
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
        .onFailure { Toast.makeText(context, R.string.status_no_app, Toast.LENGTH_SHORT).show() }
}
