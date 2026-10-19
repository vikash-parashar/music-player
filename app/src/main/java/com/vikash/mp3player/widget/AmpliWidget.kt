package com.vikash.mp3player.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.vikash.mp3player.MainActivity
import com.vikash.mp3player.MusicApp
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.PlaybackService
import com.vikash.mp3player.playback.loadSavedQueue
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Home-screen widget: the current song on a vinyl disc, with previous, play/pause and next.
 * [PlaybackService] writes what's playing with [WidgetState.update]; the buttons talk to the
 * service through a short-lived MediaController.
 *
 * Five layouts, picked by the widget's size ([SizeMode.Responsive]):
 * - SMALL (2x1): disc + play/pause, centred; tapping anywhere else opens the app.
 * - COMPACT (3x1): disc, title (with dancing bars) and artist, play/pause.
 * - WIDE (4x1): disc, title and artist, previous / play-pause / next.
 * - MEDIUM (4x2): big disc on the left; title, artist, elapsed time and the buttons centred beside it.
 * - TALL (4x3): disc with title, artist, up next and time beside it, bigger buttons centred below.
 *
 * Every animation is a framework drawable driven by an indeterminate ProgressBar inside the
 * RemoteViews (spinning disc, dancing bars, glow ring), and the elapsed time is a Chronometer, so
 * the launcher animates them while they're on screen and the app is never woken to redraw. None of
 * them run while paused or in Lite mode (the time still ticks in Lite mode). The card is tinted
 * with two dark colours taken from the album art.
 */
class AmpliWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, COMPACT, WIDE, MEDIUM, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            // A running Glance session doesn't call provideGlance again on update, so follow the
            // saved state here; otherwise a quick play then pause would leave the widget stale.
            val change by WidgetState.changes.collectAsState()
            val look by produceState(first, change) {
                if (change != value.change) value = load(context)
            }
            Content(look)
        }
    }

    /** Everything one frame of the widget needs, read outside composition. */
    private class Look(
        val s: WidgetState,
        val cover: Cover?,
        /** False while paused or in Lite mode: the disc, bars and glow rest. */
        val animate: Boolean,
        val change: Int,
    )

    /** The album art as a round disc label, and the two background colours taken from it. */
    private class Cover(val label: Bitmap, val start: Int, val end: Int)

    /**
     * Reads the saved state, Lite mode and the cover. Glance composes the content once per layout
     * size, so the result is shared until the state changes again.
     */
    private suspend fun load(context: Context): Look = loadLock.withLock {
        val change = WidgetState.changes.value
        lastLook?.let { if (it.change == change) return@withLock it }
        val s = WidgetState.read(context)
        val lite = runCatching { (context.applicationContext as MusicApp).prefs.liteMode.value }.getOrDefault(false)
        Look(s, cover(context, s.art), animate = s.playing && !lite, change = change).also { lastLook = it }
    }

    @Composable
    private fun Content(look: Look) {
        val size = LocalSize.current
        Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>())) {
            Background(look.cover)
            when {
                size.width >= TALL.width && size.height >= TALL.height -> Tall(look)
                size.width >= MEDIUM.width && size.height >= MEDIUM.height -> Medium(look)
                size.width >= WIDE.width -> Wide(look)
                size.width >= COMPACT.width -> Compact(look)
                else -> Small(look)
            }
        }
    }

    /** 2x1: disc and play/pause, centred; the rest of the widget opens the app. */
    @Composable
    private fun Small(look: Look) {
        Row(
            GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play's glow square leaves 6dp clear on each side of the chip; this balances the right one.
            Spacer(GlanceModifier.width(6.dp))
            Disc(look, 44.dp)
            Spacer(GlanceModifier.width(10.dp))
            GlowPlay(look, 36.dp, 9.dp)
        }
    }

    /** 3x1: disc, title (with bars) and artist, then play/pause. */
    @Composable
    private fun Compact(look: Look) {
        Row(
            // 6dp at the end: play's glow square adds the other 6, so the chip sits 12dp in, like the disc.
            GlanceModifier.fillMaxSize().padding(start = 12.dp, top = 4.dp, end = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Disc(look, 44.dp)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                TitleLine(look, titleSp = 14, centred = false)
                ArtistLine(look.s, artistSp = 12, centred = false)
            }
            Spacer(GlanceModifier.width(6.dp))
            GlowPlay(look, 36.dp, 9.dp)
        }
    }

    /** 4x1: disc, title (with bars) and artist, previous / play-pause / next. */
    @Composable
    private fun Wide(look: Look) {
        Row(
            GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Disc(look, 44.dp)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                TitleLine(look, titleSp = 15, centred = false)
                ArtistLine(look.s, artistSp = 12, centred = false)
            }
            Spacer(GlanceModifier.width(4.dp))
            SideButton(R.drawable.ic_widget_prev, R.string.common_previous, CMD_PREV, 36.dp, 8.dp)
            GlowPlay(look, 36.dp, 9.dp)
            SideButton(R.drawable.ic_widget_next, R.string.common_next, CMD_NEXT, 36.dp, 8.dp)
        }
    }

    /** 4x2: big disc on the left; title, artist, time and buttons centred on one axis beside it. */
    @Composable
    private fun Medium(look: Look) {
        Row(
            GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Disc(look, 84.dp)
            Spacer(GlanceModifier.width(12.dp))
            Column(
                GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TitleLine(look, titleSp = 14, centred = true)
                ArtistLine(look.s, artistSp = 12, centred = true)
                Spacer(GlanceModifier.height(2.dp))
                Elapsed(look.s, textSp = 11f)
                Spacer(GlanceModifier.height(2.dp))
                Controls(look, side = 30.dp, sidePad = 6.dp, play = 34.dp, playPad = 8.dp, gap = 6.dp)
            }
        }
    }

    /** 4x3: disc with the song details beside it, bigger buttons centred across the full width below. */
    @Composable
    private fun Tall(look: Look) {
        Column(
            GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Disc(look, 88.dp)
                Spacer(GlanceModifier.width(14.dp))
                Column(
                    GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TitleLine(look, titleSp = 16, centred = true)
                    ArtistLine(look.s, artistSp = 13, centred = true)
                    UpNextLine(look.s, nextSp = 12)
                    Spacer(GlanceModifier.height(4.dp))
                    Elapsed(look.s, textSp = 12f)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Controls(look, side = 40.dp, sidePad = 9.dp, play = 46.dp, playPad = 12.dp, gap = 14.dp)
        }
    }

    /**
     * The card: two album colours (a tinted rounded card, then a second tinted layer that fades
     * from the bottom-right corner) or the Ampli slate-to-rose gradient, with a soft highlight in
     * the top-left. Shapes rather than a bitmap, so the 24dp corners stay round at every size.
     */
    @Composable
    private fun Background(cover: Cover?) {
        val fill = GlanceModifier.fillMaxSize()
        if (cover != null) {
            Image(
                ImageProvider(R.drawable.widget_bg_base), null, fill,
                contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(ColorProvider(Color(cover.start))),
            )
            Image(
                ImageProvider(R.drawable.widget_bg_blend), null, fill,
                contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(ColorProvider(Color(cover.end))),
            )
        } else {
            Image(ImageProvider(R.drawable.widget_bg), null, fill, contentScale = ContentScale.FillBounds)
        }
        Image(ImageProvider(R.drawable.widget_bg_shine), null, fill, contentScale = ContentScale.FillBounds)
    }

    /**
     * A vinyl record with the album art as its round centre label. While playing (and not in Lite
     * mode) the record spins: a ProgressBar turns a rotate drawable of the disc. The art can't go
     * inside that drawable, so it sits on top and stays still; the disc's sheen shows the spin.
     */
    @Composable
    private fun Disc(look: Look, size: Dp) {
        val context = LocalContext.current
        Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
            if (look.animate) {
                AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_disc_spin), GlanceModifier.fillMaxSize())
            } else {
                Image(ImageProvider(R.drawable.widget_disc), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            look.cover?.let {
                Image(
                    ImageProvider(it.label), context.getString(R.string.widget_album_art),
                    GlanceModifier.size(size * LABEL_FRACTION), contentScale = ContentScale.Fit,
                )
            }
        }
    }

    /** Four little bars dancing in front of the title while playing. */
    @Composable
    private fun Bars() {
        val context = LocalContext.current
        AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_bars), GlanceModifier.size(BARS_W, BARS_H))
    }

    /**
     * The song title, led by the dancing bars while playing. When [centred], an empty space as wide
     * as the bars balances them on the right, so the title itself stays on the column's centre line;
     * the row only grows to the title's width, and a long title shrinks and ends in "…".
     */
    @Composable
    private fun TitleLine(look: Look, titleSp: Int, centred: Boolean) {
        val title = look.s.title.ifEmpty { "Ampli" }
        val style = TextStyle(
            color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = titleSp.sp,
            textAlign = if (centred) TextAlign.Center else TextAlign.Start,
        )
        if (!look.animate) {
            Text(title, maxLines = 1, modifier = GlanceModifier.fillMaxWidth(), style = style)
            return
        }
        Row(
            if (centred) GlanceModifier else GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bars()
            Spacer(GlanceModifier.width(BARS_GAP))
            Text(title, maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = style)
            if (centred) Spacer(GlanceModifier.width(BARS_W + BARS_GAP))
        }
    }

    @Composable
    private fun ArtistLine(s: WidgetState, artistSp: Int, centred: Boolean) {
        val context = LocalContext.current
        Text(
            s.artist.ifEmpty { context.getString(R.string.widget_tap_play) }, maxLines = 1, modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(
                color = ColorProvider(Color.White.copy(alpha = 0.85f)), fontSize = artistSp.sp,
                textAlign = if (centred) TextAlign.Center else TextAlign.Start,
            ),
        )
    }

    @Composable
    private fun UpNextLine(s: WidgetState, nextSp: Int) {
        val context = LocalContext.current
        val upNext = when {
            s.title.isEmpty() -> ""
            s.next.isNotEmpty() -> context.getString(R.string.widget_up_next, s.next)
            else -> context.getString(R.string.widget_queue_end)
        }
        if (upNext.isNotEmpty()) {
            Text(
                upNext, maxLines = 1, modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.7f)), fontSize = nextSp.sp, textAlign = TextAlign.Center),
            )
        }
    }

    /**
     * "01:24 / 04:28": a Chronometer that the launcher ticks by itself while playing, frozen at the
     * saved position while paused. The total uses the same formatter as the Chronometer.
     */
    @Composable
    private fun Elapsed(s: WidgetState, textSp: Float) {
        if (s.title.isEmpty()) return
        val context = LocalContext.current
        val now = SystemClock.elapsedRealtime()
        // capturedAt from before a reboot is meaningless; show the saved position, still.
        val running = s.playing && s.capturedAt in 1..now
        val base = if (running) s.capturedAt - s.positionMs else now - s.positionMs
        val views = RemoteViews(context.packageName, R.layout.widget_time).apply {
            setChronometer(R.id.widget_elapsed, base, null, running)
            setTextViewText(R.id.widget_total, if (s.durationMs > 0) " / " + DateUtils.formatElapsedTime(s.durationMs / 1000) else "")
            setTextViewTextSize(R.id.widget_elapsed, TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextViewTextSize(R.id.widget_total, TypedValue.COMPLEX_UNIT_SP, textSp)
        }
        AndroidRemoteViews(views)
    }

    /** Previous / play-pause / next, centred, with the same gap on both sides of play. */
    @Composable
    private fun Controls(look: Look, side: Dp, sidePad: Dp, play: Dp, playPad: Dp, gap: Dp) {
        Row(
            GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideButton(R.drawable.ic_widget_prev, R.string.common_previous, CMD_PREV, side, sidePad)
            Spacer(GlanceModifier.width(gap))
            GlowPlay(look, play, playPad)
            Spacer(GlanceModifier.width(gap))
            SideButton(R.drawable.ic_widget_next, R.string.common_next, CMD_NEXT, side, sidePad)
        }
    }

    @Composable
    private fun SideButton(icon: Int, label: Int, cmd: String, size: Dp, pad: Dp) {
        val context = LocalContext.current
        Image(ImageProvider(icon), context.getString(label), GlanceModifier.size(size).padding(pad).clickable(command(cmd)))
    }

    /**
     * Play/pause centred in a slightly larger square, where a soft ring grows and fades while
     * playing. The square is there when paused too, so nothing moves when the ring comes and goes.
     */
    @Composable
    private fun GlowPlay(look: Look, size: Dp, pad: Dp) {
        val context = LocalContext.current
        Box(GlanceModifier.size(size * GLOW_SCALE), contentAlignment = Alignment.Center) {
            if (look.animate) {
                AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_glow), GlanceModifier.fillMaxSize())
            }
            PlayButton(look.s, size, pad)
        }
    }

    /** Play/pause on a soft round chip. Nothing played yet (fresh install): opens the app to pick music. */
    @Composable
    private fun PlayButton(s: WidgetState, size: Dp, pad: Dp) {
        val context = LocalContext.current
        Image(
            ImageProvider(if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
            context.getString(if (s.playing) R.string.common_pause else R.string.common_play),
            GlanceModifier.size(size).background(ImageProvider(R.drawable.widget_play_bg)).padding(pad)
                .clickable(if (s.title.isEmpty()) actionStartActivity<MainActivity>() else command(CMD_TOGGLE)),
        )
    }

    private fun command(name: String): Action = actionRunCallback<PlayerAction>(actionParametersOf(CommandKey to name))

    /** The cover for [uri], reused while the song stays the same (play/pause, seeks). */
    private suspend fun cover(context: Context, uri: String): Cover? {
        if (uri.isEmpty()) return null
        lastCover?.let { (key, cached) -> if (key == uri) return cached }
        return loadCover(context, uri)?.also { lastCover = uri to it }
    }

    /** Loads the album art through the app's Coil loader; null when there is none. */
    private suspend fun loadCover(context: Context, uri: String): Cover? = try {
        withTimeoutOrNull(4_000) {
            val request = ImageRequest.Builder(context).data(uri).size(ART_PX).allowHardware(false).build()
            val result = SingletonImageLoader.get(context).execute(request)
            (result as? SuccessResult)?.image?.toBitmap()?.let { bitmap ->
                withContext(Dispatchers.Default) { makeCover(bitmap) }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun makeCover(src: Bitmap): Cover {
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, ART_PX, ART_PX, true)

        // Round label (works on every Android version, unlike clipping in RemoteViews).
        val label = Bitmap.createBitmap(ART_PX, ART_PX, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val r = ART_PX / 2f
        Canvas(label).drawCircle(r, r, r, paint)

        // Two dark colours from the art, dark enough for white text on top.
        val palette = Palette.from(scaled).maximumColorCount(16).generate()
        val dominant = palette.getDominantColor(FALLBACK_START)
        val start = deepen(palette.getDarkVibrantColor(palette.getVibrantColor(dominant)), 0.12)
        var end = deepen(palette.getDarkMutedColor(palette.getMutedColor(dominant)), 0.08)
        if (distance(start, end) < 48) end = ColorUtils.blendARGB(start, android.graphics.Color.BLACK, 0.45f)
        return Cover(label, start, end)
    }

    /** Darkens [color] step by step until its luminance is at most [maxLuminance]. */
    private fun deepen(color: Int, maxLuminance: Double): Int {
        var c = color or 0xFF000000.toInt()
        var steps = 0
        while (ColorUtils.calculateLuminance(c) > maxLuminance && steps < 16) {
            c = ColorUtils.blendARGB(c, android.graphics.Color.BLACK, 0.15f)
            steps++
        }
        return c
    }

    private fun distance(a: Int, b: Int): Int =
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
            abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
            abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))

    companion object {
        const val CMD_PREV = "prev"
        const val CMD_TOGGLE = "toggle"
        const val CMD_NEXT = "next"
        val CommandKey = ActionParameters.Key<String>("command")

        private const val ART_PX = 240
        /** The album art covers this share of the disc's diameter. */
        private const val LABEL_FRACTION = 0.58f
        /** The glow ring's square around play/pause; its drawable starts the ring at the chip's edge. */
        private const val GLOW_SCALE = 4f / 3f
        private val BARS_W = 14.dp
        private val BARS_H = 12.dp
        private val BARS_GAP = 6.dp
        private const val FALLBACK_START = 0xFF334155.toInt()

        private val loadLock = Mutex()
        private var lastLook: Look? = null
        private var lastCover: Pair<String, Cover>? = null

        private val SMALL = DpSize(100.dp, 40.dp)
        private val COMPACT = DpSize(170.dp, 40.dp)
        private val WIDE = DpSize(250.dp, 40.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)
        private val TALL = DpSize(250.dp, 180.dp)
    }
}

class AmpliWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AmpliWidget()
}

