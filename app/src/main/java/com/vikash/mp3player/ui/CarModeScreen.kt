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
import com.vikash.mp3player.data.HinglishSearch
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.StatsData
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.min
import com.vikash.mp3player.playback.PlayerConnection
import com.vikash.mp3player.playback.PlayerUiState
import kotlin.math.abs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay

// Crimson Night: red, black and green only.
private val CarBg = Color(0xFF06110C)
private val CarRed = Color(0xFFA31C1B)
private val CarGreen = Color(0xFF1F5C3F)
private val CarPale = Color(0xFFC9DDD0)

/**
 * Car mode: a screen you can use without looking. Huge buttons, swipe anywhere left or right
 * to change songs, and a big mic to say "Arijit Singh" or a song name.
 */
@Composable
fun CarModeScreen(
    state: PlayerUiState,
    player: PlayerConnection,
    songs: List<Song>,
    favorites: List<String>,
    stats: StatsData,
    onPlay: (songs: List<Song>, startIndex: Int, source: String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var heard by remember { mutableStateOf<String?>(null) }
    // The card last tapped, shown as "Playing" until another song source takes over.
    var activeMood by remember { mutableStateOf<CarMoodKind?>(null) }
    var activeLabel by remember { mutableStateOf("") }

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
        match?.let { activeMood = null; onPlay(it.songs, 0, it.label) }
    }

    fun listen() {
        try {
            voice.launch(voiceSearchIntent(context))
        } catch (e: ActivityNotFoundException) {
            heard = context.getString(R.string.car_voice_unavailable)
        }
    }

    val current = state.current
    var promoClosed by remember { mutableStateOf(CarHubPromo.isClosed(context)) }
    // Song progress for the ring and the thin bar; twice a second is plenty for a glance.
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(current?.mediaId, state.isPlaying, state.durationMs) {
        while (true) {
            progress = (player.positionMs().toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            delay(500)
        }
    }
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = android.text.format.DateFormat.getTimeFormat(context)
        while (true) { clock = fmt.format(java.util.Date()); delay(15_000) }
    }
    val lite = LocalLiteMode.current
    // Many car head units have no speech engine; then the mic button is left out.
    val voiceAvailable = remember {
        context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
    }
    val unknownArtist = stringResource(R.string.library_unknown_artist)
    val moods = remember(songs, favorites, stats) { CarMoods.build(songs, favorites, stats, unknownArtist) }
    LaunchedEffect(current?.mediaId, moods) {
        val id = current?.mediaId ?: return@LaunchedEffect
        val mood = moods.firstOrNull { it.kind == activeMood } ?: return@LaunchedEffect
        if (mood.songs.none { it.id == id }) activeMood = null // something else is playing now
    }
    Box(
        Modifier.fillMaxSize().background(CarBg)
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
        val pickMood: (CarMood, String) -> Unit = { mood, label ->
            activeMood = mood.kind
            activeLabel = label
            heard = null
            onPlay(if (mood.shuffle) mood.songs.shuffled() else mood.songs, 0, label)
        }
        val topBar: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(clock, color = CarPale, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                CarIconButton(Icons.Default.Close, stringResource(R.string.car_exit), onClick = onClose)
            }
        }
        val stage: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(m, contentAlignment = Alignment.Center) {
                // The CD shrinks to fit when the cards and the Car Hub card take room.
                val ring = min(min(maxWidth, maxHeight) - 12.dp, 320.dp).coerceAtLeast(48.dp)
                // Thin ring that fills as the song plays.
                Canvas(Modifier.size(ring)) {
                    val w = 2.dp.toPx()
                    drawCircle(CarGreen.copy(alpha = 0.5f), style = Stroke(w))
                    drawArc(CarRed, -90f, 360f * progress, false, style = Stroke(w, cap = StrokeCap.Round))
                }
                // A CD with the album art as its label. It stays put and only spins with the music.
                SpinningCd(current?.artworkUri, ring * 0.88f, spinning = !lite)
            }
        }
        val info: @Composable () -> Unit = {
            Column {
                if (activeMood != null) {
                    Text(
                        stringResource(R.string.car_playing_from, activeLabel), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    )
                }
                Text(
                    current?.title ?: stringResource(R.string.car_nothing_playing),
                    color = CarPale, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    heard ?: current?.artist ?: stringResource(if (voiceAvailable) R.string.car_tap_mic else R.string.car_swipe_short),
                    color = if (heard != null) Color(0xFFE57373) else CarPale.copy(alpha = 0.65f), fontSize = 17.sp,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
                Box(
                    Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp).height(4.dp)
                        .clip(CircleShape).background(CarGreen.copy(alpha = 0.45f)),
                ) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(CarRed))
                }
            }
        }
        val dock: @Composable (Dp) -> Unit = { playSize ->
            // One dock: previous, play/pause, next.
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xCC0B0B0B))
                    .border(1.dp, CarGreen.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockKey(Icons.Default.SkipPrevious, stringResource(R.string.common_previous)) { player.previous() }
                BoldRoundButton(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (state.isPlaying) R.string.common_pause else R.string.common_play),
                    playSize, highlight = true,
                ) { player.togglePlay() }
                DockKey(Icons.Default.SkipNext, stringResource(R.string.common_next)) { player.next() }
            }
        }
        val mic: @Composable () -> Unit = {
            // Hidden where the phone or head unit has no speech engine, so there's no dead button.
            if (voiceAvailable) Row(
                Modifier.fillMaxWidth().height(60.dp).clip(CircleShape)
                    .background(CarGreen).border(1.dp, Color(0xFF2E7D57), CircleShape)
                    .clickable { listen() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Mic, null, tint = CarPale, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.car_say_song), color = CarPale, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        val headline: @Composable (Float) -> Unit = { scale ->
            Column {
                Text(
                    stringResource(R.string.car_headline_top), color = CarRed, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Black, fontSize = (54 * scale).sp, lineHeight = (52 * scale).sp, maxLines = 1,
                )
                Text(
                    stringResource(R.string.car_headline_bottom).uppercase(), color = CarPale,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = FontWeight.Bold,
                    fontSize = (24 * scale).sp, lineHeight = (26 * scale).sp, maxLines = 1,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight * 1.15f
            // A green coupe under a red light beam; the controls sit on the dark side of the picture.
            Image(
                painterResource(R.drawable.car_hero), null,
                contentScale = ContentScale.Crop,
                alignment = if (landscape) Alignment.CenterStart else Alignment.TopCenter,
                modifier = if (landscape) Modifier.fillMaxHeight().fillMaxWidth(0.72f)
                    else Modifier.fillMaxWidth().fillMaxHeight(0.42f),
            )
            Box(
                Modifier.fillMaxSize().background(
                    if (landscape) Brush.horizontalGradient(0f to Color.Transparent, 0.42f to CarBg.copy(alpha = 0.55f), 0.62f to CarBg.copy(alpha = 0.92f), 1f to CarBg)
                    else Brush.verticalGradient(0f to CarBg.copy(alpha = 0.35f), 0.3f to Color.Transparent, 0.42f to CarBg.copy(alpha = 0.85f), 0.5f to CarBg),
                ),
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val shortScreen = maxHeight < 480.dp
            if (maxWidth > maxHeight * 1.15f) {
                // Landscape (car head units, tablets): the car on the left, everything you press on the right.
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(0.44f))
                    Column(Modifier.weight(0.56f).fillMaxHeight()) {
                        topBar()
                        if (!shortScreen) headline(0.9f)
                        if (moods.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            CarMoodRow(moods, activeMood, bleed = 0.dp, onPick = pickMood)
                        }
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            stage(Modifier.size(if (shortScreen) 84.dp else 112.dp))
                            Spacer(Modifier.width(14.dp))
                            Box(Modifier.weight(1f)) { info() }
                        }
                        Spacer(Modifier.height(14.dp))
                        dock(if (shortScreen) 72.dp else 88.dp)
                        if (voiceAvailable) Spacer(Modifier.height(12.dp))
                        mic()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    topBar()
                    if (!promoClosed) {
                        Spacer(Modifier.height(10.dp))
                        CarHubPromoCard(
                            onOpen = { CarHubPromo.open(context) },
                            onClose = { promoClosed = true; CarHubPromo.close(context) },
                        )
                    }
                    if (moods.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        CarMoodRow(moods, activeMood, bleed = 20.dp, onPick = pickMood)
                    }
                    // The car shows through here; the headline sits at its foot.
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomStart) { headline(1f) }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        stage(Modifier.size(104.dp))
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) { info() }
                    }
                    Spacer(Modifier.height(18.dp))
                    dock(96.dp)
                    if (voiceAvailable) Spacer(Modifier.height(14.dp))
                    mic()
                    Text(
                        stringResource(R.string.car_swipe_short), color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

/** Round dark key in the top bar (buddy, close). */
@Composable
private fun CarIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Color(0xCC0B0B0B)).border(1.dp, CarGreen.copy(alpha = 0.55f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = CarPale, modifier = Modifier.size(26.dp)) }
}

/** Previous / next inside the dock: a big icon with a large touch area, no extra chrome. */
@Composable
private fun DockKey(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(76.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = CarPale, modifier = Modifier.size(42.dp))
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
                .background(if (highlight) Color(0xFF4A0B0B) else Color(0xFF050505)),
        )
        Box(
            Modifier.offset(y = sink).size(size).clip(CircleShape)
                .background(
                    if (highlight) Brush.radialGradient(listOf(Color(0xFFD42A27), CarRed, Color(0xFF6E1010)), center = Offset.Unspecified)
                    else Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0B0B0B))),
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
            .background(Color(0xCC0B0B0B))
            .border(1.dp, CarGreen.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ampli_carhub_icon), null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("Ampli Car Hub", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.carhub_promo_line), color = CarPale, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.more_apps_coming_soon), color = CarRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

