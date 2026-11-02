package com.vikash.mp3player.playback

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
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
    val speed: Float = 1f,
    val pitch: Float = 1f,
    /** Saved queues (Musicolet-style): one per folder, album, playlist... Names with song counts. */
    val queues: List<QueueInfo> = emptyList(),
    val activeQueue: Int = 0,
    /** Smart shuffle (Pro) is mixing suggested songs into the queue. */
    val smartShuffle: Boolean = false,
    /** Songs Smart shuffle added, marked with a sparkle in the queue. */
    val smartIds: Set<String> = emptySet(),
)

data class QueueInfo(val name: String, val size: Int)

/**
 * The UI's handle on [PlaybackService]. Exposes player state as a flow and remembers the
 * queue and position, so the app resumes where the listener left off (like Musicolet).
 *
 * Multiple queues: playing a different folder, album or playlist starts a new queue instead of
 * wiping the current one. Each saved queue keeps its song and position, so switching back
 * resumes exactly where it was.
 */
class PlayerConnection(private val context: Context) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** All queues; the active one is refreshed from the player whenever it is saved. */
    private var queues: MutableList<SavedQueue> = mutableListOf()
    private var active = 0

    /**
     * "Keep the music going": when a queue is about to run out, this supplies similar songs to
     * add (song radio from the last song). Set by the view model; null turns it off.
     */
    var radioSource: ((seedId: String, exclude: Set<String>) -> List<Song>)? = null

    private var smartShuffle = false
    private val smartIds = mutableSetOf<String>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) keepGoing()
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

    /**
     * Plays [songs] from [startIndex]. [source] names the list (album, folder, "Online"...):
     * a new source gets its own queue and the current queue is kept for later.
     */
    fun play(songs: List<Song>, startIndex: Int, source: String = DEFAULT_QUEUE) {
        val c = controller ?: return
        smartShuffle = false
        smartIds.clear()
        snapshotActive(c)
        val existing = queues.indexOfFirst { it.name == source }
        when {
            existing >= 0 -> active = existing
            queues.isEmpty() || c.mediaItemCount == 0 -> {
                if (queues.isEmpty()) queues.add(SavedQueue(source)) else queues[active] = SavedQueue(source)
            }
            else -> {
                queues.add(SavedQueue(source))
                active = queues.lastIndex
                trimQueues()
            }
        }
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    /** Switches to another saved queue, resuming its song and position. Keeps playing if it was. */
    fun switchQueue(index: Int) {
        val c = controller ?: return
        if (index == active || index !in queues.indices) return
        val wasPlaying = c.isPlaying
        snapshotActive(c)
        active = index
        loadActive(c)
        if (wasPlaying) c.play()
        saveQueue()
    }

    fun removeQueue(index: Int) {
        val c = controller ?: return
        if (index !in queues.indices) return
        if (queues.size == 1) {
            c.clearMediaItems()
            queues.clear()
            active = 0
            saveQueue()
            return
        }
        if (index == active) {
            queues.removeAt(index)
            active = active.coerceAtMost(queues.lastIndex)
            loadActive(c)
        } else {
            snapshotActive(c)
            queues.removeAt(index)
            if (index < active) active--
        }
        saveQueue()
    }

    /** Keeps at most [MAX_QUEUES]; the oldest inactive queues go first. */
    private fun trimQueues() {
        while (queues.size > MAX_QUEUES) {
            val drop = if (active == 0) 1 else 0
            queues.removeAt(drop)
            if (drop < active) active--
        }
    }

    private fun snapshotActive(c: MediaController) {
        if (c.mediaItemCount == 0 || queues.isEmpty()) return
        queues[active] = queues[active].copy(
            items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSaved() },
            index = c.currentMediaItemIndex,
            positionMs = c.currentPosition,
        )
    }

    private fun loadActive(c: MediaController) {
        val q = queues.getOrNull(active) ?: return
        if (q.items.isEmpty()) {
            c.clearMediaItems()
            return
        }
        c.setMediaItems(q.items.map { it.toMediaItem() }, q.index.coerceIn(0, q.items.lastIndex), q.positionMs)
        c.prepare()
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

    /** Playback speed and pitch, each 0.5x to 2x. Handy for practice, podcasts and audiobooks. */
    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        controller?.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 2f), pitch.coerceIn(0.5f, 2f))
    }

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

    /** Adds song radio to the end of the queue when only the last song or two are left. */
    private fun keepGoing() {
        val c = controller ?: return
        val source = radioSource ?: return
        if (c.repeatMode != Player.REPEAT_MODE_OFF || c.mediaItemCount == 0) return
        if (c.mediaItemCount - c.currentMediaItemIndex > 2) return
        val seed = c.currentMediaItem?.mediaId ?: return
        if (!seed.startsWith("local:")) return
        val inQueue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }.toSet()
        val more = source(seed, inQueue).take(15)
        if (more.isNotEmpty()) c.addMediaItems(more.map { it.toMediaItem() })
    }

    /** Starts endless radio from [songs] (seed first) as its own queue. */
    fun startRadio(songs: List<Song>, name: String) {
        if (songs.isEmpty()) return
        play(songs, 0, name)
    }

    /**
     * Smart shuffle (Pro): turns shuffle on and slots one suggested song after every three songs
     * still to play. [suggest] gets the queue's song ids and returns songs not in it. Turning it
     * off takes the suggested songs out again.
     */
    fun setSmartShuffle(on: Boolean, suggest: (List<String>) -> List<Song>) {
        val c = controller ?: return
        if (on == smartShuffle) return
        smartShuffle = on
        if (on) {
            val ids = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
            val wanted = ((c.mediaItemCount - c.currentMediaItemIndex - 1) / 3).coerceIn(1, 15)
            val picks = suggest(ids).take(wanted)
            // Insert from the back so earlier positions stay valid.
            val slots = picks.indices.map { c.currentMediaItemIndex + 1 + (it + 1) * 3 + it }
            picks.zip(slots).reversed().forEach { (song, at) ->
                c.addMediaItem(at.coerceAtMost(c.mediaItemCount), song.toMediaItem())
                smartIds += song.id
            }
            c.shuffleModeEnabled = true
        } else {
            for (i in c.mediaItemCount - 1 downTo 0) {
                if (i != c.currentMediaItemIndex && c.getMediaItemAt(i).mediaId in smartIds) c.removeMediaItem(i)
            }
            smartIds.clear()
        }
        publish()
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
            speed = c.playbackParameters.speed,
            pitch = c.playbackParameters.pitch,
            queues = queues.mapIndexed { i, q -> QueueInfo(q.name, if (i == active) c.mediaItemCount else q.items.size) },
            activeQueue = active,
            smartShuffle = smartShuffle,
            smartIds = smartIds.toSet(),
        )
    }

    private fun saveQueue() {
        val c = controller ?: return
        if (queues.isEmpty() && c.mediaItemCount > 0) queues.add(SavedQueue(DEFAULT_QUEUE))
        snapshotActive(c)
        prefs.edit()
            .putString(KEY_QUEUES, json.encodeToString(queues.toList()))
            .putInt(KEY_ACTIVE, active)
            .apply()
    }

    private fun restoreQueue(c: MediaController) {
        val (saved, index) = readSavedQueues(prefs, json)
        queues = saved.toMutableList()
        active = index
        loadActive(c) // ready to resume, but don't start playing on app launch
    }

    private companion object {
        const val DEFAULT_QUEUE = "All songs"
        const val MAX_QUEUES = 10
        const val KEY_QUEUES = "queues"
        const val KEY_ACTIVE = "active"
    }
}

