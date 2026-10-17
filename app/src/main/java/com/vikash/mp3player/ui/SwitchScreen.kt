package com.vikash.mp3player.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.INTERNAL_STORAGE
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.UNKNOWN_ALBUM
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.delay

/** Folder keys (as saved in hidden folders) that hold WhatsApp voice notes and audio clips. */
fun whatsAppAudioFolders(folders: Collection<String>): List<String> = folders.filter { f ->
    val p = f.lowercase()
    "whatsapp" in p && ("audio" in p || "voice notes" in p || "voice_notes" in p)
}

private val CALL_SEGMENTS = setOf(
    "call", "calls", "recordings", "recorder", "callrecordings", "call recordings", "call_recordings",
    "callrecord", "call record", "call recorder", "call_rec", "callrec",
)

/** Folder keys that look like call recorder output ("Recordings/Call", "MIUI/sound_recorder/call_rec"...). */
fun callRecordingFolders(folders: Collection<String>): List<String> = folders.filter { f ->
    val p = f.lowercase().replace('\\', '/')
    "sound_recorder/call_rec" in p || "record/call" in p ||
        p.split('/').any { it.trim() in CALL_SEGMENTS }
}

/**
 * One-time "Switching from Wynk or Samsung Music?" screen, shown right after the first-run
 * welcome once music access is granted. Shows what Ampli found, offers to hide WhatsApp audio
 * and call recordings, and imports Samsung Music .m3u playlists. Skip changes nothing.
 *
 * [allFolders] are every folder key in the library, hidden ones included.
 */
@Composable
fun SwitchScreen(
    songs: List<Song>,
    allFolders: List<String>,
    onImport: (List<Uri>, (Int) -> Unit) -> Unit,
    onStart: (hideFolders: List<String>) -> Unit,
    onSkip: () -> Unit,
) {
    BackHandler(onBack = onSkip)

    var hideWhatsApp by rememberSaveable { mutableStateOf(true) }
    var hideCalls by rememberSaveable { mutableStateOf(true) }
    var importedCount by remember { mutableStateOf<Int?>(null) }

    val songCount = songs.size
    val albumCount = remember(songs) {
        songs.map { it.album.trim().lowercase() }.filter { it.isNotEmpty() && it != UNKNOWN_ALBUM.lowercase() }.distinct().size
    }
    val folderCount = remember(songs) { songs.map { it.folder.ifBlank { INTERNAL_STORAGE } }.distinct().size }
    val waFolders = remember(allFolders) { whatsAppAudioFolders(allFolders) }
    val callFolders = remember(allFolders) { callRecordingFolders(allFolders) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImport(uris) { n -> importedCount = n }
    }
    val importedMsg: String? = importedCount?.let { n ->
        if (n == 0) stringResource(R.string.m3u_nothing_found) else pluralStringResource(R.plurals.switch_imported, n, n)
    }

    // Staggered entrance: each block fades and rises in turn.
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(6) { delay(90); step++ }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ampli.Night, Color(0xFF2A0F1C))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .size(320.dp)
                .background(Brush.radialGradient(listOf(Ampli.Rose.copy(alpha = 0.28f), Color.Transparent)), CircleShape),
        )

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.switch_skip), color = Color.White.copy(alpha = 0.75f))
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Entrance(step >= 1) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.switch_title), color = Color.White, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.switch_subtitle), color = Color.White.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center, fontSize = 16.sp, lineHeight = 23.sp,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                Entrance(step >= 2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox(stringResource(R.string.switch_stat_songs), songCount, step >= 2, Modifier.weight(1f))
                        StatBox(stringResource(R.string.switch_stat_albums), albumCount, step >= 2, Modifier.weight(1f))
                        StatBox(stringResource(R.string.switch_stat_folders), folderCount, step >= 2, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(20.dp))

                Entrance(step >= 3) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                    ) {
                        SwitchRow(
                            stringResource(R.string.switch_hide_whatsapp), stringResource(R.string.switch_hide_whatsapp_sub),
                            hideWhatsApp, { hideWhatsApp = it },
                        )
                        SwitchRow(
                            stringResource(R.string.switch_hide_calls), stringResource(R.string.switch_hide_calls_sub),
                            hideCalls, { hideCalls = it },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Entrance(step >= 4) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ampli.Rose.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = Ampli.Blush, modifier = Modifier.size(22.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.switch_import), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(stringResource(R.string.switch_import_sub), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                            TextButton(onClick = {
                                picker.launch(
                                    arrayOf(
                                        "audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl",
                                        "application/vnd.apple.mpegurl", "application/octet-stream", "text/plain",
                                    )
                                )
                            }) { Text(stringResource(R.string.switch_pick), color = Ampli.Blush, fontWeight = FontWeight.SemiBold) }
                        }
                        AnimatedVisibility(visible = importedMsg != null, enter = fadeIn() + expandVertically()) {
                            Text(
                                importedMsg.orEmpty(), color = Ampli.Blush, fontSize = 13.sp,
                                modifier = Modifier.padding(start = 54.dp, top = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                Entrance(step >= 5) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Icon(
                            Icons.Default.Info, null, tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp).size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.switch_note), color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Entrance(step >= 6) {
                StartButton(
                    onClick = {
                        val hide = buildList {
                            if (hideWhatsApp) addAll(waFolders)
                            if (hideCalls) addAll(callFolders)
                        }.distinct()
                        onStart(hide)
                    },
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 28.dp),
                ) {
                    Text(stringResource(R.string.switch_start), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun Entrance(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 3 },
    ) { content() }
}

/** Owner's rule: label at the top, number at the bottom right, every box the same size. */
@Composable
private fun StatBox(label: String, count: Int, started: Boolean, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(
        targetValue = if (started) count else 0,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "statCount",
    )
    Box(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(12.dp),
    ) {
        Text(
            label, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            "%,d".format(shown), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Ampli.Rose),
        )
    }
}

@Composable
private fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "press")
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(Ampli.Rose, Ampli.RoseDeep)))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
