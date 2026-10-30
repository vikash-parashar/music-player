package com.vikash.mp3player.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import com.vikash.mp3player.ui.skin.SkinButton as Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.SongMover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Moves [songs] to a folder the user picks from their music folders or types in. */
@Composable
fun MoveSongsDialog(songs: List<Song>, folders: List<String>, onDismiss: () -> Unit, onMoved: (count: Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val choices = remember(folders) { folders.filter { SongMover.canMoveTo(it) }.distinct().sortedBy { it.lowercase() } }
    var picked by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("Music/") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val target = SongMover.normalize(picked ?: typed)
    val valid = target.contains('/') && SongMover.canMoveTo(target)

    lateinit var start: () -> Unit
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) start() else { working = false; error = context.getString(R.string.move_needs_ok) }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else { working = false; error = context.getString(R.string.delete_needs_storage) }
    }
    var consentAsked by remember { mutableStateOf(false) }
    var storageAsked by remember { mutableStateOf(false) }
    start = {
        working = true
        error = null
        scope.launch {
            // Android 11+: one prompt for all files before moving.
            val upfront = if (!consentAsked) SongMover.consentRequest(context, songs) else null
            if (upfront != null) {
                consentAsked = true
                consent.launch(IntentSenderRequest.Builder(upfront).build())
                return@launch
            }
            when (val r = withContext(Dispatchers.IO) { SongMover.move(context, songs, target) }) {
                is SongMover.Result.Moved -> onMoved(r.count)
                is SongMover.Result.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(r.sender).build())
                SongMover.Result.NeedsStoragePermission ->
                    if (storageAsked) { working = false; error = context.getString(R.string.delete_needs_storage) }
                    else { storageAsked = true; storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                is SongMover.Result.Failed -> { working = false; error = context.getString(R.string.move_failed) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(pluralStringResource(R.plurals.move_title, songs.size, songs.size)) },
        text = {
            Column {
                if (choices.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(choices.size) { i ->
                            val f = choices[i]
                            Row(Modifier.fillMaxWidth().clickable { picked = f }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = picked == f, onClick = { picked = f })
                                Text(f, maxLines = 1)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { picked = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = picked == null, onClick = { picked = null })
                    Text(stringResource(R.string.move_new_folder))
                }
                if (picked == null) OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(120) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    supportingText = { Text(stringResource(R.string.move_folder_hint)) },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = { consentAsked = false; storageAsked = false; start() }, enabled = valid && !working) {
                Text(if (working) stringResource(R.string.move_moving) else stringResource(R.string.move_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.common_cancel)) } },
    )
}
