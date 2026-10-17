package com.vikash.mp3player.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.vikash.mp3player.MainActivity
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.PlaybackService
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Home-screen widget: the current song with previous, play/pause and next.
 * [PlaybackService] writes what's playing with [WidgetState.update]; the buttons talk to the
 * service through a short-lived MediaController.
 *
 * Four layouts, picked by the widget's size ([SizeMode.Responsive]):
 * - SMALL (2x1): album art + play/pause; tapping anywhere else opens the app.
 * - WIDE (4x1): app icon, title and artist, previous / play-pause / next.
 * - MEDIUM (4x2): album art on the left; title, artist, up next and the buttons centred beside it.
 * - TALL (4x3): bigger album art with the song details beside it, bigger buttons centred below.
 */
class AmpliWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, MEDIUM, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetState.read(context)
        val art = loadArt(context, s.art)
        provideContent { Content(s, art) }
    }

    @Composable
    private fun Content(s: WidgetState, art: Bitmap?) {
        val size = LocalSize.current
        when {
            size.width >= TALL.width && size.height >= TALL.height -> Tall(s, art)
            size.width >= MEDIUM.width && size.height >= MEDIUM.height -> Medium(s, art)
            size.width >= WIDE.width -> Wide(s)
            else -> Small(s, art)
        }
    }

    /** 2x1: art and play/pause, centred; the rest of the widget opens the app. */
    @Composable
    private fun Small(s: WidgetState, art: Bitmap?) {
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg))
                .clickable(actionStartActivity<MainActivity>()).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(art, 40.dp)
            Spacer(GlanceModifier.width(12.dp))
            PlayButton(s, 40.dp, 9.dp)
        }
    }

    /** 4x1: the original widget, unchanged. */
    @Composable
    private fun Wide(s: WidgetState) {
        val white = ColorProvider(Color.White)
        val context = LocalContext.current
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(R.drawable.ic_launcher_foreground), context.getString(R.string.widget_open_app),
                GlanceModifier.size(40.dp).clickable(actionStartActivity<MainActivity>()),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                Text(
                    s.title.ifEmpty { "Ampli" }, maxLines = 1,
                    style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                )
                Text(
                    s.artist.ifEmpty { context.getString(R.string.widget_tap_play) }, maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.85f)), fontSize = 12.sp),
                )
            }
            Button(R.drawable.ic_widget_prev, context.getString(R.string.common_previous), command(CMD_PREV))
            // Nothing played yet (fresh install): play opens the app to pick music.
            Button(
                if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                context.getString(if (s.playing) R.string.common_pause else R.string.common_play),
                if (s.title.isEmpty()) actionStartActivity<MainActivity>() else command(CMD_TOGGLE),
            )
            Button(R.drawable.ic_widget_next, context.getString(R.string.common_next), command(CMD_NEXT))
        }
    }

    /** 4x2: art on the left; details and buttons centred on one axis to its right. */
    @Composable
    private fun Medium(s: WidgetState, art: Bitmap?) {
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg))
                .clickable(actionStartActivity<MainActivity>()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(art, 84.dp)
            Spacer(GlanceModifier.width(12.dp))
            Column(
                GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Details(s, titleSp = 14, artistSp = 12, nextSp = 11)
                Spacer(GlanceModifier.height(4.dp))
                Controls(s, side = 34.dp, sidePad = 7.dp, play = 38.dp, playPad = 8.dp, gap = 8.dp)
            }
        }
    }

    /** 4x3: big art with the details beside it, bigger buttons centred across the full width below. */
    @Composable
    private fun Tall(s: WidgetState, art: Bitmap?) {
        Column(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg))
                .clickable(actionStartActivity<MainActivity>()).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Art(art, 88.dp)
                Spacer(GlanceModifier.width(14.dp))
                Column(
                    GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Details(s, titleSp = 16, artistSp = 13, nextSp = 12)
                }
            }
            Spacer(GlanceModifier.height(12.dp))
            Controls(s, side = 44.dp, sidePad = 10.dp, play = 52.dp, playPad = 13.dp, gap = 20.dp)
        }
    }

    /** Title, artist and the "Up next" line, each centred on its own line. */
    @Composable
    private fun Details(s: WidgetState, titleSp: Int, artistSp: Int, nextSp: Int) {
        val context = LocalContext.current
        Text(
            s.title.ifEmpty { "Ampli" }, maxLines = 1, modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(
                color = ColorProvider(Color.White), fontWeight = FontWeight.Bold,
                fontSize = titleSp.sp, textAlign = TextAlign.Center,
            ),
        )
        Text(
            s.artist.ifEmpty { context.getString(R.string.widget_tap_play) }, maxLines = 1, modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.85f)), fontSize = artistSp.sp, textAlign = TextAlign.Center),
        )
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

    /** Previous / play-pause / next, centred, with the same gap on both sides of play. */
    @Composable
    private fun Controls(s: WidgetState, side: Dp, sidePad: Dp, play: Dp, playPad: Dp, gap: Dp) {
        val context = LocalContext.current
        Row(
            GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(R.drawable.ic_widget_prev), context.getString(R.string.common_previous),
                GlanceModifier.size(side).padding(sidePad).clickable(command(CMD_PREV)),
            )
            Spacer(GlanceModifier.width(gap))
            PlayButton(s, play, playPad)
            Spacer(GlanceModifier.width(gap))
            Image(
                ImageProvider(R.drawable.ic_widget_next), context.getString(R.string.common_next),
                GlanceModifier.size(side).padding(sidePad).clickable(command(CMD_NEXT)),
            )
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

    /** The song's album art (already cropped square with rounded corners), or the app icon on a tile. */
    @Composable
    private fun Art(art: Bitmap?, size: Dp) {
        val context = LocalContext.current
        if (art != null) {
            Image(
                ImageProvider(art), context.getString(R.string.widget_album_art),
                GlanceModifier.size(size), contentScale = ContentScale.Crop,
            )
        } else {
            Box(GlanceModifier.size(size).background(ImageProvider(R.drawable.widget_art_bg)), contentAlignment = Alignment.Center) {
                Image(
                    ImageProvider(R.drawable.ic_launcher_foreground), context.getString(R.string.widget_open_app),
                    GlanceModifier.fillMaxSize(),
                )
            }
        }
    }

    private fun command(name: String): Action = actionRunCallback<PlayerAction>(actionParametersOf(CommandKey to name))

    @Composable
    private fun Button(icon: Int, label: String, onClick: Action) {
        Image(ImageProvider(icon), label, GlanceModifier.size(40.dp).padding(8.dp).clickable(onClick))
    }

    /** Loads the album art through the app's Coil loader, as a small rounded square; null when there is none. */
    private suspend fun loadArt(context: Context, uri: String): Bitmap? {
        if (uri.isEmpty()) return null
        return try {
            withTimeoutOrNull(4_000) {
                val request = ImageRequest.Builder(context).data(uri).size(ART_PX).allowHardware(false).build()
                val result = SingletonImageLoader.get(context).execute(request)
                (result as? SuccessResult)?.image?.toBitmap()?.let(::roundedSquare)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Centre-crops to a square of ART_PX and rounds the corners (works on every Android version). */
    private fun roundedSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, ART_PX, ART_PX, true)
        val out = Bitmap.createBitmap(ART_PX, ART_PX, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val radius = ART_PX * 0.14f
        Canvas(out).drawRoundRect(RectF(0f, 0f, ART_PX.toFloat(), ART_PX.toFloat()), radius, radius, paint)
        return out
    }

    companion object {
        const val CMD_PREV = "prev"
        const val CMD_TOGGLE = "toggle"
        const val CMD_NEXT = "next"
        val CommandKey = ActionParameters.Key<String>("command")

        private const val ART_PX = 288
        private val SMALL = DpSize(100.dp, 40.dp)
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
        val controller = connect(context) ?: return
        try {
            when (command) {
                AmpliWidget.CMD_PREV -> controller.seekToPrevious()
                AmpliWidget.CMD_NEXT -> controller.seekToNext()
                AmpliWidget.CMD_TOGGLE -> if (controller.isPlaying) controller.pause() else if (controller.mediaItemCount > 0) {
                    if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                    controller.play()
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
 */
data class WidgetState(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val next: String = "",
    val art: String = "",
) {
    companion object {
        private const val PREFS = "widget"

        fun read(context: Context): WidgetState {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WidgetState(
                p.getString("title", "").orEmpty(), p.getString("artist", "").orEmpty(), p.getBoolean("playing", false),
                p.getString("next", "").orEmpty(), p.getString("art", "").orEmpty(),
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
            return WidgetState(
                title = meta?.title?.toString().orEmpty(),
                artist = meta?.artist?.toString().orEmpty(),
                playing = playing,
                next = next,
                art = meta?.artworkUri?.toString().orEmpty(),
            )
        }

        /** Saves the state and redraws every Ampli widget, only when something changed. */
        suspend fun update(context: Context, state: WidgetState) {
            if (read(context) == state) return
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", state.title).putString("artist", state.artist).putBoolean("playing", state.playing)
                .putString("next", state.next).putString("art", state.art)
                .apply()
            runCatching { AmpliWidget().updateAll(context) }
        }
    }
}
