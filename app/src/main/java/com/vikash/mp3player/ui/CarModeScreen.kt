package com.vikash.mp3player.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
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

// Dashboard Gauge (picked 11 Nov 2026): night-dash navy with one sky-blue accent, like a lit speedometer.
private val CarBg = Color(0xFF0A0C10)
private val CarKey = Color(0xFF121822)
private val CarKeyEdge = Color(0xFF1E2A3A)
private val CarSky = Color(0xFF38BDF8)
private val CarSkyDeep = Color(0xFF0284C7)
private val CarTrack = Color(0xFF1A2230)
private val CarPale = Color(0xFFCFE3FF)
private val CarMuted = Color(0xFF6B7C93)
// Kept for the Car Hub card.
private val CarRed = Color(0xFFA31C1B)
private val CarGreen = Color(0xFF1F5C3F)

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
    var positionMs by remember { mutableStateOf(0L) }
    LaunchedEffect(current?.mediaId, state.isPlaying, state.durationMs) {
        while (true) {
            positionMs = player.positionMs()
            progress = (positionMs.toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
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
                Text(clock, color = CarPale, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                CarIconButton(Icons.Default.Close, stringResource(R.string.car_exit), onClick = onClose)
            }
        }
        val names: @Composable () -> Unit = {
            Column {
                if (activeMood != null) {
                    Text(
                        stringResource(R.string.car_playing_from, activeLabel).uppercase(), color = CarSky,
                        fontFamily = CarTitleFont, fontWeight = FontWeight.Medium,
                        fontSize = 11.sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    (current?.title ?: stringResource(R.string.car_nothing_playing)).uppercase(),
                    color = Color.White, fontFamily = CarTitleFont, fontWeight = FontWeight.Bold,
                    fontSize = 28.sp, lineHeight = 32.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (heard ?: current?.artist ?: stringResource(if (voiceAvailable) R.string.car_tap_mic else R.string.car_swipe_short)).uppercase(),
                    color = if (heard != null) Color(0xFFFF8A80) else CarMuted,
                    fontFamily = CarTitleFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 2.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        val gauge: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(m, contentAlignment = Alignment.Center) {
                val dial = min(min(maxWidth, maxHeight) - 8.dp, 330.dp).coerceAtLeast(64.dp)
                // A 270 degree dial like a speedometer: the lit arc is how far into the song you are.
                Canvas(Modifier.size(dial)) {
                    val w = (dial * 0.05f).toPx()
                    val inset = w / 2 + (dial * 0.02f).toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                    val tl = Offset(inset, inset)
                    drawArc(CarTrack, 135f, 270f, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                    // Soft glow under the lit arc, then the arc itself.
                    drawArc(CarSky.copy(alpha = 0.25f), 135f, 270f * progress, false, tl, arcSize, style = Stroke(w * 2.2f, cap = StrokeCap.Round))
                    drawArc(CarSky, 135f, 270f * progress, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                    // Tick marks every 10%, like the numbers on a speedometer.
                    val r = arcSize.width / 2
                    val c = Offset(size.width / 2, size.height / 2)
                    for (i in 0..10) {
                        val ang = Math.toRadians(135.0 + 27.0 * i)
                        val dx = kotlin.math.cos(ang).toFloat(); val dy = kotlin.math.sin(ang).toFloat()
                        val lit = i / 10f <= progress
                        drawLine(
                            if (lit) CarSky else CarMuted.copy(alpha = 0.6f),
                            Offset(c.x + dx * (r - w * 1.4f), c.y + dy * (r - w * 1.4f)),
                            Offset(c.x + dx * (r - w * 2.4f), c.y + dy * (r - w * 2.4f)),
                            strokeWidth = (dial * 0.008f).toPx(), cap = StrokeCap.Round,
                        )
                    }
                }
                // The album art as a spinning disc in the middle of the dial.
                SpinningCd(current?.artworkUri, dial * 0.6f, spinning = !lite)
                Text(
                    "${fmtTime(positionMs)} / ${fmtTime(state.durationMs)}",
                    color = CarSky, fontFamily = CarTitleFont, fontWeight = FontWeight.Bold,
                    fontSize = (dial.value * 0.05f).coerceIn(11f, 16f).sp, letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.Center).offset(y = dial * 0.42f),
                )
            }
        }
        val dock: @Composable (Dp) -> Unit = { keyHeight ->
            // Three big square keys; play is the widest and lit.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GaugeKey(Icons.Default.SkipPrevious, stringResource(R.string.common_previous), keyHeight, Modifier.weight(1f)) { player.previous() }
                GaugeKey(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (state.isPlaying) R.string.common_pause else R.string.common_play),
                    keyHeight, Modifier.weight(1.3f), lit = true,
                ) { player.togglePlay() }
                GaugeKey(Icons.Default.SkipNext, stringResource(R.string.common_next), keyHeight, Modifier.weight(1f)) { player.next() }
            }
        }
        val mic: @Composable () -> Unit = {
            // Hidden where the phone or head unit has no speech engine, so there's no dead button.
            if (voiceAvailable) {
                val (micSource, micPressed, micScale) = rememberCarPress()
                Row(
                    Modifier.fillMaxWidth().height(56.dp).graphicsLayer { scaleX = micScale; scaleY = micScale }
                        .clip(RoundedCornerShape(18.dp)).background(CarKey)
                        .border(1.5.dp, if (micPressed) CarSky else CarKeyEdge, RoundedCornerShape(18.dp))
                        .clickable(interactionSource = micSource, indication = null, role = Role.Button) { listen() },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Mic, null, tint = CarSky, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.car_say_song), color = CarPale, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val shortScreen = maxHeight < 480.dp
            if (maxWidth > maxHeight * 1.15f) {
                // Landscape (car head units, tablets): the dial on the left, everything you press on the right.
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    gauge(Modifier.weight(0.46f).fillMaxHeight())
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(0.54f).fillMaxHeight()) {
                        topBar()
                        Spacer(Modifier.height(6.dp))
                        names()
                        if (moods.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            CarMoodRow(moods, activeMood, bleed = 0.dp, onPick = pickMood)
                        }
                        Spacer(Modifier.weight(1f))
                        dock(if (shortScreen) 64.dp else 76.dp)
                        if (voiceAvailable) Spacer(Modifier.height(10.dp))
                        mic()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    topBar()
                    Spacer(Modifier.height(8.dp))
                    names()
                    gauge(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp))
                    if (moods.isNotEmpty()) {
                        CarMoodRow(moods, activeMood, bleed = 20.dp, onPick = pickMood)
                        Spacer(Modifier.height(14.dp))
                    }
                    dock(78.dp)
                    if (voiceAvailable) Spacer(Modifier.height(10.dp))
                    mic()
                    if (!promoClosed) {
                        Spacer(Modifier.height(10.dp))
                        CarHubPromoCard(
                            onOpen = { CarHubPromo.open(context) },
                            onClose = { promoClosed = true; CarHubPromo.close(context) },
                        )
                    } else Text(
                        stringResource(R.string.car_swipe_short), color = CarMuted, fontSize = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Narrow, bold display face for the song in Car mode (Oswald, SIL Open Font License). */
internal val CarTitleFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.oswald_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.oswald_bold, FontWeight.Bold),
)

/**
 * How every Car mode key feels under the thumb: it squashes a little with a spring, the phone gives a
 * short tick, and its ring turns from red to green while it is held, so you know it took the press
 * without looking.
 */
@Composable
private fun rememberCarPress(): Triple<MutableInteractionSource, Boolean, Float> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 900f), label = "carPress",
    )
    return Triple(source, pressed, scale)
}

/** Round dark key in the top bar (close). Its edge lights up sky blue while held. */
@Composable
private fun CarIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val (source, pressed, scale) = rememberCarPress()
    Box(
        Modifier.size(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(CarKey)
            .border(1.5.dp, if (pressed) CarSky else CarKeyEdge, CircleShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = CarPale, modifier = Modifier.size(24.dp)) }
}

/**
 * A big rounded-square dash key. [lit] is the sky-blue play key with a soft glow. Every key springs in,
 * ticks, and its edge lights up while it is held.
 */
@Composable
private fun GaugeKey(icon: ImageVector, label: String, height: Dp, modifier: Modifier, lit: Boolean = false, onClick: () -> Unit) {
    val (source, pressed, scale) = rememberCarPress()
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (lit) Modifier.shadow(if (pressed) 6.dp else 16.dp, shape, ambientColor = CarSky, spotColor = CarSky) else Modifier)
            .clip(shape)
            .background(if (lit) Brush.verticalGradient(listOf(CarSky, CarSkyDeep)) else Brush.verticalGradient(listOf(CarKey, CarKey)))
            .border(1.5.dp, if (pressed) (if (lit) Color.White else CarSky) else (if (lit) Color.Transparent else CarKeyEdge), shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (lit) Color(0xFF04111C) else CarPale, modifier = Modifier.size(height * 0.46f)) }
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
