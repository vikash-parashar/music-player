package com.vikash.mp3player.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.MusicApp
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.AutoPlay
import com.vikash.mp3player.playback.ProSound

/** Play Store page, shared in invites and the stats share card. */
const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.vikash.ampli"

@Composable
private fun CardTitle(title: String, pro: Boolean, unlocked: Boolean, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (pro && !unlocked) {
            Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text("PRO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            trailing()
        }
    }
}

/** Free: start playing when Bluetooth, the car stereo or wired earphones connect. */
@Composable
fun AutoPlayCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(AutoPlay.isEnabled(context)) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardTitle(stringResource(R.string.autoplay_title), pro = false, unlocked = true) {
                Switch(checked = on, onCheckedChange = { on = it; AutoPlay.setEnabled(context, it) })
            }
            Text(
                stringResource(R.string.autoplay_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Free: robot arm CD change animation when a song is tapped. */
@Composable
fun DiscAnimationCard() {
    val prefs = (LocalContext.current.applicationContext as MusicApp).prefs
    val on by prefs.discAnimation.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardTitle(stringResource(R.string.disc_anim_title), pro = false, unlocked = true) {
                Switch(checked = on, onCheckedChange = prefs::setDiscAnimation)
            }
            Text(
                stringResource(R.string.disc_anim_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Free: Lite mode for older phones. On by default when the phone has about 3 GB of RAM or less;
 * the dance buddy, visualiser and looping animations rest and transitions are shorter.
 */
@Composable
fun LiteModeCard() {
    val prefs = (LocalContext.current.applicationContext as MusicApp).prefs
    val on by prefs.liteMode.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardTitle(stringResource(R.string.lite_mode_title), pro = false, unlocked = true) {
                Switch(checked = on, onCheckedChange = prefs::setLiteMode)
            }
            Text(
                stringResource(R.string.lite_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Pro: 10-band equaliser, bass boost and 3D surround. */
@Composable
fun ProSoundCard(unlocked: Boolean, onGetPro: () -> Unit) {
    val context = LocalContext.current
    val s by ProSound.state.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardTitle(stringResource(R.string.prosound_title), pro = true, unlocked = unlocked) {
                if (s.eqAvailable) Switch(checked = s.eqEnabled, onCheckedChange = { ProSound.setEqEnabled(context, it) })
            }
            Text(
                stringResource(R.string.prosound_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!unlocked) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onGetPro) { Text(stringResource(R.string.prosound_unlock)) }
                return@Column
            }
            if (!s.eqAvailable && !s.bassAvailable && !s.surroundAvailable) {
                Text(stringResource(R.string.prosound_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (s.eqAvailable) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProSound.Preset.entries.forEach { p ->
                        AssistChip(
                            onClick = { ProSound.usePreset(context, p) },
                            label = { Text(stringResource(presetLabel(p))) },
                            enabled = s.eqEnabled,
                        )
                    }
                }
                ProSound.FREQUENCIES.forEachIndexed { i, hz ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (hz >= 1000) "${hz / 1000}k" else "$hz", Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = s.gains[i],
                            onValueChange = { ProSound.setGain(context, i, it) },
                            valueRange = -ProSound.MAX_DB..ProSound.MAX_DB,
                            enabled = s.eqEnabled,
                            modifier = Modifier.weight(1f),
                        )
                        Text("%+.0f dB".format(s.gains[i]), Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (s.bassAvailable) {
                Text(stringResource(R.string.prosound_bass), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = s.bass.toFloat(),
                    onValueChange = { ProSound.setBass(context, it.toInt()) },
                    valueRange = 0f..ProSound.MAX_STRENGTH.toFloat(),
                )
            }
            if (s.surroundAvailable) {
                Text(stringResource(R.string.prosound_surround), style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = s.surround.toFloat(),
                    onValueChange = { ProSound.setSurround(context, it.toInt()) },
                    valueRange = 0f..ProSound.MAX_STRENGTH.toFloat(),
                )
                Text(
                    stringResource(R.string.prosound_surround_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun presetLabel(p: ProSound.Preset): Int = when (p) {
    ProSound.Preset.Flat -> R.string.prosound_preset_flat
    ProSound.Preset.Bass -> R.string.prosound_preset_bass
    ProSound.Preset.Vocal -> R.string.prosound_preset_vocal
    ProSound.Preset.Bollywood -> R.string.prosound_preset_bollywood
    ProSound.Preset.Rock -> R.string.prosound_preset_rock
    ProSound.Preset.Classical -> R.string.prosound_preset_classical
}

/** Pro: pure black theme and wallpaper colours. */
@Composable
fun ProLookCard(unlocked: Boolean, onGetPro: () -> Unit) {
    val prefs = (LocalContext.current.applicationContext as MusicApp).prefs
    val amoled by prefs.amoledBlack.collectAsState()
    val wallpaper by prefs.wallpaperColors.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardTitle(stringResource(R.string.prolook_title), pro = true, unlocked = unlocked)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.prolook_amoled))
                    Text(stringResource(R.string.prolook_amoled_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = unlocked && amoled, onCheckedChange = { if (unlocked) prefs.setAmoledBlack(it) else onGetPro() })
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.prolook_wallpaper))
                        Text(stringResource(R.string.prolook_wallpaper_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = unlocked && wallpaper, onCheckedChange = { if (unlocked) prefs.setWallpaperColors(it) else onGetPro() })
                }
            }
        }
    }
}

/** Free: share the app with friends. */
@Composable
fun InviteCard() {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.invite_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.invite_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val text = context.getString(R.string.invite_message, PLAY_STORE_URL)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.invite_title))) }
            }) { Text(stringResource(R.string.invite_button)) }
        }
    }
}
