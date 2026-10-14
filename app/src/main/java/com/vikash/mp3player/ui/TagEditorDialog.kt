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
    var artist by remember { mutableStateOf(song.artist.takeUnless { it.startsWith("<unknown") || it.startsWith("Unknown") }.orEmpty()) }
    var album by remember { mutableStateOf(song.album.takeUnless { it.startsWith("<unknown") || it.startsWith("Unknown") }.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askedOnce by remember { mutableStateOf(false) }

    lateinit var save: () -> Unit

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) save() else { saving = false; error = "Ampli needs your OK to change this file." }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else { saving = false; error = "Ampli needs storage access to change this file." }
    }

    save = {
        saving = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { TagWriter.write(context, song, title, artist, album) }
            when (result) {
                TagWriter.Result.Saved -> { saving = false; onSaved() }
                is TagWriter.Result.NeedsConsent ->
                    if (askedOnce) { saving = false; error = "Android didn't allow editing this file." }
                    else { askedOnce = true; consent.launch(IntentSenderRequest.Builder(result.sender).build()) }
                TagWriter.Result.NeedsStoragePermission ->
                    if (askedOnce) { saving = false; error = "Ampli needs storage access to change this file." }
                    else { askedOnce = true; storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                is TagWriter.Result.Failed -> { saving = false; error = result.message }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit song info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "Saved inside the MP3 file, so it shows up the same in every app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { askedOnce = false; save() }, enabled = !saving && title.isNotBlank()) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}
