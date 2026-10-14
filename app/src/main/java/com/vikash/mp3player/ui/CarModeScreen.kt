package com.vikash.mp3player.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.playback.PlayerConnection
import com.vikash.mp3player.playback.PlayerUiState
import com.vikash.mp3player.ui.theme.Ampli
import kotlin.math.abs

private val CarBg = Color(0xFF0B0A10)

/**
 * Car mode: a screen you can use without looking. Huge buttons, swipe anywhere left or right
 * to change songs, and a big mic to say "Arijit Singh" or a song name.
 */
@Composable
fun CarModeScreen(
    state: PlayerUiState,
    player: PlayerConnection,
    songs: List<Song>,
    onPlay: (songs: List<Song>, startIndex: Int, source: String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var heard by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val phrases = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        if (result.resultCode != Activity.RESULT_OK || phrases.isEmpty()) return@rememberLauncherForActivityResult
        val match = VoiceMatch.find(phrases, songs)
        heard = if (match == null) context.getString(R.string.car_not_found, phrases.first())
            else context.getString(R.string.car_playing, match.label)
        match?.let { onPlay(it.songs, 0, it.label) }
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.car_voice_prompt))
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        try {
            voice.launch(intent)
        } catch (e: ActivityNotFoundException) {
            heard = context.getString(R.string.car_voice_unavailable)
        }
    }

    val current = state.current
    Box(
        Modifier.fillMaxSize().background(CarBg).systemBarsPadding()
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > 120f) { if (total < 0) player.next() else player.previous() }
                    },
                ) { _, dx -> total += dx }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.car_mode), color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.car_exit), tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    current?.title ?: stringResource(R.string.car_nothing_playing),
                    color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    current?.artist ?: stringResource(R.string.car_tap_mic),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                heard?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Ampli.Rose, fontSize = 18.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.car_swipe_hint), color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
            }
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(Icons.Default.SkipPrevious, stringResource(R.string.common_previous), Modifier.weight(1f)) { player.previous() }
                BigButton(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (state.isPlaying) R.string.common_pause else R.string.common_play),
                    Modifier.weight(1.3f), highlight = true,
                ) { player.togglePlay() }
                BigButton(Icons.Default.SkipNext, stringResource(R.string.common_next), Modifier.weight(1f)) { player.next() }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(Ampli.Gradient).clickable { listen() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Mic, stringResource(R.string.car_voice_search), tint = Color.White, modifier = Modifier.size(48.dp)) }
            }
        }
    }
}

@Composable
private fun BigButton(icon: ImageVector, label: String, modifier: Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(28.dp))
            .background(if (highlight) Ampli.Gradient else androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.08f)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(72.dp)) }
}

/** Turns what the driver said into songs to play: an artist, an album or a song title. */
internal object VoiceMatch {
    data class Match(val label: String, val songs: List<Song>)

    private val filler = Regex("^(play|please play|chalao|bajao|sunao|gaana|song|songs|music)\\s+|\\s+(chalao|bajao|sunao|ke gaane|ka gaana|songs|song)$")

    fun find(phrases: List<String>, songs: List<Song>): Match? {
        if (songs.isEmpty()) return null
        for (raw in phrases) {
            val q = filler.replace(raw.trim().lowercase(), "").trim()
            if (q.length < 2) continue
            songs.filter { it.artist.lowercase().contains(q) }.takeIf { it.isNotEmpty() }
                ?.let { return Match(it.first().artist, it.shuffled()) }
            songs.filter { it.album.isNotBlank() && it.album.lowercase().contains(q) }.takeIf { it.isNotEmpty() }
                ?.let { return Match(it.first().album, it) }
            val byTitle = songs.filter { it.title.lowercase().contains(q) }
            if (byTitle.isNotEmpty()) {
                val first = byTitle.first()
                return Match(first.title, listOf(first) + songs.filter { it.id != first.id && it.artist == first.artist }.shuffled())
            }
            // Loose match: every spoken word appears in the title or artist.
            val words = q.split(' ').filter { it.length > 1 }
            songs.firstOrNull { s -> val hay = "${s.title} ${s.artist}".lowercase(); words.all { it in hay } }
                ?.let { return Match(it.title, listOf(it)) }
        }
        return null
    }
}
