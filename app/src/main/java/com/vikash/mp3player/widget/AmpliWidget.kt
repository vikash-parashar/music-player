package com.vikash.mp3player.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vikash.mp3player.MainActivity
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Home-screen widget: the current song with previous, play/pause and next.
 * [PlaybackService] writes what's playing with [WidgetState.update]; the buttons talk to the
 * service through a short-lived MediaController.
 */
class AmpliWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetState.read(context)
        provideContent { Content(s) }
    }

    @Composable
    private fun Content(s: WidgetState) {
        val white = ColorProvider(Color.White)
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(R.drawable.ic_launcher_foreground), "Open Ampli",
                GlanceModifier.size(40.dp).clickable(actionStartActivity<MainActivity>()),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                Text(
                    s.title.ifEmpty { "Ampli" }, maxLines = 1,
                    style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                )
                Text(
                    s.artist.ifEmpty { "Tap play to resume your music" }, maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.85f)), fontSize = 12.sp),
                )
            }
            Button(R.drawable.ic_widget_prev, "Previous", command(CMD_PREV))
            // Nothing played yet (fresh install): play opens the app to pick music.
            Button(
                if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                if (s.playing) "Pause" else "Play",
                if (s.title.isEmpty()) actionStartActivity<MainActivity>() else command(CMD_TOGGLE),
            )
            Button(R.drawable.ic_widget_next, "Next", command(CMD_NEXT))
        }
    }

    private fun command(name: String): Action = actionRunCallback<PlayerAction>(actionParametersOf(CommandKey to name))

    @Composable
    private fun Button(icon: Int, label: String, onClick: Action) {
        Image(ImageProvider(icon), label, GlanceModifier.size(40.dp).padding(8.dp).clickable(onClick))
    }

    companion object {
        const val CMD_PREV = "prev"
        const val CMD_TOGGLE = "toggle"
        const val CMD_NEXT = "next"
        val CommandKey = ActionParameters.Key<String>("command")
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

/** What the widget shows, written by the playback service. */
data class WidgetState(val title: String, val artist: String, val playing: Boolean) {
    companion object {
        private const val PREFS = "widget"

        fun read(context: Context): WidgetState {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WidgetState(p.getString("title", "").orEmpty(), p.getString("artist", "").orEmpty(), p.getBoolean("playing", false))
        }

        /** Saves the state and redraws every Ampli widget, only when something changed. */
        suspend fun update(context: Context, title: String, artist: String, playing: Boolean) {
            if (read(context) == WidgetState(title, artist, playing)) return
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("artist", artist).putBoolean("playing", playing).apply()
            runCatching { AmpliWidget().updateAll(context) }
        }
    }
}
