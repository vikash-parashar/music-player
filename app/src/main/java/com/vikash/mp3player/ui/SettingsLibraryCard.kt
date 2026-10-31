package com.vikash.mp3player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.LibraryFilter
import com.vikash.mp3player.playback.ShakeToSkip
import com.vikash.mp3player.ui.skin.SkinCard as Card
import com.vikash.mp3player.ui.skin.SkinSwitch as Switch

/** Hide short clips, voice notes and call recordings from the library. */
@Composable
fun LibraryCleanCard(onLibraryChanged: () -> Unit) {
    val context = LocalContext.current
    var minSec by remember { mutableIntStateOf(LibraryFilter.minSeconds(context)) }
    var hideRec by remember { mutableStateOf(LibraryFilter.hideRecordings(context)) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SettingRow(stringResource(R.string.library_hide_recordings_title)) {
                Switch(checked = hideRec, onCheckedChange = {
                    hideRec = it
                    LibraryFilter.setHideRecordings(context, it)
                    onLibraryChanged()
                })
            }
            Text(
                stringResource(R.string.library_hide_recordings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.library_min_length_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilter.MIN_CHOICES.forEach { sec ->
                    FilterChip(
                        selected = minSec == sec,
                        onClick = {
                            if (minSec != sec) {
                                minSec = sec
                                LibraryFilter.setMinSeconds(context, sec)
                                onLibraryChanged()
                            }
                        },
                        label = { Text(stringResource(R.string.library_min_length_secs, sec)) },
                    )
                }
            }
        }
    }
}

/** Shake the phone to skip to the next song. */
@Composable
fun ShakeToSkipCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(ShakeToSkip.enabled(context)) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SettingRow(stringResource(R.string.shake_skip_title)) {
                Switch(checked = on, onCheckedChange = {
                    on = it
                    ShakeToSkip.setEnabled(context, it)
                })
            }
            Text(
                stringResource(R.string.shake_skip_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}
