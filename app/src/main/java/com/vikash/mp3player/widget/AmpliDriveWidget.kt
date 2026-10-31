package com.vikash.mp3player.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vikash.mp3player.MainActivity
import com.vikash.mp3player.MusicApp
import com.vikash.mp3player.R
import com.vikash.mp3player.ui.CarLaunch

/**
 * "Ampli Drive": the Car mode look on the home screen. The green car under a light beam on the
 * left, DRIVE / differently, a spinning CD with the song, and previous / play-pause / next.
 * Two colour sets are offered as separate entries in the widget list ([DriveCrimsonWidget],
 * [DriveOrangeWidget]); they share this code and differ only in [DrivePalette].
 *
 * Layouts by size: 2x1 = CD + play over the car; 4x1 = car, CD, song, play; 4x2 and up = the full
 * look with logo and headline. Tapping the card opens Car mode; tapping the song opens the player.
 * Same state, buttons and redraw path as [AmpliWidget].
 */
abstract class AmpliDriveWidget(private val palette: DrivePalette) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val change by WidgetState.changes.collectAsState()
            // Re-read on every change; the state is a few SharedPreferences values.
            val s = remember(change) { WidgetState.read(context) }
            val lite = runCatching { (context.applicationContext as MusicApp).prefs.liteMode.value }.getOrDefault(false)
            Content(s, animate = s.playing && !lite)
        }
    }

    @Composable
    private fun Content(s: WidgetState, animate: Boolean) {
        val context = LocalContext.current
        val size = LocalSize.current
        val openCar = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setAction(CarLaunch.ACTION_CAR_MODE)
                .putExtra(CarLaunch.EXTRA_AUTOPLAY, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        Box(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(palette.bg).clickable(openCar),
        ) {
            when {
                size.width >= MEDIUM.width && size.height >= MEDIUM.height -> Medium(s, animate, size)
                size.width >= WIDE.width -> Wide(s, animate, size)
                else -> Small(s, animate)
            }
        }
    }

    /** The car (with the beam over it) filling [width] from the left, fading into the card colour. */
    @Composable
    private fun CarArt(width: Dp, beamAt: Float) {
        Box(GlanceModifier.width(width).fillMaxHeight()) {
            Image(
                ImageProvider(R.drawable.widget_drive_car), LocalContext.current.getString(R.string.widget_drive_open_car),
                GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
            Row(GlanceModifier.fillMaxSize()) {
                Spacer(GlanceModifier.width(width * beamAt))
                Image(
                    ImageProvider(R.drawable.widget_drive_beam), null,
                    GlanceModifier.width(width * 0.16f).fillMaxHeight(), contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(ColorProvider(palette.beam)),
                )
            }
        }
    }

    @Composable
    private fun Fade(modifier: GlanceModifier) {
        Image(
            ImageProvider(R.drawable.widget_drive_fade), null, modifier, contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(ColorProvider(palette.bg)),
        )
    }

    /** 4x2 and up: logo, car on the left, headline + song + buttons on the right. */
    @Composable
    private fun Medium(s: WidgetState, animate: Boolean, size: DpSize) {
        val carW = size.width * 0.62f
        CarArt(carW, beamAt = 0.56f)
        Row(GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.width(carW * 0.55f))
            Fade(GlanceModifier.width(carW * 0.45f).fillMaxHeight())
        }
        Row(GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.width(carW))
            Box(GlanceModifier.defaultWeight().fillMaxHeight().background(palette.bg)) {}
        }
        Logo()
        Row(GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.width(size.width * 0.5f))
            Column(
                GlanceModifier.defaultWeight().fillMaxHeight().padding(end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val big = size.height >= TALL_H
                Image(
                    ImageProvider(R.drawable.widget_drive_title), "DRIVE",
                    GlanceModifier.size(if (big) 158.dp else 116.dp, if (big) 34.dp else 25.dp),
                    contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(ColorProvider(palette.accent)),
                )
                Image(
                    ImageProvider(R.drawable.widget_drive_subtitle), "differently",
                    GlanceModifier.size(if (big) 80.dp else 62.dp, if (big) 18.dp else 14.dp),
                    contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(ColorProvider(palette.ink)),
                )
                Spacer(GlanceModifier.height(if (big) 10.dp else 6.dp))
                SongRow(s, animate, disc = if (big) 44.dp else 36.dp, titleSp = 14, artistSp = 11)
                Spacer(GlanceModifier.height(if (big) 10.dp else 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SideButton(R.drawable.ic_widget_prev, R.string.common_previous, AmpliWidget.CMD_PREV, 30.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    PlayButton(s, if (big) 42.dp else 36.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    SideButton(R.drawable.ic_widget_next, R.string.common_next, AmpliWidget.CMD_NEXT, 30.dp)
                }
            }
        }
    }

    /** 4x1: car on the left, then the CD, song and play/pause. */
    @Composable
    private fun Wide(s: WidgetState, animate: Boolean, size: DpSize) {
        val carW = size.width * 0.46f
        CarArt(carW, beamAt = 0.6f)
        Row(GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.width(carW * 0.45f))
            Fade(GlanceModifier.width(carW * 0.55f).fillMaxHeight())
        }
        Row(
            GlanceModifier.fillMaxSize().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(GlanceModifier.width(carW * 0.78f))
            Box(GlanceModifier.defaultWeight()) { SongRow(s, animate, disc = 40.dp, titleSp = 14, artistSp = 11) }
            Spacer(GlanceModifier.width(6.dp))
            PlayButton(s, 38.dp)
        }
    }

    /** 2x1: the car softly behind the CD and play/pause. */
    @Composable
    private fun Small(s: WidgetState, animate: Boolean) {
        Image(ImageProvider(R.drawable.widget_drive_car), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(GlanceModifier.fillMaxSize().background(palette.bg.copy(alpha = 0.55f))) {}
        Row(
            GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Disc(animate, 44.dp)
            Spacer(GlanceModifier.width(14.dp))
            PlayButton(s, 38.dp)
        }
    }

    @Composable
    private fun Logo() {
        Row(GlanceModifier.padding(start = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(palette.logo), null, GlanceModifier.size(18.dp))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "AMPLI",
                style = TextStyle(color = ColorProvider(palette.ink), fontSize = 10.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    /** CD + title + artist; tapping it opens the player instead of Car mode. */
    @Composable
    private fun SongRow(s: WidgetState, animate: Boolean, disc: Dp, titleSp: Int, artistSp: Int) {
        val context = LocalContext.current
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Disc(animate, disc)
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    s.title.ifEmpty { "Ampli" }, maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = titleSp.sp),
                )
                Text(
                    s.artist.ifEmpty { context.getString(R.string.widget_tap_play) }, maxLines = 1,
                    style = TextStyle(color = ColorProvider(palette.ink.copy(alpha = 0.85f)), fontSize = artistSp.sp),
                )
            }
        }
    }

    /** The CD spins (a framework animation, no app code) only while playing outside Lite mode. */
    @Composable
    private fun Disc(animate: Boolean, size: Dp) {
        val context = LocalContext.current
        Box(GlanceModifier.size(size)) {
            if (animate) {
                AndroidRemoteViews(RemoteViews(context.packageName, palette.spinLayout), GlanceModifier.fillMaxSize())
            } else {
                Image(ImageProvider(palette.disc), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }

    @Composable
    private fun SideButton(icon: Int, label: Int, cmd: String, size: Dp) {
        val context = LocalContext.current
        Image(
            ImageProvider(icon), context.getString(label),
            GlanceModifier.size(size).background(ImageProvider(R.drawable.widget_drive_ring)).padding(8.dp).clickable(command(cmd)),
            colorFilter = ColorFilter.tint(ColorProvider(palette.ink)),
        )
    }

    @Composable
    private fun PlayButton(s: WidgetState, size: Dp) {
        val context = LocalContext.current
        Image(
            ImageProvider(if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
            context.getString(if (s.playing) R.string.common_pause else R.string.common_play),
            GlanceModifier.size(size).background(ImageProvider(palette.playBg)).padding(size * 0.26f)
                .clickable(if (s.title.isEmpty()) actionStartActivity<MainActivity>() else command(AmpliWidget.CMD_TOGGLE)),
        )
    }

    private fun command(name: String): Action =
        actionRunCallback<PlayerAction>(actionParametersOf(AmpliWidget.CommandKey to name))

    companion object {
        private val SMALL = DpSize(100.dp, 40.dp)
        private val WIDE = DpSize(250.dp, 40.dp)
        private val MEDIUM = DpSize(250.dp, 100.dp)
        private val TALL_H = 150.dp

        /** Redraws both Ampli Drive widgets (called from [WidgetState.redraw]). */
        suspend fun redrawAll(context: Context) {
            runCatching { DriveCrimsonWidget().updateAll(context) }
            runCatching { DriveOrangeWidget().updateAll(context) }
        }
    }
}

/** Colours and drawables for one Ampli Drive colour set. */
class DrivePalette(
    val bg: Color,
    val accent: Color,
    val beam: Color,
    val ink: Color,
    val disc: Int,
    val spinLayout: Int,
    val playBg: Int,
    val logo: Int,
) {
    companion object {
        val CRIMSON = DrivePalette(
            bg = Color(0xFF06110C), accent = Color(0xFFC62A22), beam = Color(0x99D23A2E), ink = Color(0xFFC9DDD0),
            disc = R.drawable.widget_drive_disc_crimson, spinLayout = R.layout.widget_drive_spin_crimson,
            playBg = R.drawable.widget_drive_play_crimson, logo = R.drawable.widget_drive_logo_crimson,
        )
        val ORANGE = DrivePalette(
            bg = Color(0xFF07111E), accent = Color(0xFFF2732A), beam = Color(0x99FF8030), ink = Color(0xFFCFE2F2),
            disc = R.drawable.widget_drive_disc_orange, spinLayout = R.layout.widget_drive_spin_orange,
            playBg = R.drawable.widget_drive_play_orange, logo = R.drawable.widget_drive_logo_orange,
        )
    }
}

class DriveCrimsonWidget : AmpliDriveWidget(DrivePalette.CRIMSON)
class DriveOrangeWidget : AmpliDriveWidget(DrivePalette.ORANGE)

class DriveCrimsonReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DriveCrimsonWidget()
}

class DriveOrangeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DriveOrangeWidget()
}
