package com.vikash.mp3player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.AudioOutput

/** "24-bit · 96 kHz FLAC" style label for what is playing; empty when nothing has played yet. */
fun AudioOutput.Info.sourceLabel(): String = listOfNotNull(
    sourceBits.takeIf { it > 0 }?.let { "$it-bit" },
    sourceRate.takeIf { it > 0 }?.let { khz(it) },
    codec.takeIf { it.isNotBlank() },
).joinToString(" · ")

fun AudioOutput.Info.outputLabel(): String = listOfNotNull(
    outputBits.takeIf { it > 0 }?.let { if (it == 32) "32-bit float" else "$it-bit" },
    outputRate.takeIf { it > 0 }?.let { khz(it) },
).joinToString(" · ")

private fun khz(hz: Int) = if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(hz / 1000.0)

/** Hi-Res output and USB DAC bit-perfect playback (Pro), plus a live readout of the signal path. */
@Composable
fun AudioOutputCard(isPro: Boolean, onGetPro: () -> Unit) {
    val context = LocalContext.current
    val info by AudioOutput.info.collectAsStateWithLifecycle()
    var hiRes by remember { mutableStateOf(AudioOutput.hiResEnabled(context)) }
    var bitPerfect by remember { mutableStateOf(AudioOutput.bitPerfectEnabled(context)) }
    val startedWith = remember { AudioOutput.hiResEnabled(context) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.hires_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!isPro) Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Signal path: device, then what the file is, then what reaches Android.
            Text(stringResource(R.string.hires_device, info.device.ifBlank { "-" }), style = MaterialTheme.typography.bodyMedium)
            if (info.sourceLabel().isNotBlank()) Text(
                stringResource(R.string.hires_path, info.sourceLabel(), info.outputLabel().ifBlank { "-" }),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info.bitPerfect) Text(stringResource(R.string.hires_bit_perfect_on), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.hires_switch), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.hires_switch_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hiRes && isPro, onCheckedChange = {
                    if (!isPro) onGetPro() else { hiRes = it; AudioOutput.setHiRes(context, it) }
                })
            }
            if (isPro && hiRes != startedWith) Text(stringResource(R.string.hires_restart), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.usb_bit_perfect), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (AudioOutput.bitPerfectSupported) R.string.usb_bit_perfect_desc else R.string.usb_bit_perfect_old),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = bitPerfect && isPro && AudioOutput.bitPerfectSupported,
                    enabled = AudioOutput.bitPerfectSupported,
                    onCheckedChange = {
                        if (!isPro) onGetPro() else { bitPerfect = it; AudioOutput.setBitPerfect(context, it) }
                    },
                )
            }
            if (!isPro) TextButton(onClick = onGetPro) { Text(stringResource(R.string.hires_get_pro)) }
        }
    }
}

/** Entry to Network folders (Pro). */
@Composable
fun NetworkFoldersCard(isPro: Boolean, onOpen: () -> Unit, onGetPro: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { if (isPro) onOpen() else onGetPro() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lan, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.net_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.net_card_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (isPro) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.Lock, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
