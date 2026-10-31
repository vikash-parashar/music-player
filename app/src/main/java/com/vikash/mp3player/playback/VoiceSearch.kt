package com.vikash.mp3player.playback

import com.vikash.mp3player.data.Song
import java.text.Normalizer
import java.util.Locale

/**
 * "Hey Google, play Coldplay on Ampli" and the search box in Android Auto.
 *
 * Matches the spoken words against artist, album and title. When the whole query names an
 * artist or album, the result is all of that artist's or album's songs (album order); otherwise
 * songs are ranked by how many words they match. An empty query ("play some music") means
 * everything, shuffled by the caller.
 */
object VoiceSearch {
    /** Songs to queue for [query]; empty when nothing matched. */
    fun find(songs: List<Song>, query: String, artistHint: String? = null, albumHint: String? = null): List<Song> {
        val q = norm(query)
        if (q.isEmpty() && artistHint.isNullOrBlank() && albumHint.isNullOrBlank()) return songs

        // The assistant often tells us the artist or album directly.
        albumHint?.let(::norm)?.takeIf { it.isNotEmpty() }?.let { a ->
            songs.filter { norm(it.album).contains(a) }.takeIf { it.isNotEmpty() }?.let { return it }
        }
        artistHint?.let(::norm)?.takeIf { it.isNotEmpty() }?.let { a ->
            val byArtist = songs.filter { norm(it.artist).contains(a) }
            if (byArtist.isNotEmpty() && q.isNotEmpty()) {
                val titled = byArtist.filter { norm(it.title).contains(q) }
                if (titled.isNotEmpty()) return titled + (byArtist - titled.toSet())
            }
            if (byArtist.isNotEmpty()) return byArtist
        }
        if (q.isEmpty()) return emptyList()

        // Whole query is an artist or album name: play all of it.
        songs.filter { norm(it.artist) == q }.takeIf { it.isNotEmpty() }?.let { return it }
        songs.filter { norm(it.album) == q }.takeIf { it.isNotEmpty() }?.let { return it }
        // Exact title first, then the rest of that artist.
        songs.filter { norm(it.title) == q }.takeIf { it.isNotEmpty() }?.let { exact ->
            val artist = norm(exact.first().artist)
            return exact + songs.filter { norm(it.artist) == artist && it !in exact }
        }

        val words = q.split(' ').filter { it.length > 1 || q.length <= 2 }
        if (words.isEmpty()) return emptyList()
        return songs
            .map { song ->
                val title = norm(song.title); val artist = norm(song.artist); val album = norm(song.album)
                var score = 0
                for (w in words) {
                    if (title.contains(w)) score += 3
                    if (artist.contains(w)) score += 2
                    if (album.contains(w)) score += 1
                }
                if (title.contains(q)) score += 5
                if (artist.contains(q)) score += 4
                song to score
            }
            .filter { (_, score) -> score >= words.size * 2 || score >= 5 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(200)
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
