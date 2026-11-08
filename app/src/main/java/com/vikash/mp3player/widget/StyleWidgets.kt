package com.vikash.mp3player.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as startIntent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vikash.mp3player.MainActivity
import com.vikash.mp3player.MusicApp
import com.vikash.mp3player.R
import com.vikash.mp3player.playback.SleepTimer
import com.vikash.mp3player.ui.CarLaunch
import java.text.DateFormat
import java.util.Date

/**
 * The style widgets added on 8 Nov 2026: Vinyl Deck, Clock + Music, Quick Mixes, Retro Cassette and
 * Slim Pill. They read the same [WidgetState] as [AmpliWidget] and send the same commands through
 * [PlayerAction]. All use [SizeMode.Exact], so layouts get the real widget size (Responsive only
 * reports the matched bucket, which left the right side of wide widgets empty).
 *
 * Vinyl Deck, Quick Mixes and Retro Cassette are Pro; without Pro they show a "Pro widget" card
 * that opens the app. Clock + Music and Slim Pill are free.
 */
abstract class StyleWidget(private val pro: Boolean) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val change by WidgetState.changes.collectAsState()
            val s = remember(change) { WidgetState.read(context) }
            val app = context.applicationContext as? MusicApp
            val lite = runCatching { app?.prefs?.liteMode?.value }.getOrNull() ?: false
            val unlocked = !pro || (runCatching { app?.proUnlocked?.value }.getOrNull() ?: false)
            val favourite = s.id.isNotEmpty() && (runCatching { app?.prefs?.isFavorite(s.id) }.getOrNull() ?: false)
            if (unlocked) Body(s, StyleExtras(animate = s.playing && !lite, favourite = favourite))
            else Locked()
        }
    }

    @Composable
    protected abstract fun Body(s: WidgetState, x: StyleExtras)

    @Composable
    private fun Locked() {
        val context = LocalContext.current
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Color(0xFF1B1428)).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PRO", style = TextStyle(color = ColorProvider(Color(0xFFF59E0B)), fontWeight = FontWeight.Bold, fontSize = 12.sp))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                context.getString(R.string.widget_pro_locked), maxLines = 2,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, textAlign = androidx.glance.text.TextAlign.Center),
            )
        }
    }

    // ---- shared pieces ----

    protected fun command(name: String): Action =
        actionRunCallback<PlayerAction>(actionParametersOf(AmpliWidget.CommandKey to name))

    protected fun openApp(): Action = actionStartActivity<MainActivity>()

    @Composable
    protected fun Icon(icon: Int, label: Int, action: Action, size: Dp, pad: Dp, bg: Int?, tint: Color) {
        val context = LocalContext.current
        var m = GlanceModifier.size(size)
        if (bg != null) m = m.background(ImageProvider(bg))
        Image(
            ImageProvider(icon), context.getString(label), m.padding(pad).clickable(action),
            colorFilter = ColorFilter.tint(ColorProvider(tint)),
        )
    }

    @Composable
    protected fun PlayPause(s: WidgetState, size: Dp, bg: Int, tint: Color) {
        Icon(
            if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            if (s.playing) R.string.common_pause else R.string.common_play,
            if (s.title.isEmpty()) openApp() else command(AmpliWidget.CMD_TOGGLE),
            size, size * 0.27f, bg, tint,
        )
    }

    @Composable
    protected fun Title(s: WidgetState, sp: Int, color: Color, serif: Boolean = false, mono: Boolean = false) {
        Text(
            s.title.ifEmpty { "Ampli" }, maxLines = 1,
            style = TextStyle(
                color = ColorProvider(color), fontWeight = FontWeight.Bold, fontSize = sp.sp,
                fontFamily = when {
                    serif -> FontFamily.Serif
                    mono -> FontFamily.Monospace
                    else -> null
                },
            ),
        )
    }

    @Composable
    protected fun Artist(s: WidgetState, sp: Int, color: Color) {
        val context = LocalContext.current
        Text(
            s.artist.ifEmpty { context.getString(R.string.widget_tap_play) }, maxLines = 1,
            style = TextStyle(color = ColorProvider(color), fontSize = sp.sp),
        )
    }

    /** A thin progress line: where the song was when the widget was last drawn. */
    @Composable
    protected fun Progress(s: WidgetState, width: Dp, color: Color, track: Color) {
        val now = SystemClock.elapsedRealtime()
        val pos = if (s.playing && s.capturedAt in 1..now) s.positionMs + (now - s.capturedAt) else s.positionMs
        val f = if (s.durationMs > 0) (pos.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
        Row(GlanceModifier.width(width).height(4.dp).cornerRadius(2.dp).background(track)) {
            if (f > 0f) Box(GlanceModifier.width(width * f).height(4.dp).cornerRadius(2.dp).background(color)) {}
        }
    }

    /** "01:24 / 04:28", ticked by the launcher's Chronometer (the app is not woken). */
    @Composable
    protected fun Time(s: WidgetState, sp: Float) {
        if (s.title.isEmpty()) return
        val context = LocalContext.current
        val now = SystemClock.elapsedRealtime()
        val running = s.playing && s.capturedAt in 1..now
        val base = if (running) s.capturedAt - s.positionMs else now - s.positionMs
        val views = RemoteViews(context.packageName, R.layout.widget_time).apply {
            setChronometer(R.id.widget_elapsed, base, null, running)
            setTextViewText(R.id.widget_total, if (s.durationMs > 0) " / " + DateUtils.formatElapsedTime(s.durationMs / 1000) else "")
            setTextViewTextSize(R.id.widget_elapsed, TypedValue.COMPLEX_UNIT_SP, sp)
            setTextViewTextSize(R.id.widget_total, TypedValue.COMPLEX_UNIT_SP, sp)
        }
        AndroidRemoteViews(views)
    }

    /** A spinning drawable while playing (framework animation), a still image otherwise. */
    @Composable
    protected fun Spinner(animate: Boolean, spinLayout: Int, still: Int, size: Dp) {
        val context = LocalContext.current
        Box(GlanceModifier.size(size)) {
            if (animate) AndroidRemoteViews(RemoteViews(context.packageName, spinLayout), GlanceModifier.fillMaxSize())
            else Image(ImageProvider(still), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }

    companion object {
        suspend fun redrawAll(context: Context) {
            runCatching { VinylDeckWidget().updateAll(context) }
            runCatching { ClockMusicWidget().updateAll(context) }
            runCatching { QuickMixesWidget().updateAll(context) }
            runCatching { CassetteWidget().updateAll(context) }
            runCatching { SlimPillWidget().updateAll(context) }
        }
    }
}

/** What a style widget needs besides [WidgetState]. */
class StyleExtras(val animate: Boolean, val favourite: Boolean)

/** 1. Vinyl Deck (4x2, Pro): turntable with a tonearm, song, time, progress, shuffle, heart. */
class VinylDeckWidget : StyleWidget(pro = true) {
    @Composable
    override fun Body(s: WidgetState, x: StyleExtras) {
        val size = LocalSize.current
        val plate = minOf(size.height - 24.dp, size.width * 0.42f)
        val ink = Color(0xFFF3E2CC)
        Row(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ImageProvider(R.drawable.widget_vinyl_bg))
                .padding(12.dp).clickable(openApp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(plate), contentAlignment = Alignment.TopEnd) {
                Box(GlanceModifier.fillMaxSize().padding(5.dp)) {
                    Spinner(x.animate, R.layout.widget_disc_spin, R.drawable.widget_disc, plate - 10.dp)
                }
                Image(ImageProvider(R.drawable.widget_tonearm), null, GlanceModifier.width(plate * 0.3f).height(plate * 0.72f))
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Title(s, 17, Color.White, serif = true)
                Artist(s, 12, Color(0xFFE6CFB5))
                Spacer(GlanceModifier.height(6.dp))
                Progress(s, size.width - plate - 48.dp, Color(0xFFF59E0B), Color(0x33FFFFFF))
                Spacer(GlanceModifier.height(3.dp))
                Time(s, 10.5f)
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ring = R.drawable.widget_drive_ring
                    Icon(R.drawable.ic_widget_shuffle, R.string.widget_shuffle, command(AmpliWidget.CMD_SHUFFLE), 28.dp, 7.dp, ring,
                        if (s.shuffle) Color(0xFFF59E0B) else ink)
                    Spacer(GlanceModifier.width(5.dp))
                    Icon(R.drawable.ic_widget_prev, R.string.common_previous, command(AmpliWidget.CMD_PREV), 28.dp, 8.dp, ring, ink)
                    Spacer(GlanceModifier.width(5.dp))
                    PlayPause(s, 38.dp, R.drawable.widget_vinyl_play, Color(0xFF1A1210))
                    Spacer(GlanceModifier.width(5.dp))
                    Icon(R.drawable.ic_widget_next, R.string.common_next, command(AmpliWidget.CMD_NEXT), 28.dp, 8.dp, ring, ink)
                    Spacer(GlanceModifier.width(5.dp))
                    Icon(
                        if (x.favourite) R.drawable.ic_widget_heart else R.drawable.ic_widget_heart_outline,
                        R.string.widget_favourite, command(AmpliWidget.CMD_FAVOURITE), 28.dp, 7.dp, ring, Color(0xFFF43F5E),
                    )
                }
            }
        }
    }
}

