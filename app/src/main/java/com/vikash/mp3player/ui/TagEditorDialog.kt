package com.vikash.mp3player.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.TagWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Edit a song's title, artist and album. Saved into the MP3 itself, so other apps see it too. */
@Composable
fun TagEditorDialog(song: Song, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(song.title) }
    val unknownArtist = stringResource(R.string.library_unknown_artist)
    var artist by remember { mutableStateOf(song.artist.takeUnless { it.startsWith("<unknown") || it.startsWith("Unknown") || it == unknownArtist }.orEmpty()) }
    var album by remember { mutableStateOf(song.album.takeUnless { it.startsWith("<unknown") || it.startsWith("Unknown") }.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askedOnce by remember { mutableStateOf(false) }

    lateinit var save: () -> Unit

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) save() else { saving = false; error = context.getString(R.string.tags_needs_ok) }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else { saving = false; error = context.getString(R.string.tags_needs_storage) }
    }

    save = {
        saving = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { TagWriter.write(context, song, title, artist, album) }
            when (result) {
                TagWriter.Result.Saved -> { saving = false; onSaved() }
                is TagWriter.Result.NeedsConsent ->
                    if (askedOnce) { saving = false; error = context.getString(R.string.tags_not_allowed) }
                    else { askedOnce = true; consent.launch(IntentSenderRequest.Builder(result.sender).build()) }
                TagWriter.Result.NeedsStoragePermission ->
                    if (askedOnce) { saving = false; error = context.getString(R.string.tags_needs_storage) }
                    else { askedOnce = true; storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                is TagWriter.Result.Failed -> { saving = false; error = result.message }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.song_edit_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.tags_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text(stringResource(R.string.tags_artist)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text(stringResource(R.string.tags_album)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.tags_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { askedOnce = false; save() }, enabled = !saving && title.isNotBlank()) {
                Text(if (saving) stringResource(R.string.tags_saving) else stringResource(R.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.common_cancel)) } },
    )
}