/** Sends a widget button press to the player. */
class PlayerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[AmpliWidget.CommandKey] ?: return
        // Glance runs this off the main thread, but a MediaController built there uses the main
        // looper and refuses calls from any other thread, so every button press was dropped.
        withContext(Dispatchers.Main) { send(context, command) }
    }

    private suspend fun send(context: Context, command: String) {
        val controller = connect(context) ?: return
        try {
            // If the app was closed, the button woke a fresh service with an empty player, so
            // every press did nothing. Load the queue the app saved, like the app does on launch.
            val restored = controller.mediaItemCount == 0 && loadSavedQueue(context, controller)
            if (controller.mediaItemCount == 0) return
            when (command) {
                AmpliWidget.CMD_PREV -> controller.seekToPrevious()
                AmpliWidget.CMD_NEXT -> controller.seekToNext()
                AmpliWidget.CMD_TOGGLE -> if (controller.isPlaying && !restored) controller.pause() else {
                    if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                    controller.play()
                }
            }
            if (restored || command == AmpliWidget.CMD_TOGGLE) {
                // Stay connected until the song is really playing (or paused): releasing earlier
                // unbinds a service that is not yet in the foreground, and Android stops it.
                withTimeoutOrNull(4_000) {
                    while (controller.playWhenReady != controller.isPlaying) delay(100)
                }
            }
            delay(300) // let the command reach the service before disconnecting
        } finally {
            controller.release()
        }
    }

    private suspend fun connect(context: Context): MediaController? = suspendCancellableCoroutine { cont ->
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { cont.resume(runCatching { future.get() }.getOrNull()) },
            ContextCompat.getMainExecutor(context),
        )
        cont.invokeOnCancellation { future.cancel(true) }
    }
}

