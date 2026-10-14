package com.vikash.mp3player.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.vikash.mp3player.BuildConfig
import com.vikash.mp3player.R
import com.vikash.mp3player.ui.theme.Ampli
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import com.vikash.mp3player.data.ThemeMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.playback.EqualizerController
import com.vikash.mp3player.playback.SleepTimer
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    adsRemoved: Boolean,
    price: String?,
    onRemoveAds: () -> Unit,
    onTestPro: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    seekStyle: SeekStyle,
    onSeekStyle: (SeekStyle) -> Unit,
    onBackup: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenDuplicates: () -> Unit,
    folders: List<Pair<String, Int>>,
    hiddenFolders: Set<String>,
    onFolderHidden: (String, Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProCard(adsRemoved, price, onRemoveAds)
        if (BuildConfig.DEBUG) TestProCard(adsRemoved, onTestPro)
        AppearanceCard(themeMode, onThemeMode)
        SeekStyleCard(seekStyle, isPro = adsRemoved, onPick = onSeekStyle, onGetPro = onRemoveAds)
        SleepTimerCard()
        FadeCard()
        EqualizerCard()
        CarModeCard(onOpenCarMode)
        TransferCard(onOpenTransfer)
        FoldersCard(folders, hiddenFolders, onFolderHidden)
        DuplicatesCard(onOpenDuplicates)
        BackupCard(onBackup, onRestore)
    }
}

/** What the one-time Ampli Pro purchase unlocks. Shared with the locked Videos screen. */
val PRO_FEATURES = listOf(
    "No ads, ever",
    "Video player for camera clips and downloads",
    "Name that song: identify music playing around you (coming soon)",
)

@Composable
fun ProCard(isPro: Boolean, price: String?, onGetPro: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ampli.Gradient)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isPro) "You have Ampli Pro" else "Ampli Pro",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isPro) "Everything is unlocked forever. Thanks for supporting Ampli!"
                    else "Pay once, keep it forever. No monthly fee, on every phone with this Google account.",
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(10.dp))
                PRO_FEATURES.forEach {
                    Text("✓  $it", color = Color.White, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
                if (!isPro) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onGetPro,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ampli.RoseDeep),
                    ) { Text(if (price != null) "Get Pro for $price" else "Get Ampli Pro") }
                }
            }
            Spacer(Modifier.width(8.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(72.dp))
        }
    }
}

@Composable
private fun TestProCard(isPro: Boolean, onTestPro: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Test build: try Pro", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Purchases only work in the Play Store version. This switch is not in the release app.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = isPro, onCheckedChange = onTestPro)
        }
    }
}

