package com.vikash.mp3player.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
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
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RemoveAdsCard(adsRemoved, price, onRemoveAds)
        AppearanceCard(themeMode, onThemeMode)
        SleepTimerCard()
        EqualizerCard()
    }
}

@Composable
private fun RemoveAdsCard(adsRemoved: Boolean, price: String?, onRemoveAds: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ampli.Gradient)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (adsRemoved) "Ampli Ad-free" else "Go ad-free",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (adsRemoved) "You're ad-free forever. Thanks for supporting Ampli!"
                    else "One payment, no subscription. Works on every phone with this Google account.",
                    color = Color.White.copy(alpha = 0.9f),
                )
                if (!adsRemoved) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onRemoveAds,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ampli.RoseDeep),
                    ) { Text(if (price != null) "Remove ads for $price" else "Remove ads") }
                }
            }
            Spacer(Modifier.width(12.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(88.dp))
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
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) { now = System.currentTimeMillis(); delay(1000) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
            val remaining = endsAt?.let { it - now }
            Text(
                if (remaining != null) "Music stops in ${formatDuration(remaining.coerceAtLeast(0))}" else "Stop the music after a while.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    AssistChip(onClick = { SleepTimer.start(m) }, label = { Text("$m min") })
                }
                if (endsAt != null) OutlinedButton(onClick = SleepTimer::cancel) { Text("Cancel") }
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