/** 3. Clock + Music (4x2, free): big clock and date, sleep timer, mini player. */
class ClockMusicWidget : StyleWidget(pro = false) {
    @Composable
    override fun Body(s: WidgetState, x: StyleExtras) {
        val context = LocalContext.current
        val size = LocalSize.current
        val teal = Color(0xFF5EEAD4)
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ImageProvider(R.drawable.widget_clock_bg))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth()) {
                AndroidRemoteViews(
                    RemoteViews(context.packageName, R.layout.widget_clock).apply {
                        setTextViewTextSize(R.id.widget_clock_time, TypedValue.COMPLEX_UNIT_SP, if (size.height >= 150.dp) 52f else 40f)
                    },
                    GlanceModifier.defaultWeight(),
                )
                SleepBadge(teal)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(
                GlanceModifier.fillMaxWidth().cornerRadius(24.dp).background(Color(0x1AFFFFFF)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spinner(x.animate, R.layout.widget_disc_spin, R.drawable.widget_disc, 30.dp)
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight().clickable(openApp())) {
                    Title(s, 13, Color.White)
                    Artist(s, 10, Color(0xFF9FE7DC))
                }
                Icon(R.drawable.ic_widget_next, R.string.common_next, command(AmpliWidget.CMD_NEXT), 30.dp, 8.dp, R.drawable.widget_clock_chip, Color(0xFF071A1A))
                Spacer(GlanceModifier.width(6.dp))
                PlayPause(s, 30.dp, R.drawable.widget_clock_chip, Color(0xFF071A1A))
            }
        }
    }

    /** Tap to start a 30-minute sleep timer; while one runs, it shows when the music stops (tap to cancel). */
    @Composable
    private fun SleepBadge(color: Color) {
        val context = LocalContext.current
        val endsAt = SleepTimer.endsAtMillis.value
        val text = if (endsAt != null && endsAt > System.currentTimeMillis()) {
            context.getString(R.string.widget_sleep_until, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(endsAt)))
        } else context.getString(R.string.widget_sleep_30)
        Text(
            text, maxLines = 2,
            modifier = GlanceModifier.padding(start = 6.dp).clickable(command(AmpliWidget.CMD_SLEEP)),
            style = TextStyle(color = ColorProvider(color), fontSize = 11.sp),
        )
    }
}