/** Pick the Now Playing progress bar. Each option shows a live preview. */
@Composable
private fun SeekStyleCard(current: SeekStyle, isPro: Boolean, onPick: (SeekStyle) -> Unit, onGetPro: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text("Progress bar style", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                if (isPro) "Pick how the bar moves while a song plays." else "Classic and Squiggle are free. The rest come with Ampli Pro.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))
            SeekStyle.entries.forEachIndexed { i, style ->
                val locked = style.pro && !isPro
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (locked) onGetPro() else onPick(style) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = style == current, onClick = { if (locked) onGetPro() else onPick(style) }, enabled = !locked)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(style.label, style = MaterialTheme.typography.bodyLarge)
                            if (locked) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.Lock, "Ampli Pro", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        AnimatedSeekBar(
                            progress = 0.25f + 0.07f * i,
                            playing = true,
                            style = style,
                            height = 40.dp,
                            modifier = Modifier.alpha(if (locked) 0.55f else 1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceCard(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Day / Night mode", style = MaterialTheme.typography.titleMedium)
            Text(
                "Auto follows your phone's dark mode.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { onMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        icon = {
                            Icon(
                                when (m) {
                                    ThemeMode.System -> Icons.Default.BrightnessAuto
                                    ThemeMode.Light -> Icons.Default.LightMode
                                    ThemeMode.Dark -> Icons.Default.DarkMode
                                },
                                null,
                                Modifier.size(18.dp),
                            )
                        },
                    ) { Text(m.label) }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerCard() {
    val endsAt by SleepTimer.endsAtMillis.collectAsState()
    val afterSong by SleepTimer.stopAfterSong.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) { now = System.currentTimeMillis(); delay(1000) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
            val remaining = endsAt?.let { it - now }
            Text(
                when {
                    afterSong -> "Music stops when this song ends."
                    remaining != null -> "Music stops in ${formatDuration(remaining.coerceAtLeast(0))}"
                    else -> "Stop the music after a while."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = afterSong,
                    onClick = { SleepTimer.setStopAfterSong(!afterSong) },
                    label = { Text("End of this song") },
                )
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    AssistChip(onClick = { SleepTimer.start(m) }, label = { Text("$m min") })
                }
                if (endsAt != null || afterSong) OutlinedButton(onClick = SleepTimer::cancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun EqualizerCard() {
    val context = LocalContext.current
    val eq by EqualizerController.state.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Equalizer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (eq.available) Switch(checked = eq.enabled, onCheckedChange = { EqualizerController.setEnabled(context, it) })
            }
            if (!eq.available) {
                Text(
                    "Start playing a song to use the equalizer. Some phones don't support one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(
                "Now adjusting: ${eq.outputLabel}. Speaker, headphones and each Bluetooth device (like your car) keep their own settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                eq.presets.forEachIndexed { i, name ->
                    AssistChip(
                        onClick = { EqualizerController.usePreset(context, i.toShort()) },
                        label = { Text(name) },
                        enabled = eq.enabled,
                    )
                }
            }
            eq.bands.forEach { band ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hz = band.centerHz
                    Text(if (hz >= 1000) "${hz / 1000}k" else "$hz", Modifier.width(48.dp))
                    Slider(
                        value = band.levelMb.toFloat(),
                        onValueChange = { EqualizerController.setBandLevel(context, band.index, it.toInt().toShort()) },
                        valueRange = eq.minLevelMb.toFloat()..eq.maxLevelMb.toFloat(),
                        enabled = eq.enabled,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${band.levelMb / 100} dB", Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Save favorites, settings, equalizer and listening history to a file, or load them back. */
@Composable
private fun BackupCard(onBackup: (Uri) -> Unit, onRestore: (Uri) -> Unit) {
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onBackup(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestore(uri)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Backup and restore", style = MaterialTheme.typography.titleMedium)
            Text(
                "Keep your favorites, settings, equalizer and listening history safe. Save to Google Drive to move them to a new phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { save.launch("ampli-backup-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}.json") }) { Text("Back up") }
                OutlinedButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                    Text("Restore")
                }
            }
        }
    }
}

/** Opens the Wi-Fi transfer screen: drop songs on a laptop browser, they land on the phone. */
@Composable
private fun TransferCard(onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Send songs from your computer", style = MaterialTheme.typography.titleMedium)
            Text(
                "No cable needed. Open an address on any laptop on the same Wi-Fi and drag your music in.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpen) { Text("Start Wi-Fi transfer") }
        }
    }
}

/** Opens the duplicate finder: the same song saved twice wastes space and clutters shuffle. */
@Composable
private fun DuplicatesCard(onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Clean up duplicates", style = MaterialTheme.typography.titleMedium)
            Text(
                "Find songs saved more than once, keep the best copy and free up space.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpen) { Text("Find duplicates") }
        }
    }
}

/** "Fade between songs": how many seconds the end and start of songs fade over. */
@Composable
private fun FadeCard() {
    val context = LocalContext.current
    val seconds by remember { com.vikash.mp3player.playback.FadeSetting.seconds(context) }.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Fade between songs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Each song fades out at the end and the next one fades in, so nothing stops suddenly.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.vikash.mp3player.playback.FadeSetting.CHOICES.forEach { s ->
                    FilterChip(
                        selected = seconds == s,
                        onClick = { com.vikash.mp3player.playback.FadeSetting.set(context, s) },
                        label = { Text(if (s == 0) "Off" else "$s s") },
                    )
                }
            }
        }
    }
}

/** Hide folders full of non-music audio (WhatsApp voice notes, call recordings) from the Library. */
@Composable
private fun FoldersCard(folders: List<Pair<String, Int>>, hidden: Set<String>, onHidden: (String, Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Music folders", style = MaterialTheme.typography.titleMedium)
            Text(
                if (hidden.isEmpty()) "Hide folders you don't want in your Library, like WhatsApp audio or call recordings."
                else "${hidden.size} hidden. Their songs stay on the phone but don't show in Ampli.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Done" else "Choose folders") }
            if (expanded) {
                if (folders.isEmpty()) Text("No folders found yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                folders.forEach { (folder, count) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(folder, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("$count songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = folder !in hidden, onCheckedChange = { show -> onHidden(folder, !show) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CarModeCard(onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Car mode", style = MaterialTheme.typography.titleMedium)
            Text(
                "Huge buttons, swipe to change songs, and say a song or artist to play it. Keep your eyes on the road.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpen) { Text("Start car mode") }
        }
    }
}
