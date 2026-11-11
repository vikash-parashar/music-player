package com.vikash.mp3player

import android.app.Application
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.vikash.mp3player.data.AppPrefs
import com.vikash.mp3player.data.JamendoRepository
import com.vikash.mp3player.data.LocalMusicRepository
import com.vikash.mp3player.data.LocalVideoRepository
import com.vikash.mp3player.data.PlayStats
import com.vikash.mp3player.data.PlaylistStore
import com.vikash.mp3player.data.SongDownloader
import com.vikash.mp3player.monetization.BillingManager

/** Simple manual dependency container; swap for Hilt if the app grows. */
class MusicApp : Application(), SingletonImageLoader.Factory {

    val localMusic by lazy { LocalMusicRepository(this) }
    val localVideos by lazy { LocalVideoRepository(this) }
    val jamendo by lazy { JamendoRepository() }
    val downloader by lazy { SongDownloader(this) }
    val billing by lazy { BillingManager(this) }
    val prefs by lazy { AppPrefs(this) }
    val playStats by lazy { PlayStats(this) }
    val playlists by lazy { PlaylistStore(this) }
    val spoken by lazy { com.vikash.mp3player.data.SpokenRepository() }
    val bookmarks by lazy { com.vikash.mp3player.data.BookmarkStore(this) }
    val podcastFollows by lazy { com.vikash.mp3player.data.PodcastFollows(this) }
    val engagement by lazy { com.vikash.mp3player.data.Engagement(this) }

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    /** Pro features: bought, inside the 14-day welcome or 24-hour ad trial, or a closed-test tester. */
    val proUnlocked: kotlinx.coroutines.flow.StateFlow<Boolean> by lazy {
        kotlinx.coroutines.flow.combine(billing.adsRemoved, com.vikash.mp3player.monetization.ProTrial.active) { bought, trial -> bought || trial || billing.testerPro }
            .stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, billing.adsRemoved.value || billing.testerPro || com.vikash.mp3player.monetization.ProTrial.isActive())
    }

    /** The Pro looks (widgets, cassettes, themes, seek bars): Pro, or the Cassette & Themes Pack on its own. */
    val looksUnlocked: kotlinx.coroutines.flow.StateFlow<Boolean> by lazy {
        kotlinx.coroutines.flow.combine(proUnlocked, billing.looksPack) { pro, pack -> pro || pack }
            .stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, proUnlocked.value || billing.looksPack.value)
    }

    /** No ads: Pro bought, or any free Pro trial running. Closed-test testers still see ads. */
    val adFree: kotlinx.coroutines.flow.StateFlow<Boolean> by lazy {
        kotlinx.coroutines.flow.combine(billing.adsRemoved, com.vikash.mp3player.monetization.ProTrial.active) { bought, trial -> bought || trial }
            .stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, billing.adsRemoved.value || com.vikash.mp3player.monetization.ProTrial.isActive())
    }

    override fun onCreate() {
        super.onCreate()
        com.vikash.mp3player.data.CrashLog.install(this)
        com.vikash.mp3player.monetization.ProTrial.init(this)
        // Users upgrading from builds without the welcome screens already gave music access.
        val audio = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        val onboarded = getSharedPreferences("ampli_prefs", MODE_PRIVATE).getBoolean("welcome_done", false) ||
            checkSelfPermission(audio) == android.content.pm.PackageManager.PERMISSION_GRANTED
        com.vikash.mp3player.data.OwnerHooks.init(this, onboarded)
        // Lite mode switched in Settings: the widget's disc, bars and glow stop or start.
        appScope.launch { prefs.liteMode.drop(1).collect { com.vikash.mp3player.widget.WidgetState.redraw(this@MusicApp) } }
        // The style widgets show the heart, the sleep timer and Pro state: redraw when those change in the app.
        appScope.launch { prefs.favorites.drop(1).collect { com.vikash.mp3player.widget.WidgetState.redraw(this@MusicApp) } }
        appScope.launch { com.vikash.mp3player.playback.SleepTimer.endsAtMillis.drop(1).collect { com.vikash.mp3player.widget.WidgetState.redraw(this@MusicApp) } }
        appScope.launch { looksUnlocked.drop(1).collect { com.vikash.mp3player.widget.WidgetState.redraw(this@MusicApp) } }
    }

    // Lite mode: covers appear at once instead of fading in (read when the loader is first made).
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(!prefs.liteMode.value)
            // Real album art first; covers found with "Find album art" only fill the gaps.
            .components { add(com.vikash.mp3player.data.SavedAlbumArtInterceptor(this@MusicApp)) }
            .build()
}
