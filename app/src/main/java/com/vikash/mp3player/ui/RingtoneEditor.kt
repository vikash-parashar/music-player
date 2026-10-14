package com.vikash.mp3player.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.vikash.mp3player.data.RingtoneMaker
import com.vikash.mp3player.data.RingtoneTarget
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private const val BARS = 120
private const val MIN_CLIP_MS = 1_000L
private const val NUDGE_MS = 100L

/**
 * Full-screen ringtone maker: pick a section of [song] with two handles, preview it, then save
 * it and set it as the call, notification and/or alarm sound. Free for everyone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RingtoneEditorScreen(song: Song, onClose: () -> Unit) {
    val context = LocalContext.current
    val maker = remember { RingtoneMaker(context.applicationContext) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    val duration = song.durationMs.coerceAtLeast(MIN_CLIP_MS)
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(minOf(30_000L, duration)) }
    var waveform by remember { mutableStateOf<FloatArray?>(null) }
    var targets by remember { mutableStateOf(setOf(RingtoneTarget.Call)) }
    // Ringtone for one person: after saving, pick them from Contacts.
    var forContact by remember { mutableStateOf(false) }
    var fadeInSec by remember { mutableIntStateOf(0) }
    var fadeOutSec by remember { mutableIntStateOf(0) }
    var contactClip by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    // Saved clip waiting for the "Modify system settings" permission.
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var askWriteSettings by remember { mutableStateOf(false) }
    var awaitingSettings by remember { mutableStateOf(false) }

    fun setStart(ms: Long) { startMs = ms.coerceIn(0L, endMs - MIN_CLIP_MS) }
    fun setEnd(ms: Long) { endMs = ms.coerceIn(startMs + MIN_CLIP_MS, duration) }
    fun quickLength(len: Long) {
        val l = len.coerceAtMost(duration)
        if (startMs + l > duration) startMs = duration - l
        endMs = startMs + l
    }

    LaunchedEffect(song.uri) {
        // Stylised bars straight away, replaced by the real waveform as it decodes.
        val seed = song.id.hashCode()
        waveform = FloatArray(BARS) { 0.3f + 0.5f * abs(((it * 7919 + seed) % 97) / 97f) }
        runCatching { maker.waveform(song.uri, BARS) { waveform = it } }.onSuccess { waveform = it }
    }

    // Preview: our own small player, so the main queue isn't touched (it pauses for audio focus).
    val preview = remember {
        ExoPlayer.Builder(context).setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
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
            // Hear the fades in the preview too.
            val fin = if (fadeInSec > 0) ((pos - startMs).toFloat() / (fadeInSec * 1000)).coerceIn(0f, 1f) else 1f
            val fout = if (fadeOutSec > 0) ((endMs - pos).toFloat() / (fadeOutSec * 1000)).coerceIn(0f, 1f) else 1f
            preview.volume = minOf(fin, fout)
            if (pos >= endMs || (!preview.playWhenReady)) previewing = false
            delay(40)
        }
    }

    fun successMessage(applied: Set<RingtoneTarget>): String {
        val joiner = " " + context.getString(R.string.ringtone_and) + " "
        val what = applied.sortedBy { it.ordinal }.joinToString(joiner) { context.getString(it.whatRes) }
        return context.getString(R.string.ringtone_set_as, what)
    }

    fun applyDefaults(uri: Uri) {
        val ok = maker.setAsDefault(uri, targets)
        Toast.makeText(
            context,
            if (ok) successMessage(targets) else context.getString(R.string.ringtone_saved_not_set),
            Toast.LENGTH_LONG,
        ).show()
    }

    fun finish(uri: Uri) {
        if (targets.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.ringtone_saved), Toast.LENGTH_SHORT).show()
            onClose()
        } else if (maker.canSetDefaults()) {
            applyDefaults(uri)
            onClose()
        } else {
            pendingUri = uri
            askWriteSettings = true
        }
    }

    // Per-contact ringtone: ask for Contacts access, let the user pick a person, then carry on.
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { contact ->
        val clip = contactClip
        contactClip = null
        if (clip == null) return@rememberLauncherForActivityResult
        if (contact != null) {
            val ok = maker.setForContact(contact, clip)
            Toast.makeText(context, context.getString(if (ok) R.string.ringtone_contact_set else R.string.ringtone_contact_failed), Toast.LENGTH_LONG).show()
        }
        finish(clip)
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) pickContact.launch(null)
        else {
            Toast.makeText(context, context.getString(R.string.ringtone_contact_needs_permission), Toast.LENGTH_LONG).show()
            contactClip?.let { finish(it) }
            contactClip = null
        }
    }
    fun afterSave(uri: Uri) {
        if (!forContact) return finish(uri)
        contactClip = uri
        contactsPermission.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
    }

    // Back from the system settings page: apply if the switch was turned on.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingSettings) {
                awaitingSettings = false
                val uri = pendingUri
                if (uri != null && maker.canSetDefaults()) {
                    applyDefaults(uri)
                } else {
                    Toast.makeText(context, context.getString(R.string.ringtone_saved_pick_later), Toast.LENGTH_LONG).show()
                }
                onClose()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.ringtone_close)) }
                Text(stringResource(R.string.ringtone_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.artworkUri, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
                Text("0:00", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            TimeNudger(stringResource(R.string.ringtone_start), startMs, onChange = ::setStart)
            TimeNudger(stringResource(R.string.ringtone_end), endMs, onChange = ::setEnd)
            Text(
                stringResource(R.string.ringtone_length, formatClipTime(endMs - startMs)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 40).forEach { s ->
                    AssistChip(onClick = { quickLength(s * 1000L) }, label = { Text(stringResource(R.string.settings_seconds, s)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientPlayButton(previewing, 52.dp) { previewing = !previewing }
                Spacer(Modifier.width(12.dp))
                Text(if (previewing) stringResource(R.string.ringtone_previewing) else stringResource(R.string.ringtone_preview))
            }

            Spacer(Modifier.height(16.dp))
            FadePicker(stringResource(R.string.ringtone_fade_in), fadeInSec) { fadeInSec = it }
            FadePicker(stringResource(R.string.ringtone_fade_out), fadeOutSec) { fadeOutSec = it }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.ringtone_use_as), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RingtoneTarget.entries.forEach { t ->
                    val on = t in targets
                    FilterChip(
                        selected = on,
                        onClick = { targets = if (on) targets - t else targets + t },
                        label = { Text(stringResource(t.labelRes)) },
                        leadingIcon = if (on) {
                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                        } else null,
                    )
                }
                FilterChip(
                    selected = forContact,
                    onClick = { forContact = !forContact },
                    label = { Text(stringResource(R.string.ringtone_target_contact)) },
                    leadingIcon = if (forContact) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
                FilterChip(
                    selected = targets.isEmpty() && !forContact,
                    onClick = { targets = emptySet(); forContact = false },
                    label = { Text(stringResource(R.string.ringtone_save_only)) },
                    leadingIcon = if (targets.isEmpty() && !forContact) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
            }

            Spacer(Modifier.height(20.dp))
            if (saving) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ringtone_cutting), style = MaterialTheme.typography.bodyMedium)
            } else {
                Button(
                    onClick = {
                        previewing = false
                        error = null
                        saving = true
                        progress = 0f
                        scope.launch {
                            runCatching {
                                maker.makeClip(
                                    song, startMs, endMs, targets, onProgress = { p -> progress = p },
                                    fadeInMs = fadeInSec * 1000L, fadeOutMs = fadeOutSec * 1000L,
                                )
                            }.onSuccess { uri ->
                                saving = false
                                afterSave(uri)
                            }.onFailure { e ->
                                saving = false
                                error = e.message ?: context.getString(R.string.ringtone_err_generic)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (targets.isEmpty()) stringResource(R.string.ringtone_save) else stringResource(R.string.ringtone_save_and_set)) }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ringtone_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (askWriteSettings) {
        AlertDialog(
            onDismissRequest = { askWriteSettings = false; onClose() },
            title = { Text(stringResource(R.string.ringtone_permission_title)) },
            text = {
                Text(stringResource(R.string.ringtone_permission_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    askWriteSettings = false
                    awaitingSettings = true
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text(stringResource(R.string.ringtone_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askWriteSettings = false
                    Toast.makeText(context, context.getString(R.string.ringtone_saved_pick_later), Toast.LENGTH_LONG).show()
                    onClose()
                }) { Text(stringResource(R.string.common_not_now)) }
            },
        )
    }
}

/** "Fade in: Off 1s 2s 3s" chips. */
@Composable
private fun FadePicker(label: String, seconds: Int, onPick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
        listOf(0, 1, 2, 3).forEach { s ->
            FilterChip(
                selected = seconds == s,
                onClick = { onPick(s) },
                label = { Text(if (s == 0) stringResource(R.string.ringtone_fade_off) else stringResource(R.string.ringtone_fade_seconds, s)) },
            )
        }
    }
}

