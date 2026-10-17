package com.vikash.mp3player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vikash.mp3player.recognize.SongRecognizer
import com.vikash.mp3player.ui.theme.Ampli

private sealed interface RecognizeState {
    data object Idle : RecognizeState
    data object Listening : RecognizeState
    data object Searching : RecognizeState
    data class Done(val result: SongRecognizer.Result) : RecognizeState
}

/** "Name that song": hold the phone near the music, get the title and links to official apps. */
@Composable
fun RecognizeDialog(isPro: Boolean, onGetPro: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    if (!isPro) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Hearing, null) },
            title = { Text(stringResource(R.string.recognize_title)) },
            text = { Text(stringResource(R.string.recognize_pro_text)) },
            confirmButton = { Button(onClick = { onDismiss(); onGetPro() }) { Text(stringResource(R.string.pro_get)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_not_now)) } },
        )
        return
    }
    if (!SongRecognizer.isConfigured) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Hearing, null) },
            title = { Text(stringResource(R.string.recognize_title)) },
            text = { Text(stringResource(R.string.recognize_coming_soon)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
        )
        return
    }

    var state by remember { mutableStateOf<RecognizeState>(RecognizeState.Idle) }
    var attempt by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) attempt++ else denied = true
    }
    fun start() {
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (has) attempt++ else mic.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { start() }
    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        state = RecognizeState.Listening
        val sample = SongRecognizer.record(context)
        if (sample == null) {
            state = RecognizeState.Done(SongRecognizer.Result.Failed(R.string.recognize_mic_failed))
            return@LaunchedEffect
        }
        state = RecognizeState.Searching
        state = RecognizeState.Done(SongRecognizer.identify(sample))
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recognize_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val s = state) {
                    RecognizeState.Idle, RecognizeState.Listening, RecognizeState.Searching -> {
                        if (denied) {
                            Text(stringResource(R.string.recognize_mic_denied), textAlign = TextAlign.Center)
                        } else {
                            Pulse()
                            Text(
                                if (s == RecognizeState.Searching) stringResource(R.string.recognize_searching) else stringResource(R.string.recognize_listening),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    is RecognizeState.Done -> when (val r = s.result) {
                        is SongRecognizer.Result.Found -> {
                            val m = r.match
                            Text(m.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(m.artist, textAlign = TextAlign.Center)
                            m.album?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
                            val q = Uri.encode("${m.title} ${m.artist}")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { open("https://www.jiosaavn.com/search/$q") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.recognize_open_in, "JioSaavn")) }
                                OutlinedButton(onClick = { open(m.spotifyUrl ?: "https://open.spotify.com/search/$q") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.recognize_open_in, "Spotify")) }
                                OutlinedButton(onClick = { open("https://music.youtube.com/search?q=$q") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.recognize_open_in, "YouTube Music")) }
                                m.appleMusicUrl?.let { url ->
                                    OutlinedButton(onClick = { open(url) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.recognize_open_in, "Apple Music")) }
                                }
                            }
                        }
                        SongRecognizer.Result.NotFound ->
                            Text(stringResource(R.string.recognize_not_found), textAlign = TextAlign.Center)
                        is SongRecognizer.Result.Failed -> Text(stringResource(r.messageRes), textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {
            val done = state is RecognizeState.Done || denied
            if (done) Button(onClick = { denied = false; state = RecognizeState.Idle; start() }) { Text(stringResource(R.string.recognize_listen_again)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun Pulse() {
    // Lite mode: the circle stays still.
    val scaleState: State<Float> = if (LocalLiteMode.current) remember { mutableFloatStateOf(1f) } else
        rememberInfiniteTransition(label = "pulse").animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    val scale by scaleState
    Box(Modifier.size(96.dp).scale(scale).clip(CircleShape).background(Ampli.Gradient), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Hearing, null, tint = Color.White, modifier = Modifier.size(44.dp))
    }
}