/** The saved queues and the index of the active one. */
private fun readSavedQueues(prefs: SharedPreferences, json: Json): Pair<List<SavedQueue>, Int> {
    val raw = prefs.getString("queues", null)
    val queues = if (raw != null) {
        runCatching { json.decodeFromString<List<SavedQueue>>(raw) }.getOrNull().orEmpty()
    } else {
        // Builds before multiple queues saved a single queue under these keys.
        val old = prefs.getString("items", null)
            ?.let { runCatching { json.decodeFromString<List<SavedItem>>(it) }.getOrNull() }
        if (old.isNullOrEmpty()) emptyList()
        else listOf(SavedQueue("All songs", old, prefs.getInt("index", 0), prefs.getLong("position", 0)))
    }
    return queues to prefs.getInt("active", 0).coerceIn(0, (queues.size - 1).coerceAtLeast(0))
}

/**
 * Loads the queue the app saved last time into [player], for a player that starts empty because
 * the app had been closed (a widget or headset button woke the service). Returns false when
 * nothing was saved.
 */
fun loadSavedQueue(context: Context, player: Player): Boolean {
    val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)
    val (queues, active) = readSavedQueues(prefs, Json { ignoreUnknownKeys = true })
    val q = queues.getOrNull(active)?.takeIf { it.items.isNotEmpty() } ?: return false
    player.setMediaItems(q.items.map { it.toMediaItem() }, q.index.coerceIn(0, q.items.lastIndex), q.positionMs)
    player.prepare()
    return true
}

/**
 * The queue the app saved last time, for Media3's playback resumption (earbuds or the car pressing
 * play, or the media card in quick settings, after Android closed the app). Null when nothing was saved.
 */
fun savedQueueForResumption(context: Context): androidx.media3.session.MediaSession.MediaItemsWithStartPosition? {
    val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)
    val (queues, active) = readSavedQueues(prefs, Json { ignoreUnknownKeys = true })
    val q = queues.getOrNull(active)?.takeIf { it.items.isNotEmpty() } ?: return null
    return androidx.media3.session.MediaSession.MediaItemsWithStartPosition(
        q.items.map { it.toMediaItem() }, q.index.coerceIn(0, q.items.lastIndex), q.positionMs,
    )
}

@Serializable
private data class SavedQueue(
    val name: String,
    val items: List<SavedItem> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
)

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
    .apply { if (id.startsWith(com.vikash.mp3player.data.RADIO_HLS_PREFIX)) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8) }
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