/**
 * The phone's speech recogniser, asked for a few guesses. Indian English first with Hindi as a
 * second language, so both "Arijit Singh" and "तुम ही हो" come back usable.
 */
internal fun voiceSearchIntent(context: Context): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        .putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN")) // read by Google speech
        .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.car_voice_prompt))
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

/** Turns what the driver said into songs to play: an artist, an album or a song title. */
internal object VoiceMatch {
    data class Match(val label: String, val songs: List<Song>)

    private val filler = Regex("^(play|please play|chalao|bajao|sunao|gaana|song|songs|music)\\s+|\\s+(chalao|bajao|sunao|ke gaane|ka gaana|songs|song)$")
    private val playWords = Regex("^(play|please play)\\s|\\s(chalao|bajao|sunao|चलाओ|बजाओ|सुनाओ)$")

    /** What was said without "play" / "chalao" and the like: the words to search for. */
    fun clean(raw: String): String = filler.replace(raw.trim().lowercase(), "").trim()

    /** "Play Arijit" or "Arijit ke gaane chalao": the user wants music now, not just a list. */
    fun wantsPlay(raw: String): Boolean = playWords.containsMatchIn(raw.trim().lowercase())

    fun find(phrases: List<String>, songs: List<Song>): Match? {
        if (songs.isEmpty()) return null
        for (raw in phrases) {
            val q = clean(raw)
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
            // Sounds alike: "तुम ही हो" (Hindi speech) finds "Tum Hi Ho", and Hinglish spellings fold together.
            val key = HinglishSearch.key(q)
            if (key.length >= 2) {
                songs.firstOrNull { HinglishSearch.key("${it.title} ${it.artist}").contains(key) }
                    ?.let { return Match(it.title, listOf(it)) }
            }
        }
        return null
    }
}