/** 5. Quick Mixes (4x2, Pro): Favourites, Recently added, Most played, Shuffle all + shortcuts. */
class QuickMixesWidget : StyleWidget(pro = true) {
    @Composable
    override fun Body(s: WidgetState, x: StyleExtras) {
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Color(0xFF14111C)).padding(10.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Tile(R.drawable.widget_tile_rose, R.drawable.ic_widget_heart, R.string.widget_mix_favourites, WidgetLaunch.MIX_FAVOURITES)
                Spacer(GlanceModifier.width(8.dp))
                Tile(R.drawable.widget_tile_sky, R.drawable.ic_widget_new, R.string.widget_mix_recent, WidgetLaunch.MIX_RECENT)
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Tile(R.drawable.widget_tile_amber, R.drawable.ic_widget_top, R.string.widget_mix_most, WidgetLaunch.MIX_MOST_PLAYED)
                Spacer(GlanceModifier.width(8.dp))
                Tile(R.drawable.widget_tile_green, R.drawable.ic_widget_shuffle, R.string.widget_mix_shuffle, WidgetLaunch.MIX_SHUFFLE)
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Chip(R.string.widget_chip_sleep, command(AmpliWidget.CMD_SLEEP))
                Spacer(GlanceModifier.width(6.dp))
                Chip(R.string.widget_chip_player, openApp())
                Spacer(GlanceModifier.width(6.dp))
                val context = LocalContext.current
                Chip(
                    R.string.widget_chip_car,
                    startIntent(
                        Intent(context, MainActivity::class.java).setAction(CarLaunch.ACTION_CAR_MODE)
                            .putExtra(CarLaunch.EXTRA_AUTOPLAY, false)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    ),
                )
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.Tile(bg: Int, icon: Int, label: Int, mix: String) {
        val context = LocalContext.current
        Column(
            GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(16.dp).background(ImageProvider(bg))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clickable(startIntent(WidgetLaunch.intent(context, mix))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(icon), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(ColorProvider(Color.White)))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                context.getString(label), maxLines = 1,
                style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 13.sp),
            )
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.Chip(label: Int, action: Action) {
        val context = LocalContext.current
        Box(
            GlanceModifier.defaultWeight().cornerRadius(14.dp).background(Color(0x1FFFFFFF)).padding(vertical = 6.dp).clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Text(context.getString(label), maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xFFDDDDDD)), fontSize = 11.sp))
        }
    }
}

