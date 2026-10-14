package com.vikash.mp3player.playback

import android.content.Context
import androidx.media3.common.Player

/**
 * Remembers where you stopped in long tracks (20 minutes or more: audiobooks, podcasts,
 * DJ mixes, kathas, and every audiobook chapter or podcast episode) so they carry on from there next time instead of starting over.
 */
class ResumePositions(context: Context) {

    private val prefs = context.getSharedPreferences("resume_positions", Context.MODE_PRIVATE)

    fun save(player: Player) {
        val id = player.currentMediaItem?.mediaId ?: return
        val duration = player.duration
        // Audiobook chapters and podcast episodes always resume, even short ones.
        val spoken = id.startsWith("librivox:") || id.startsWith("podcast:")
        if (duration < if (spoken) MIN_SPOKEN_MS else MIN_LENGTH_MS) return
        val pos = player.currentPosition
        // Near the start or the end there's nothing worth resuming.
        if (pos < 10_000 || duration - pos < 30_000) clear(id) else prefs.edit().putLong(id, pos).apply()
    }

    fun positionFor(id: String): Long? = prefs.getLong(id, -1L).takeIf { it > 0 }

    fun clear(id: String) {
        if (prefs.contains(id)) prefs.edit().remove(id).apply()
    }

    private companion object {
        const val MIN_LENGTH_MS = 20 * 60_000L
        const val MIN_SPOKEN_MS = 2 * 60_000L
    }
}
