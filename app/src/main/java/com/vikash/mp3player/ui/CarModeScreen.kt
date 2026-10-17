package com.vikash.mp3player.ui

import android.app.Activity
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
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
    var buddy by remember { mutableStateOf(Buddy.load(context)) }
    var promoClosed by remember { mutableStateOf(CarHubPromo.isClosed(context)) }
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
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.car_mode), color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.car_exit), tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            if (!promoClosed) {
                CarHubPromoCard(
                    onOpen = { CarHubPromo.open(context) },
                    onClose = { promoClosed = true; CarHubPromo.close(context) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                current?.title ?: stringResource(R.string.car_nothing_playing),
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                heard ?: current?.artist ?: stringResource(R.string.car_tap_mic),
                color = if (heard != null) Ampli.Rose else Color.White.copy(alpha = 0.7f), fontSize = 18.sp,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val b = buddy
                if (b != null) {
                    DanceBuddy(b, state.isPlaying, current?.mediaId, Modifier.fillMaxSize().padding(top = 8.dp))
                } else {
                    Text(stringResource(R.string.car_swipe_hint), color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            // Buddy picker, all options visible at once.
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                (Buddy.entries + listOf<Buddy?>(null)).forEach { option ->
                    val on = option == buddy
                    Text(
                        stringResource(option?.label ?: R.string.buddy_off),
                        color = if (on) Color.White else Color(0xFFCFC6CC), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (on) Ampli.Rose.copy(alpha = 0.3f) else Color(0xFF16131A))
                            .border(1.dp, if (on) Color(0xFFFDA4AF) else Color(0xFF2A2430), CircleShape)
                            .clickable { buddy = option; Buddy.save(context, option) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                BoldRoundButton(Icons.Default.SkipPrevious, stringResource(R.string.common_previous), 88.dp) { player.previous() }
                BoldRoundButton(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (state.isPlaying) R.string.common_pause else R.string.common_play),
                    116.dp, highlight = true,
                ) { player.togglePlay() }
                BoldRoundButton(Icons.Default.SkipNext, stringResource(R.string.common_next), 88.dp) { player.next() }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BoldRoundButton(Icons.Default.Mic, stringResource(R.string.car_voice_search), 72.dp, iconTint = Color(0xFF67E8F9)) { listen() }
            }
        }
    }
}

/**
 * A big round key with a darker rim underneath, so it looks raised; it sinks a little when pressed.
 * [highlight] is the rose play button.
 */
@Composable
private fun BoldRoundButton(
    icon: ImageVector,
    label: String,
    size: Dp,
    highlight: Boolean = false,
    iconTint: Color = Color.White,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val rim = 5.dp
    val sink = if (pressed) 3.dp else 0.dp
    Box(Modifier.size(size, size + rim)) {
        Box(
            Modifier.align(Alignment.BottomCenter).size(size).clip(CircleShape)
                .background(if (highlight) Color(0xFF7F1028) else Color(0xFF0B090D)),
        )
        Box(
            Modifier.offset(y = sink).size(size).clip(CircleShape)
                .background(
                    if (highlight) Brush.radialGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFF9F1239)), center = Offset.Unspecified)
                    else Brush.verticalGradient(listOf(Color(0xFF2A2430), Color(0xFF1A161E))),
                )
                .clickable(interactionSource = source, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = iconTint, modifier = Modifier.size(size * 0.46f)) }
    }
}

/** Small card telling drivers about Ampli Car Hub. Closing it keeps it closed. */
@Composable
private fun CarHubPromoCard(onOpen: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0x380891B2), Color(0x140891B2))))
            .border(1.dp, Color(0x3867E8F9), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ampli_carhub_icon), null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("Ampli Car Hub", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.carhub_promo_line), color = Color(0xFFCFFAFE), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.more_apps_coming_soon), color = Color(0xFF67E8F9), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

internal object CarHubPromo {
    private const val KEY = "carhub_promo_closed"
    private fun prefs(c: Context) = c.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
    fun isClosed(c: Context) = prefs(c).getBoolean(KEY, false)
    fun close(c: Context) = prefs(c).edit().putBoolean(KEY, true).apply()
    fun open(c: Context) {
        try {
            c.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(MoreApps.CARHUB_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) { }
    }
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
