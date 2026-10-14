package com.vikash.mp3player.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.SongDeleter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deletes [songs] from the phone. On Android 11+ this goes straight to Android's own
 * confirmation (it lists the files and deletes them); older phones get Ampli's confirmation.
 */
@Composable
fun DeleteSongsDialog(songs: List<Song>, onDismiss: () -> Unit, onDeleted: (count: Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askedOnce by remember { mutableStateOf(false) }
    var deletesItself by remember { mutableStateOf(false) }

    lateinit var start: () -> Unit

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        when {
            result.resultCode != Activity.RESULT_OK -> if (SongDeleter.systemConfirms) onDismiss() else { working = false; error = context.getString(R.string.delete_needs_ok) }
            deletesItself -> onDeleted(songs.size)
            else -> start()
        }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else { working = false; error = context.getString(R.string.delete_needs_storage) }
    }

    start = {
        working = true
        error = null
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { SongDeleter.delete(context, songs) }) {
                is SongDeleter.Result.Deleted -> onDeleted(result.count)
                is SongDeleter.Result.NeedsConsent -> {
                    deletesItself = result.deletesItself
                    consent.launch(IntentSenderRequest.Builder(result.sender).build())
                }
                SongDeleter.Result.NeedsStoragePermission ->
                    if (askedOnce) { working = false; error = context.getString(R.string.delete_needs_storage) }
                    else { askedOnce = true; storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                is SongDeleter.Result.Failed -> if (SongDeleter.systemConfirms) onDismiss() else { working = false; error = result.message }
            }
        }
    }

    if (SongDeleter.systemConfirms) {
        LaunchedEffect(Unit) { start() }
        return
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(pluralStringResource(R.plurals.delete_title, songs.size, songs.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                songs.take(5).forEach { Text("• ${it.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (songs.size > 5) Text(stringResource(R.string.delete_and_more, songs.size - 5))
                Text(
                    stringResource(R.string.delete_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { askedOnce = false; start() },
                enabled = !working,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(if (working) stringResource(R.string.delete_deleting) else stringResource(R.string.common_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.common_cancel)) } },
    )
}
