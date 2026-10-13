package com.vikash.mp3player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vikash.mp3player.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class NowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
)

data class PlayerUiState(
    val connected: Boolean = false,
    val current: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<NowPlaying> = emptyList(),
    val currentIndex: Int = 0,
)

/**
 * The UI's handle on [PlaybackService]. Exposes player state as a flow and remembers the
 * queue and position, so the app resumes where the listener left off (like Musicolet).
 */
class PlayerConnection(private val context: Context) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED)
            ) saveQueue()
        }

        // A deleted file or dropped stream should not stop the whole queue.
        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            if (c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            }
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            if (c.mediaItemCount == 0) restoreQueue(c)
            publish()
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        saveQueue()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    fun play(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(song), 0)
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(song), 0)
        c.addMediaItem(song.toMediaItem())
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun skipToQueueItem(index: Int) { controller?.seekTo(index, 0L) }
    fun removeFromQueue(index: Int) { controller?.removeMediaItem(index) }
    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun publish() {
        val c = controller ?: return
        _state.value = PlayerUiState(
            connected = true,
            current = c.currentMediaItem?.toNowPlaying(),
            isPlaying = c.isPlaying,
            durationMs = c.duration.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toNowPlaying() },
            currentIndex = c.currentMediaItemIndex,
        )
    }

    private fun saveQueue() {
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSaved() }
        prefs.edit()
            .putString("items", json.encodeToString(items))
            .putInt("index", c.currentMediaItemIndex)
            .putLong("position", c.currentPosition)
            .apply()
    }

    private fun restoreQueue(c: MediaController) {
        val raw = prefs.getString("items", null) ?: return
        val items = runCatching { json.decodeFromString<List<SavedItem>>(raw) }.getOrNull() ?: return
        if (items.isEmpty()) return
        val index = prefs.getInt("index", 0).coerceIn(0, items.lastIndex)
        c.setMediaItems(items.map { it.toMediaItem() }, index, prefs.getLong("position", 0))
        c.prepare() // ready to resume, but don't start playing on app launch
    }
}

@Serializable
private data class SavedItem(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
) {
    fun toMediaItem() = buildMediaItem(id, uri.toUri(), title, artist, "", artworkUri?.toUri())
}

private fun MediaItem.toSaved() = SavedItem(
    id = mediaId,
    uri = (localConfiguration?.uri ?: requestMetadata.mediaUri).toString(),
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    artworkUri = mediaMetadata.artworkUri?.toString(),
)

private fun MediaItem.toNowPlaying() = NowPlaying(
    mediaId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    artworkUri = mediaMetadata.artworkUri,
)

fun Song.toMediaItem(): MediaItem = buildMediaItem(id, uri, title, artist, album, artworkUri)

private fun buildMediaItem(
    id: String, uri: Uri, title: String, artist: String, album: String, artworkUri: Uri?,
): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    // PlaybackService restores the URI from here; see its onAddMediaItems.
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
    )
    .build()