/**
 * What the widget shows, written by the playback service. [next] is the next song's title
 * ("" when the queue ends here); [art] is the current song's artwork URI ("" when it has none).
 * [positionMs] was the playback position at [capturedAt] ([SystemClock.elapsedRealtime]), so the
 * widget's clock can carry on from there by itself; [durationMs] is 0 while unknown.
 */
data class WidgetState(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val next: String = "",
    val art: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val capturedAt: Long = 0L,
) {
    /** Same song and state, and the clock would show the same time (within half a second). */
    private fun sameAs(other: WidgetState): Boolean {
        if (copy(positionMs = 0L, capturedAt = 0L) != other.copy(positionMs = 0L, capturedAt = 0L)) return false
        val drift = if (playing) (capturedAt - positionMs) - (other.capturedAt - other.positionMs) else positionMs - other.positionMs
        return abs(drift) < 500
    }

    companion object {
        private const val PREFS = "widget"

        private val _changes = MutableStateFlow(0)

        /** Goes up every time the saved state changes; a running widget session redraws on it. */
        val changes: StateFlow<Int> = _changes.asStateFlow()

        fun read(context: Context): WidgetState {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WidgetState(
                p.getString("title", "").orEmpty(), p.getString("artist", "").orEmpty(), p.getBoolean("playing", false),
                p.getString("next", "").orEmpty(), p.getString("art", "").orEmpty(),
                p.getLong("position", 0L), p.getLong("duration", 0L), p.getLong("captured", 0L),
            )
        }

        /** A snapshot of [player] for the widget. Call on the player's thread. */
        fun from(player: Player, playing: Boolean = player.isPlaying): WidgetState {
            val meta = player.currentMediaItem?.mediaMetadata
            val next = runCatching {
                val index = player.nextMediaItemIndex
                if (index == C.INDEX_UNSET || index >= player.mediaItemCount) ""
                else player.getMediaItemAt(index).mediaMetadata.title?.toString().orEmpty()
            }.getOrDefault("")
            val duration = player.duration
            return WidgetState(
                title = meta?.title?.toString().orEmpty(),
                artist = meta?.artist?.toString().orEmpty(),
                playing = playing,
                next = next,
                art = meta?.artworkUri?.toString().orEmpty(),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = if (duration == C.TIME_UNSET || duration < 0L) 0L else duration,
                capturedAt = SystemClock.elapsedRealtime(),
            )
        }

        /** Saves the state and redraws every Ampli widget, only when something visible changed. */
        suspend fun update(context: Context, state: WidgetState) {
            if (read(context).sameAs(state)) return
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", state.title).putString("artist", state.artist).putBoolean("playing", state.playing)
                .putString("next", state.next).putString("art", state.art)
                .putLong("position", state.positionMs).putLong("duration", state.durationMs).putLong("captured", state.capturedAt)
                .apply()
            redraw(context)
        }

        /** Redraws every Ampli widget from the saved state (also used when Lite mode is switched). */
        suspend fun redraw(context: Context) {
            _changes.update { it + 1 }
            runCatching { AmpliWidget().updateAll(context) }
        }
    }
}
