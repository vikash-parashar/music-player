package com.vikash.mp3player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.vikash.mp3player.MainActivity
import com.vikash.mp3player.MusicApp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.LocalMusicRepository
import com.vikash.mp3player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owns the player so music keeps going with the screen off or the app closed.
 *
 * - Lock screen, notification shade and Bluetooth car stereos: Media3 publishes the session, so
 *   they show title, artist and artwork, and get play/pause, previous, next and seek. We add
 *   Shuffle and Repeat buttons on top (see [controlButtons]).
 * - Android Auto: this is a [MediaLibraryService], so the car screen can browse Songs, Albums
 *   and Folders and start playback without touching the phone.
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var library: LocalMusicRepository

    /** Cached local songs for the car browse tree; reloaded when the root is requested. */
    private var songs: List<Song> = emptyList()

    // Listening time of the current song, for play counts and listening stats. Tracked here,
    // not in the UI, so songs played with the app closed still count.
    private var trackedItem: MediaItem? = null
    private var trackedDurationMs = 0L
    private var heardMs = 0L
    private var playingSince = 0L // elapsedRealtime when playback started; 0 while paused

    override fun onCreate() {
        super.onCreate()
        library = LocalMusicRepository(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true, // pause for calls and other apps
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK) // keep streaming with the screen off
            .build()

        EqualizerController.attach(this, player.audioSessionId)

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_ampli) }
        )

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val librarySession = MediaLibrarySession.Builder(this, player, Callback())
            .setSessionActivity(openApp)
            .setCustomLayout(controlButtons(player))
            .build()
        session = librarySession

        // Keep the Shuffle/Repeat icons in sync wherever they are shown.
        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                librarySession.setCustomLayout(controlButtons(player))
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                librarySession.setCustomLayout(controlButtons(player))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (trackedItem == null) trackedItem = player.currentMediaItem
                    playingSince = SystemClock.elapsedRealtime()
                } else {
                    addHeardTime()
                    playingSince = 0L
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.duration > 0) trackedDurationMs = player.duration
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                reportListening()
                trackedItem = mediaItem
                trackedDurationMs = if (player.duration > 0) player.duration else 0L
                if (playingSince != 0L) playingSince = SystemClock.elapsedRealtime()
            }
        })

        scope.launch {
            SleepTimer.endsAtMillis.collectLatest { endsAt ->
                if (endsAt == null) return@collectLatest
                delay((endsAt - System.currentTimeMillis()).coerceAtLeast(0))
                player.pause()
                SleepTimer.cancel()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Stop the service when the user swipes the app away while nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player ?: return stopSelf()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    private fun addHeardTime() {
        if (playingSince != 0L) {
            val now = SystemClock.elapsedRealtime()
            heardMs += now - playingSince
            playingSince = now
        }
    }

    /** Hands the finished song's listening time to [com.vikash.mp3player.data.PlayStats]. */
    private fun reportListening() {
        addHeardTime()
        val item = trackedItem
        if (item != null && heardMs > 0) {
            (application as MusicApp).playStats.record(
                id = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                heardMs = heardMs,
                durationMs = trackedDurationMs,
            )
        }
        heardMs = 0L
    }

    override fun onDestroy() {
        reportListening()
        scope.cancel()
        EqualizerController.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ shuffle + repeat buttons

    private fun controlButtons(player: Player): ImmutableList<CommandButton> {
        val shuffle = CommandButton.Builder()
            .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.shuffle_on else R.string.shuffle_off))
            .setIconResId(if (player.shuffleModeEnabled) R.drawable.ic_shuffle else R.drawable.ic_shuffle_off)
            .setSessionCommand(CMD_SHUFFLE)
            .build()
        val (repeatIcon, repeatLabel) = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat to R.string.repeat_all
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one to R.string.repeat_one
            else -> R.drawable.ic_repeat_off to R.string.repeat_off
        }
        val repeat = CommandButton.Builder()
            .setDisplayName(getString(repeatLabel))
            .setIconResId(repeatIcon)
            .setSessionCommand(CMD_REPEAT)
            .build()
        return ImmutableList.of(shuffle, repeat)
    }

    // ------------------------------------------------------------------ Android Auto browse tree

    private fun folderItem(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private suspend fun loadSongs(): List<Song> =
        try {
            library.loadSongs().also { songs = it }
        } catch (_: SecurityException) {
            emptyList() // storage permission not granted yet; the phone app asks for it
        }

    /** The songs under a browse node, in the order the car should queue them. */
    private fun songsUnder(parentId: String): List<Song> = when {
        parentId == NODE_SONGS -> songs
        parentId.startsWith(PREFIX_ALBUM) -> songs.filter { it.album == parentId.removePrefix(PREFIX_ALBUM) }
        parentId.startsWith(PREFIX_FOLDER) -> songs.filter { it.folder == parentId.removePrefix(PREFIX_FOLDER) }
        else -> emptyList()
    }

    private fun childrenOf(parentId: String): List<MediaItem> = when (parentId) {
        NODE_ROOT -> listOf(
            folderItem(NODE_SONGS, getString(R.string.tab_songs)),
            folderItem(NODE_ALBUMS, getString(R.string.tab_albums)),
            folderItem(NODE_FOLDERS, getString(R.string.tab_folders)),
        )
        NODE_ALBUMS -> songs.map { it.album }.distinct().sorted().map { folderItem(PREFIX_ALBUM + it, it) }
        NODE_FOLDERS -> songs.map { it.folder }.filter { it.isNotEmpty() }.distinct().sorted()
            .map { folderItem(PREFIX_FOLDER + it, it) }
        // Playable children carry "<parent>|<songId>" so a tap can queue the whole list.
        else -> songsUnder(parentId).map { song ->
            song.toMediaItem().buildUpon().setMediaId(parentId + SEP + song.id).build()
        }
    }

    private inner class Callback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(CMD_SHUFFLE)
                .add(CMD_REPEAT)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(controlButtons(session.player))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                ACTION_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                ACTION_REPEAT -> player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val result = SettableFuture.create<LibraryResult<MediaItem>>()
            scope.launch {
                loadSongs()
                result.set(LibraryResult.ofItem(folderItem(NODE_ROOT, getString(R.string.app_name)), params))
            }
            return result
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val result = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            scope.launch {
                if (songs.isEmpty()) loadSongs()
                val all = childrenOf(parentId)
                val from = (page * pageSize).coerceIn(0, all.size)
                val to = (from + pageSize).coerceIn(from, all.size)
                result.set(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params))
            }
            return result
        }

        // Media items arriving from a controller have their playback URI stripped for security,
        // so the phone UI also puts it in requestMetadata and we restore it here.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri
                if (item.localConfiguration == null && uri != null) item.buildUpon().setUri(uri).build() else item
            }.toMutableList()
        )

        // A tap on a song in Android Auto: queue the whole list it came from, starting at that song.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val single = mediaItems.singleOrNull()
            val parent = single?.mediaId?.substringBefore(SEP, missingDelimiterValue = "")
            if (single != null && !parent.isNullOrEmpty() && single.requestMetadata.mediaUri == null) {
                val songId = single.mediaId.substringAfter(SEP)
                val list = songsUnder(parent)
                val index = list.indexOfFirst { it.id == songId }
                if (index >= 0) {
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(list.map { it.toMediaItem() }, index, 0L)
                    )
                }
            }
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
    }

    private companion object {
        const val ACTION_SHUFFLE = "ampli.SHUFFLE"
        const val ACTION_REPEAT = "ampli.REPEAT"
        val CMD_SHUFFLE = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)
        val CMD_REPEAT = SessionCommand(ACTION_REPEAT, Bundle.EMPTY)

        const val NODE_ROOT = "root"
        const val NODE_SONGS = "songs"
        const val NODE_ALBUMS = "albums"
        const val NODE_FOLDERS = "folders"
        const val PREFIX_ALBUM = "album:"
        const val PREFIX_FOLDER = "folder:"
        const val SEP = "|"
    }
}
