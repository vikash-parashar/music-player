package com.vikash.mp3player.ui

import android.app.LocaleManager
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.vikash.mp3player.ui.skin.SkinButton as Button
import com.vikash.mp3player.ui.skin.SkinCard as Card
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
import com.vikash.mp3player.ui.skin.SkinOutlinedButton as OutlinedButton
import androidx.compose.material3.Slider
import com.vikash.mp3player.ui.skin.SkinSwitch as Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onLibraryChanged: () -> Unit,
    onOpenHelp: () -> Unit,
    proUnlocked: Boolean = adsRemoved,
    tipPrices: Map<String, String> = emptyMap(),
    tipsReceived: Int = 0,
    onTip: (String) -> Unit = {},
    songs: List<com.vikash.mp3player.data.Song> = emptyList(),
    onOpenNetwork: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProCard(adsRemoved, price, onRemoveAds)
        SupportDeveloperCard(tipPrices, tipsReceived, onTip)
        MoreFromAmpliCard()
        if (BuildConfig.DEBUG) TestProCard(adsRemoved, onTestPro)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LanguageCard()
        com.vikash.mp3player.ui.skin.SkinPickerCard(proUnlocked, onRemoveAds)
        AppearanceCard(themeMode, onThemeMode)
        LiteModeCard()
        LibraryCleanCard(onLibraryChanged)
        ShakeToSkipCard()
        AudioOutputCard(isPro = proUnlocked, onGetPro = onRemoveAds)
        NetworkFoldersCard(isPro = proUnlocked, onOpen = onOpenNetwork, onGetPro = onRemoveAds)
        ProLookCard(proUnlocked, onRemoveAds)
        SeekStyleCard(seekStyle, isPro = proUnlocked, onPick = onSeekStyle, onGetPro = onRemoveAds)
        SleepTimerCard()
        FadeCard()
        EvenVolumeCard()
        EqualizerCard()
        ProSoundCard(proUnlocked, onRemoveAds)
        KeepGoingCard()
        AutoPlayCard()
        DiscAnimationCard()
        HeadsetClicksCard()
        CarModeCard(onOpenCarMode)
        TransferCard(onOpenTransfer)
        FoldersCard(folders, hiddenFolders, onFolderHidden)
        DuplicatesCard(onOpenDuplicates)
        FindMissingArtCard(songs)
        ConvertCard(onConverted = onLibraryChanged)
        BackupCard(onBackup, onRestore)
        InviteCard()
        HelpCard(onOpenHelp)
        PrivacyChoicesCard()
        UsageCountsCard()
        AboutCard()
    }
}

/** What the one-time Ampli Pro purchase unlocks. Shared with the locked Videos screen. */
@Composable
fun proFeatures(): List<String> = listOf(
    stringResource(R.string.pro_feature_no_ads),
    stringResource(R.string.pro_feature_videos),
    stringResource(R.string.pro_feature_cast),
    if (com.vikash.mp3player.recognize.SongRecognizer.isConfigured) stringResource(R.string.pro_feature_recognize)
    else stringResource(R.string.pro_feature_recognize_soon),
)

@Composable
fun ProCard(isPro: Boolean, price: String?, onGetPro: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ampli.Gradient)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isPro) stringResource(R.string.pro_you_have) else stringResource(R.string.pro_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isPro) stringResource(R.string.pro_thanks)
                    else stringResource(R.string.pro_pitch),
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(10.dp))
                proFeatures().forEach {
                    Text("✓  $it", color = Color.White, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
                if (!isPro) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onGetPro,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ampli.RoseDeep),
                    ) { Text(stringResource(R.string.pro_get_for_price, price ?: stringResource(R.string.pro_display_price))) }
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
                Text(stringResource(R.string.settings_test_pro), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_test_pro_desc),
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
            Text(stringResource(R.string.settings_seek_style), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                if (isPro) stringResource(R.string.settings_seek_style_desc_pro) else stringResource(R.string.settings_seek_style_desc_free),
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
                            Text(stringResource(style.labelRes), style = MaterialTheme.typography.bodyLarge)
                            if (locked) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.Lock, stringResource(R.string.pro_name), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
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
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_theme_desc),
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
                    ) { Text(stringResource(m.labelRes)) }
                }
            }
        }
    }
}

@Composable
internal fun SleepTimerCard() {
    val endsAt by SleepTimer.endsAtMillis.collectAsState()
    val afterSong by SleepTimer.stopAfterSong.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) { now = System.currentTimeMillis(); delay(1000) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_sleep_timer), style = MaterialTheme.typography.titleMedium)
            val remaining = endsAt?.let { it - now }
            Text(
                when {
                    afterSong -> stringResource(R.string.settings_sleep_after_song)
                    remaining != null -> stringResource(R.string.settings_sleep_stops_in, formatDuration(remaining.coerceAtLeast(0)))
                    else -> stringResource(R.string.settings_sleep_desc)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = afterSong,
                    onClick = { SleepTimer.setStopAfterSong(!afterSong) },
                    label = { Text(stringResource(R.string.settings_sleep_end_of_song)) },
                )
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    AssistChip(onClick = { SleepTimer.start(m) }, label = { Text(stringResource(R.string.settings_minutes, m)) })
                }
                if (endsAt != null || afterSong) OutlinedButton(onClick = SleepTimer::cancel) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

