package com.vikash.mp3player.ui.skin

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.MusicApp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.UiSkin
import com.vikash.mp3player.ui.isDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Free users can wear a Pro look for [SECONDS] seconds; then it goes back to Classic. */
object SkinTrial {
    const val SECONDS = 30
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _active = MutableStateFlow<UiSkin?>(null)
    val active: StateFlow<UiSkin?> = _active.asStateFlow()
    private val _left = MutableStateFlow(0)
    val left: StateFlow<Int> = _left.asStateFlow()
    /** The look whose trial just ran out, so the app can offer Pro once. */
    val ended = MutableStateFlow<UiSkin?>(null)

    fun start(skin: UiSkin) {
        job?.cancel()
        _active.value = skin
        job = scope.launch {
            for (s in SECONDS downTo 1) { _left.value = s; delay(1000) }
            _active.value = null
            ended.value = skin
        }
    }

    fun stop() {
        job?.cancel()
        _active.value = null
    }
}

/** A small live sample of a look: its background with one card on it, drawn in that look. */
@Composable
private fun SkinSwatch(skin: UiSkin, selected: Boolean, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val dark = isDarkTheme()
    val spec = skinSpec(skin, dark)
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(horizontal = 4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape)
                .clickable(onClick = onClick),
        ) {
            MaterialTheme(colorScheme = skinColorScheme(skin, dark) ?: MaterialTheme.colorScheme) {
                CompositionLocalProvider(LocalSkin provides spec) {
                    Box(Modifier.fillMaxSize().skinBackdrop().padding(10.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1.3f).skinSurface(RoundedCornerShape(minOf(spec.corner, 10.dp))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Aa", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (locked) Icon(
                Icons.Default.Lock, null,
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(skin.labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SkinGrid(current: UiSkin, unlocked: Boolean, onPick: (UiSkin) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UiSkin.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k -> SkinSwatch(k, k == current, locked = !unlocked && k != UiSkin.Classic, Modifier.weight(1f)) { onPick(k) } }
            }
        }
    }
}

/** Settings card: pick the app style (Pro), try one for 30 seconds, turn haptics on or off. */
@Composable
fun SkinPickerCard(unlocked: Boolean, onGetPro: () -> Unit) {
    val prefs = (LocalContext.current.applicationContext as MusicApp).prefs
    val chosen by prefs.uiSkin.collectAsState()
    val haptics by prefs.skinHaptics.collectAsState()
    val trial by SkinTrial.active.collectAsState()
    val left by SkinTrial.left.collectAsState()
    val current = trial ?: if (unlocked) chosen else UiSkin.Classic
    SkinCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.skin_card_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!unlocked) Text("PRO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                stringResource(R.string.skin_card_desc), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            SkinGrid(current, unlocked) { k ->
                when {
                    unlocked || k == UiSkin.Classic -> { SkinTrial.stop(); prefs.setUiSkin(k) }
                    else -> SkinTrial.start(k)
                }
            }
            val t = trial
            if (t != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.skin_trying, stringResource(t.labelRes), left),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    SkinButton(onClick = onGetPro) { Text(stringResource(R.string.pstyle_unlock)) }
                }
            } else if (!unlocked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.skin_try) + " · " + stringResource(R.string.skin_keep_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.skin_haptics))
                    Text(
                        stringResource(R.string.skin_haptics_desc), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SkinSwitch(checked = haptics, onCheckedChange = prefs::setSkinHaptics)
            }
        }
    }
}

/** Shown once when a 30-second trial runs out: keep the look with Pro, or go back to Classic. */
@Composable
fun SkinTrialEndedDialog(onGetPro: () -> Unit) {
    val ended by SkinTrial.ended.collectAsState()
    val skin = ended ?: return
    AlertDialog(
        onDismissRequest = { SkinTrial.ended.value = null },
        title = { Text(stringResource(R.string.skin_keep_title, stringResource(skin.labelRes))) },
        text = { Text(stringResource(R.string.skin_keep_body)) },
        confirmButton = {
            SkinButton(onClick = { SkinTrial.ended.value = null; onGetPro() }) { Text(stringResource(R.string.pstyle_unlock)) }
        },
        dismissButton = { TextButton(onClick = { SkinTrial.ended.value = null }) { Text(stringResource(R.string.skin_not_now)) } },
    )
}

/** Right after buying Pro (or starting the Pro trial): pick the app style straight away. */
@Composable
fun SkinWelcomeDialog(onDismiss: () -> Unit) {
    val prefs = (LocalContext.current.applicationContext as MusicApp).prefs
    val chosen by prefs.uiSkin.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skin_welcome_title)) },
        text = {
            Column {
                Text(stringResource(R.string.skin_welcome_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                SkinGrid(chosen, unlocked = true) { SkinTrial.stop(); prefs.setUiSkin(it) }
            }
        },
        confirmButton = { SkinButton(onClick = onDismiss) { Text(stringResource(R.string.skin_done)) } },
    )
}