/** Waveform strip with the selected part highlighted and two draggable handles. */
@Composable
private fun Waveform(
    bars: FloatArray?,
    startFraction: Float,
    endFraction: Float,
    playheadFraction: Float?,
    onMoveStart: (Float) -> Unit,
    onMoveEnd: (Float) -> Unit,
) {
    val selected = MaterialTheme.colorScheme.primary
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val handle = Ampli.Rose
    val playColor = MaterialTheme.colorScheme.onSurface
    // Which handle the finger is moving: true = start.
    var draggingStart by remember { mutableStateOf(true) }
    val currentStart by rememberUpdatedState(startFraction)
    val currentEnd by rememberUpdatedState(endFraction)

    Canvas(
        Modifier.fillMaxWidth().height(140.dp)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val f = (pos.x / size.width).coerceIn(0f, 1f)
                    if (abs(f - currentStart) <= abs(f - currentEnd)) onMoveStart(f) else onMoveEnd(f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val f = pos.x / size.width
                        draggingStart = abs(f - currentStart) <= abs(f - currentEnd)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        if (draggingStart) onMoveStart(f) else onMoveEnd(f)
                    },
                )
            },
    ) {
        val levels = bars ?: return@Canvas
        val slot = size.width / levels.size
        val barW = (slot * 0.6f).coerceAtLeast(1f)
        val mid = size.height / 2
        levels.forEachIndexed { i, level ->
            val x = i * slot + (slot - barW) / 2
            val f = (i + 0.5f) / levels.size
            val h = (level * size.height * 0.9f).coerceAtLeast(2f)
            drawRoundRect(
                color = if (f in startFraction..endFraction) selected else dimmed,
                topLeft = Offset(x, mid - h / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2),
            )
        }
        listOf(startFraction, endFraction).forEach { f ->
            val x = f * size.width
            drawLine(handle, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            drawCircle(handle, radius = 8.dp.toPx(), center = Offset(x, if (f == startFraction) 8.dp.toPx() else size.height - 8.dp.toPx()))
        }
        playheadFraction?.let {
            val x = it * size.width
            drawLine(playColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/** "Start 0:12.3" with −0.1 s / +0.1 s buttons. */
@Composable
private fun TimeNudger(label: String, ms: Long, onChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(56.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onChange(ms - NUDGE_MS) }) { Icon(Icons.Default.Remove, stringResource(R.string.ringtone_nudge_earlier, label)) }
        Text(formatClipTime(ms), style = MaterialTheme.typography.titleMedium, color = Color.Unspecified)
        IconButton(onClick = { onChange(ms + NUDGE_MS) }) { Icon(Icons.Default.Add, stringResource(R.string.ringtone_nudge_later, label)) }
    }
}

private fun formatClipTime(ms: Long): String =
    String.format(Locale.US, "%d:%04.1f", ms / 60_000, (ms % 60_000) / 1000.0)