/** 6. Retro Cassette (4x2, Pro): tape with turning reels, song on the label, walkman keys. */
class CassetteWidget : StyleWidget(pro = true) {
    @Composable
    override fun Body(s: WidgetState, x: StyleExtras) {
        val size = LocalSize.current
        val ink = Color(0xFF2A2420)
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Color(0xFFE9E2D0)).padding(12.dp),
        ) {
            Column(
                GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(12.dp).background(ink).padding(8.dp).clickable(openApp()),
            ) {
                Row(
                    GlanceModifier.fillMaxWidth().cornerRadius(6.dp).background(Color(0xFFF6C75B)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(GlanceModifier.defaultWeight()) { Title(s, 12, ink, mono = true) }
                    Text("C-60", style = TextStyle(color = ColorProvider(ink), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.height(6.dp))
                val reel = minOf(size.height * 0.24f, 44.dp)
                Row(
                    GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(8.dp).background(Color(0xFF3B332D)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spinner(x.animate, R.layout.widget_reel_spin, R.drawable.widget_reel, reel)
                    Spacer(GlanceModifier.width(size.width * 0.22f))
                    Spinner(x.animate, R.layout.widget_reel_spin, R.drawable.widget_reel, reel)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Key(R.drawable.ic_widget_prev, R.string.common_previous, command(AmpliWidget.CMD_PREV), false)
                Spacer(GlanceModifier.width(6.dp))
                Key(
                    if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    if (s.playing) R.string.common_pause else R.string.common_play,
                    if (s.title.isEmpty()) openApp() else command(AmpliWidget.CMD_TOGGLE), s.playing,
                )
                Spacer(GlanceModifier.width(6.dp))
                Key(R.drawable.ic_widget_next, R.string.common_next, command(AmpliWidget.CMD_NEXT), false)
                Spacer(GlanceModifier.width(6.dp))
                Key(R.drawable.ic_widget_shuffle, R.string.widget_shuffle, command(AmpliWidget.CMD_SHUFFLE), s.shuffle)
                Spacer(GlanceModifier.width(6.dp))
                Key(
                    if (x.favourite) R.drawable.ic_widget_heart else R.drawable.ic_widget_heart_outline,
                    R.string.widget_favourite, command(AmpliWidget.CMD_FAVOURITE), x.favourite,
                )
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.Key(icon: Int, label: Int, action: Action, on: Boolean) {
        val context = LocalContext.current
        Box(
            GlanceModifier.defaultWeight().height(28.dp)
                .background(ImageProvider(if (on) R.drawable.widget_key_on else R.drawable.widget_key)).clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(icon), context.getString(label), GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(ColorProvider(if (on) Color.White else Color(0xFF2A2420))),
            )
        }
    }
}

/** 8. Slim Pill (4x1, free): spinning disc, song, progress line, previous / play / next. */
class SlimPillWidget : StyleWidget(pro = false) {
    @Composable
    override fun Body(s: WidgetState, x: StyleExtras) {
        val size = LocalSize.current
        val disc = minOf(size.height - 12.dp, 58.dp)
        val ink = Color(0xFFE9E3FF)
        Row(
            GlanceModifier.fillMaxSize().cornerRadius(40.dp).background(ImageProvider(R.drawable.widget_pill_bg)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spinner(x.animate, R.layout.widget_disc_spin, R.drawable.widget_disc, disc)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight().clickable(openApp())) {
                Title(s, 13, Color.White)
                Spacer(GlanceModifier.height(5.dp))
                Progress(s, (size.width - disc - 150.dp).coerceAtLeast(40.dp), Color(0xFFF43F5E), Color(0x26FFFFFF))
            }
            Spacer(GlanceModifier.width(6.dp))
            Icon(R.drawable.ic_widget_prev, R.string.common_previous, command(AmpliWidget.CMD_PREV), 32.dp, 9.dp, R.drawable.widget_pill_chip, ink)
            Spacer(GlanceModifier.width(4.dp))
            PlayPause(s, 42.dp, R.drawable.widget_pill_play, Color.White)
            Spacer(GlanceModifier.width(4.dp))
            Icon(R.drawable.ic_widget_next, R.string.common_next, command(AmpliWidget.CMD_NEXT), 32.dp, 9.dp, R.drawable.widget_pill_chip, ink)
        }
    }
}

class VinylDeckReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = VinylDeckWidget() }
class ClockMusicReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ClockMusicWidget() }
class QuickMixesReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickMixesWidget() }
class CassetteReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CassetteWidget() }
class SlimPillReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SlimPillWidget() }

/** Opens the app and starts one of the Quick Mixes. Handled by [MusicPlayerApp] once the library is loaded. */
object WidgetLaunch {
    const val ACTION_MIX = "com.vikash.ampli.action.WIDGET_MIX"
    private const val EXTRA_MIX = "mix"
    const val MIX_FAVOURITES = "favourites"
    const val MIX_RECENT = "recent"
    const val MIX_MOST_PLAYED = "most_played"
    const val MIX_SHUFFLE = "shuffle"

    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** The mix asked for by the latest tap, until the app has started it. */
    val pending: kotlinx.coroutines.flow.StateFlow<String?> = _pending

    fun intent(context: Context, mix: String): Intent =
        Intent(context, MainActivity::class.java).setAction(ACTION_MIX).putExtra(EXTRA_MIX, mix)
            // Distinct data per mix, so the four PendingIntents don't collapse into one.
            .setData(android.net.Uri.parse("ampli-widget://mix/$mix"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Call from the activity with every intent it receives (onCreate and onNewIntent). */
    fun handle(intent: Intent?) {
        if (intent?.action != ACTION_MIX) return
        _pending.value = intent.getStringExtra(EXTRA_MIX)
        intent.action = null
    }

    fun done() {
        _pending.value = null
    }
}