@Composable
internal fun EqualizerCard() {
    val context = LocalContext.current
    val eq by EqualizerController.state.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_equalizer), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (eq.available) Switch(checked = eq.enabled, onCheckedChange = { EqualizerController.setEnabled(context, it) })
            }
            if (!eq.available) {
                Text(
                    stringResource(R.string.settings_equalizer_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(
                stringResource(R.string.settings_equalizer_output, eq.outputLabel),
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
            Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_backup_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { save.launch("ampli-backup-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}.json") }) { Text(stringResource(R.string.settings_backup_button)) }
                OutlinedButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                    Text(stringResource(R.string.settings_restore_button))
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
            Text(stringResource(R.string.transfer_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_transfer_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpen) { Text(stringResource(R.string.settings_transfer_start)) }
        }
    }
}

/** Opens the duplicate finder: the same song saved twice wastes space and clutters shuffle. */
@Composable
private fun DuplicatesCard(onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_duplicates), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_duplicates_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpen) { Text(stringResource(R.string.settings_duplicates_button)) }
        }
    }
}

/** Earphone button: press twice for next, three times for previous. */
@Composable
private fun HeadsetClicksCard() {
    val context = LocalContext.current
    val on by remember { com.vikash.mp3player.playback.HeadsetClicks.enabled(context) }.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_headset_clicks), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_headset_clicks_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            com.vikash.mp3player.ui.skin.SkinSwitch(checked = on, onCheckedChange = { com.vikash.mp3player.playback.HeadsetClicks.setEnabled(context, it) })
        }
    }
}

@Composable
private fun HelpCard(onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.help_card_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpen) { Text(stringResource(R.string.help_open)) }
        }
    }
}

/** Lets people stop the feature-use counts sent to Ampli (only when the Ampli server is set up). */
@Composable
private fun UsageCountsCard() {
    if (!com.vikash.mp3player.data.OwnerHooks.enabled) return
    val context = LocalContext.current
    var on by remember { mutableStateOf(com.vikash.mp3player.data.OwnerHooks.usageOn(context)) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.usage_counts_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.usage_counts_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = on, onCheckedChange = { on = it; com.vikash.mp3player.data.OwnerHooks.setUsageOn(context, it) })
        }
    }
}

/** Version and the random app ID, so people can ask support to show or delete their data. */
@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val id = remember { com.vikash.mp3player.data.OwnerHooks.installId(context) }
    val copied = stringResource(R.string.about_copied)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.about_version, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.about_install_id), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    id, modifier = Modifier.weight(1f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Ampli app ID", id))
                    android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.about_copy)) }
            }
            Text(stringResource(R.string.about_install_id_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Shown only where the law requires it: lets people change their ad consent later. */
@Composable
private fun PrivacyChoicesCard() {
    val required by com.vikash.mp3player.monetization.Ads.privacyOptionsRequired.collectAsState()
    if (!required) return
    val activity = LocalContext.current as? android.app.Activity ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.privacy_choices_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_choices_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { com.vikash.mp3player.monetization.Ads.showPrivacyOptions(activity) }) {
                Text(stringResource(R.string.privacy_choices_open))
            }
        }
    }
}

/** "Even volume": uses ReplayGain tags so every song plays at about the same loudness. */
@Composable
internal fun EvenVolumeCard() {
    val context = LocalContext.current
    val on by remember { com.vikash.mp3player.playback.EvenVolume.enabled(context) }.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_even_volume), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_even_volume_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = on == true, onCheckedChange = { com.vikash.mp3player.playback.EvenVolume.set(context, it) })
        }
    }
}

/** "Fade between songs": how many seconds the end and start of songs fade over. */
@Composable
internal fun FadeCard() {
    val context = LocalContext.current
    val seconds by remember { com.vikash.mp3player.playback.FadeSetting.seconds(context) }.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_fade), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_fade_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.vikash.mp3player.playback.FadeSetting.CHOICES.forEach { s ->
                    FilterChip(
                        selected = seconds == s,
                        onClick = { com.vikash.mp3player.playback.FadeSetting.set(context, s) },
                        label = { Text(if (s == 0) stringResource(R.string.settings_off) else stringResource(R.string.settings_seconds, s)) },
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
            Text(stringResource(R.string.settings_folders), style = MaterialTheme.typography.titleMedium)
            Text(
                if (hidden.isEmpty()) stringResource(R.string.settings_folders_desc)
                else stringResource(R.string.settings_folders_hidden, hidden.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) stringResource(R.string.common_done) else stringResource(R.string.settings_folders_choose)) }
            if (expanded) {
                if (folders.isEmpty()) Text(stringResource(R.string.settings_folders_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                folders.forEach { (folder, count) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(groupLabel(folder), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(pluralStringResource(R.plurals.library_song_count, count, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(stringResource(R.string.car_mode), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_car_mode_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpen) { Text(stringResource(R.string.settings_car_mode_start)) }
        }
    }
}

/**
 * App language (Android 13+). Older phones simply follow the phone's language. Android restarts
 * the screen in the new language by itself.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    val localeManager = remember(context) { context.getSystemService(LocaleManager::class.java) }
    var current by remember {
        mutableStateOf(localeManager?.applicationLocales?.let { if (it.isEmpty()) "" else it.get(0).language }.orEmpty())
    }
    fun pick(tag: String) {
        current = tag
        localeManager?.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
    val choices = listOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_english),
        "hi" to stringResource(R.string.settings_language_hindi),
        "ta" to stringResource(R.string.settings_language_tamil),
        "te" to stringResource(R.string.settings_language_telugu),
        "bn" to stringResource(R.string.settings_language_bengali),
        "mr" to stringResource(R.string.settings_language_marathi),
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                stringResource(R.string.settings_language_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))
            choices.forEach { (tag, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { pick(tag) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = current == tag, onClick = { pick(tag) })
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
