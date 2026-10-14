package com.vikash.mp3player.cast

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.framework.CastContext
import com.vikash.mp3player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Moves playback between the phone and a Chromecast. When a cast session starts, the queue,
 * song and position move to the TV or speaker; when it ends, they come back to the phone.
 * Every screen, the notification and the lock screen keep working because they all talk to the
 * same media session, which simply switches player.
 */
@OptIn(UnstableApi::class)
class CastSwitcher(
    private val context: Context,
    private val local: Player,
    private val session: MediaSession,
    private val scope: CoroutineScope,
) : SessionAvailabilityListener {

    private val server = CastMediaServer(context)
    private val castPlayer: CastPlayer? = runCatching {
        @Suppress("DEPRECATION")
        CastPlayer(CastContext.getSharedInstance(context))
    }.getOrNull() // No Google Play services (some car head units, Huawei): casting just isn't offered.

    /** What was playing on the TV, kept so it can come back to the phone when casting ends. */
    private var snapshot: Snapshot? = null
    private var snapshotJob: Job? = null
    /** The phone's own version of every item sent to the TV, to put back exactly when casting ends. */
    private val originals = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()

    val isCasting: Boolean get() = castPlayer != null && session.player === castPlayer

    /**
     * Songs added from the phone while casting (play next, a new list) arrive with phone-only
     * addresses; this turns them into items the TV can play. Songs it can't serve are left out.
     */
    suspend fun adaptForCast(items: List<MediaItem>): List<MediaItem> =
        withContext(Dispatchers.IO) { items.mapNotNull { toCast(it) } }

    init {
        castPlayer?.setSessionAvailabilityListener(this)
        if (castPlayer?.isCastSessionAvailable == true) onCastSessionAvailable()
    }

    override fun onCastSessionAvailable() {
        val cast = castPlayer ?: return
        val from = Snapshot.of(local)
        local.pause()
        scope.launch {
            val items = withContext(Dispatchers.IO) { from.items.map { toCast(it) } }
            if (items.any { it == null }) {
                Toast.makeText(context, context.getString(R.string.cast_needs_same_wifi), Toast.LENGTH_LONG).show()
                return@launch
            }
            if (items.isNotEmpty()) {
                cast.setMediaItems(items.filterNotNull(), from.index, from.positionMs)
                cast.repeatMode = from.repeatMode
                cast.playWhenReady = from.playWhenReady
                cast.prepare()
            }
            local.stop()
            session.player = cast
            CastState.set(true)
            snapshotJob?.cancel()
            snapshotJob = scope.launch {
                while (isActive) {
                    if (cast.mediaItemCount > 0) snapshot = Snapshot.of(cast)
                    delay(1_000)
                }
            }
        }
    }

    override fun onCastSessionUnavailable() {
        snapshotJob?.cancel()
        val back = snapshot
        snapshot = null
        if (session.player !== local) {
            session.player = local
            if (back != null && back.items.isNotEmpty()) {
                local.setMediaItems(back.items.map { toLocal(it) }, back.index, back.positionMs)
                local.repeatMode = back.repeatMode
                local.playWhenReady = false // don't start blasting from the phone speaker
                local.prepare()
            }
        }
        server.stop()
        originals.clear()
        CastState.set(false)
    }

    fun release() {
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        server.stop()
        CastState.set(false)
    }

    /** A queue item the Chromecast can play: files on the phone get a Wi-Fi address. */
    private fun toCast(item: MediaItem): MediaItem? {
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return null
        originals[item.mediaId] = item
        val isLocal = uri.scheme == "content" || uri.scheme == "file"
        val url = if (isLocal) server.urlFor(uri) ?: return null else uri.toString()
        val mime = (if (isLocal) runCatching { context.contentResolver.getType(uri) }.getOrNull() else null)
            ?: MimeTypes.AUDIO_MPEG
        val art = item.mediaMetadata.artworkUri?.let { a ->
            if (a.scheme != "content") a
            else if (runCatching { context.contentResolver.openInputStream(a)?.close() != null }.getOrDefault(false)) server.urlFor(a)?.let(Uri::parse)
            else null
        }
        return MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setUri(url)
            .setMimeType(mime)
            .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(art).build())
            .build()
    }

    /** Back to a phone item: songs on the phone are found again by their id. */
    private fun toLocal(item: MediaItem): MediaItem {
        val id = item.mediaId
        originals[id]?.let { return it }
        val uri: Uri = if (id.startsWith("local:")) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            ContentUris.withAppendedId(collection, id.removePrefix("local:").toLongOrNull() ?: 0L)
        } else {
            item.localConfiguration?.uri ?: Uri.EMPTY
        }
        val art = if (id.startsWith("local:")) null else item.mediaMetadata.artworkUri
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(art).build())
            .build()
    }

    private data class Snapshot(
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val repeatMode: Int,
    ) {
        companion object {
            fun of(p: Player) = Snapshot(
                items = List(p.mediaItemCount) { p.getMediaItemAt(it) },
                index = p.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = p.currentPosition.coerceAtLeast(0),
                playWhenReady = p.playWhenReady,
                repeatMode = p.repeatMode,
            )
        }
    }
}

/** Whether music is playing on a Chromecast right now, for the cast icon in the player. */
object CastState {
    private val _casting = MutableStateFlow(false)
    val casting: StateFlow<Boolean> = _casting.asStateFlow()
    internal fun set(on: Boolean) { _casting.value = on }
}
