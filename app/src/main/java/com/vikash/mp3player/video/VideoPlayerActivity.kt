package com.vikash.mp3player.video

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.vikash.mp3player.R
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Full-screen video player. Plays a list (the folder the user opened) so next/previous work,
 * or a single file handed over by another app through "Open with".
 * Music in Ampli pauses automatically because both players request audio focus.
 */
class VideoPlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var zoom: ZoomLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::zoom.isInitialized) zoom.reset() // the frame changed size; start again from 100%
    }

    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            // Landscape videos open sideways, portrait ones (Reels, camera) stay upright.
            if (videoSize.width > 0 && videoSize.height > 0) {
                requestedOrientation = if (videoSize.width > videoSize.height)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            zoom.reset() // each video starts at normal size
        }

        override fun onPlaybackStateChanged(state: Int) {
            val p = player ?: return
            if (state == Player.STATE_ENDED && !p.hasNextMediaItem()) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        view = (layoutInflater.inflate(R.layout.video_player_view, null) as PlayerView).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setShowNextButton(true)
            setShowPreviousButton(true)
            setShowShuffleButton(false)
            keepScreenOn = true
            setFullscreenButtonClickListener { toggleOrientation() }
        }
        // Pinch to zoom: only the picture scales, the controls stay where they are.
        val hint = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0x99000000.toInt()) }
            alpha = 0f
        }
        zoom = ZoomLayout(this).apply {
            addView(view)
            addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(48) })
            target = view.findViewById(androidx.media3.ui.R.id.exo_content_frame)
            onZoom = { pct ->
                hint.text = getString(R.string.video_zoom, pct)
                hint.animate().cancel()
                hint.alpha = 1f
                hint.animate().alpha(0f).setStartDelay(700).setDuration(300).start()
            }
        }
        setContentView(zoom)
        start(intent, savedInstanceState?.getLong(KEY_POSITION) ?: 0L, savedInstanceState?.getInt(KEY_INDEX) ?: -1)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        start(intent, 0L, -1)
    }

    private fun start(intent: Intent, positionMs: Long, savedIndex: Int) {
        val uris: List<Uri> = intent.getStringArrayListExtra(EXTRA_URIS)?.map(Uri::parse)
            ?: listOfNotNull(intent.data)
        if (uris.isEmpty()) { finish(); return }
        val index = (if (savedIndex >= 0) savedIndex else intent.getIntExtra(EXTRA_INDEX, 0)).coerceIn(0, uris.lastIndex)

        val p = player ?: ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player = it; view.player = it; it.addListener(listener) }
        p.setMediaItems(uris.map { MediaItem.fromUri(it) }, index, positionMs)
        p.prepare()
        p.play()
    }

    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /** Home button while a video plays: keep watching in a small floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = player ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && p.isPlaying) {
            val size = p.videoSize
            val ratio = if (size.width > 0 && size.height > 0)
                Rational(size.width, size.height).coerceRatio() else Rational(16, 9)
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        view.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode) zoom.reset()
    }

    override fun onStop() {
        super.onStop()
        // Closing the PiP window stops the video too.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) player?.pause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        player?.let {
            outState.putLong(KEY_POSITION, it.currentPosition)
            outState.putInt(KEY_INDEX, it.currentMediaItemIndex)
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    /** Android only allows PiP ratios between 1:2.39 and 2.39:1. */
    private fun Rational.coerceRatio(): Rational {
        val v = toFloat()
        return when {
            v > 2.39f -> Rational(239, 100)
            v < 1 / 2.39f -> Rational(100, 239)
            else -> this
        }
    }

    companion object {
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val KEY_INDEX = "index"

        fun start(context: Context, uris: List<Uri>, index: Int) {
            context.startActivity(
                Intent(context, VideoPlayerActivity::class.java)
                    .putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map(Uri::toString)))
                    .putExtra(EXTRA_INDEX, index)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }
}
