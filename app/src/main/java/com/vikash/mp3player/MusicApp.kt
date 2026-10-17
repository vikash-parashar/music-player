package com.vikash.mp3player

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        com.vikash.mp3player.data.CrashLog.install(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
