package com.vikash.mp3player

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.vikash.mp3player.data.JamendoRepository
import com.vikash.mp3player.data.LocalMusicRepository
import com.vikash.mp3player.data.SongDownloader
import com.vikash.mp3player.monetization.BillingManager

/** Simple manual dependency container; swap for Hilt if the app grows. */
class MusicApp : Application(), SingletonImageLoader.Factory {

    val localMusic by lazy { LocalMusicRepository(this) }
    val jamendo by lazy { JamendoRepository() }
    val downloader by lazy { SongDownloader(this) }
    val billing by lazy { BillingManager(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
