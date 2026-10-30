package com.vikash.mp3player.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import com.vikash.mp3player.ui.skin.SkinButton as Button
import com.vikash.mp3player.ui.skin.SkinCard as Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.AudioExtractor

/** Settings card for Ampli Convert: pick one of your videos and keep its sound as a music file. */
@Composable
fun ConvertCard(onConverted: () -> Unit) {
    var picked by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) picked = uri }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.convert_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.convert_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { picker.launch("video/*") }) { Text(stringResource(R.string.convert_pick)) }
        }
    }
    picked?.let { uri -> ConvertDialog(uri, onDone = { saved -> picked = null; if (saved) onConverted() }) }
}

@Composable
private fun ConvertDialog(video: Uri, onDone: (saved: Boolean) -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<AudioExtractor.Result?>(null) }
    LaunchedEffect(video) {
        result = AudioExtractor.extract(context, video) { progress = it }
    }
    val r = result
    AlertDialog(
        onDismissRequest = { if (r != null) onDone(r is AudioExtractor.Result.Saved) },
        title = { Text(stringResource(R.string.convert_title)) },
        text = {
            Column {
                when (r) {
                    null -> {
                        Text(stringResource(R.string.convert_working))
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is AudioExtractor.Result.Saved -> Text(stringResource(R.string.convert_saved, r.name, AudioExtractor.FOLDER))
                    AudioExtractor.Result.NoSound -> Text(stringResource(R.string.convert_no_sound))
                    AudioExtractor.Result.Unsupported -> Text(stringResource(R.string.convert_unsupported))
                    is AudioExtractor.Result.Failed -> Text(stringResource(R.string.convert_failed, r.message))
                }
            }
        },
        confirmButton = {
            // Closing while it works cancels the copy (the half-made file is removed).
            TextButton(onClick = { onDone(r is AudioExtractor.Result.Saved) }) {
                Text(if (r == null) stringResource(R.string.common_cancel) else stringResource(R.string.common_done))
            }
        },
    )
}
