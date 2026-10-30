package com.vikash.mp3player.data

import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** One "made for you" list on the Home tab. */
data class Mix(
    val key: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    /** Daily Mix 3+ and Time capsule need Pro. */
    val pro: Boolean = false,
)

/**
 * Spotify-style recommendations built only from what is on the phone and how it is played
 * (play counts, last played, favourites, artist, album, genre, folder and year). Nothing leaves
 * the device. Every function is pure, so the Home tab can rebuild them on a background thread.
 */
object MixEngine {
    private val DAY = TimeUnit.DAYS.toMillis(1)

    /** Strings the engine needs, passed in so it stays free of Android resources. */
    data class Labels(
        val dailyMix: (Int) -> String,
        val andMore: (String) -> String,
        val decade: (Int) -> String,
    )

    // ---- building blocks ----------------------------------------------------------------------

    private fun plays(s: Song, stats: StatsData) = stats.songs[s.id]?.plays ?: 0
    private fun last(s: Song, stats: StatsData) = stats.songs[s.id]?.lastPlayedMs ?: 0L

    /** Artists ranked by how much they are played, favourites counting as extra plays. */
    fun topArtists(songs: List<Song>, stats: StatsData, favorites: Collection<String>): List<String> {
        val fav = favorites.toHashSet()
        return songs.groupBy { it.artist }
            .mapValues { (_, list) -> list.sumOf { plays(it, stats) + if (it.id in fav) 3 else 0 } }
            .filter { it.value > 0 }
            .entries.sortedByDescending { it.value }
            .map { it.key }
    }

    /** Spreads songs so the same artist rarely plays twice in a row. */
    fun spread(list: List<Song>): List<Song> {
        val byArtist = list.groupBy { it.artist }.values.map { it.toMutableList() }.sortedByDescending { it.size }.toMutableList()
        val out = ArrayList<Song>(list.size)
        var lastArtist: String? = null
        while (byArtist.isNotEmpty()) {
            val pick = byArtist.firstOrNull { it.first().artist != lastArtist } ?: byArtist.first()
            val s = pick.removeAt(0)
            out += s
            lastArtist = s.artist
            if (pick.isEmpty()) byArtist.remove(pick)
            byArtist.sortByDescending { it.size }
        }
        return out
    }

    /** How close [b] is to [a]: same artist, album, genre, folder, era. */
    private fun likeness(a: Song, b: Song): Int {
        var score = 0
        if (a.artist == b.artist) score += 5
        if (a.album.isNotBlank() && a.album == b.album) score += 3
        if (a.genre.isNotBlank() && a.genre.equals(b.genre, ignoreCase = true)) score += 3
        if (a.folder.isNotBlank() && a.folder == b.folder) score += 2
        if (a.year > 0 && b.year > 0 && kotlin.math.abs(a.year - b.year) <= 3) score += 1
        return score
    }

    // ---- song radio ---------------------------------------------------------------------------

    /**
     * Endless "radio" from a seed song: the closest songs on the phone, a few of them ones the
     * listener plays a lot, mixed so artists don't repeat back to back.
     */
    fun radio(seed: Song, all: List<Song>, stats: StatsData, exclude: Set<String> = emptySet(), count: Int = 25, random: Random = Random): List<Song> {
        val pool = all.filter { !it.isOnline && it.id != seed.id && it.id !in exclude }
        if (pool.isEmpty()) return emptyList()
        val scored = pool.map { s ->
            val bonus = (plays(s, stats).coerceAtMost(10)) * 0.3
            s to likeness(seed, s) + bonus + random.nextDouble() * 2.5
        }.sortedByDescending { it.second }
        val close = scored.filter { it.second >= 3 }.map { it.first }
        val picked = (close.take(count) + scored.map { it.first }.filter { it !in close }.shuffled(random))
            .distinctBy { it.id }.take(count)
        return spread(picked)
    }

    // ---- smart shuffle ------------------------------------------------------------------------

    /**
     * Songs to slot into a list for Smart shuffle: close to the list's own songs but not in it.
     * The caller puts one after every few songs.
     */
    fun recommendFor(list: List<Song>, all: List<Song>, stats: StatsData, count: Int, random: Random = Random): List<Song> {
        if (list.isEmpty()) return emptyList()
        val ids = list.map { it.id }.toHashSet()
        val seeds = list.shuffled(random).take(8)
        return seeds.flatMap { radio(it, all, stats, ids, 6, random) }
            .distinctBy { it.id }.shuffled(random).take(count)
    }

    // ---- Home tab lists -----------------------------------------------------------------------

    /**
     * Daily Mixes: each starts from one favourite artist and adds the artists that sound like them
     * (same genre, folder or era), mixing songs they love with ones they haven't played much.
     */
    fun dailyMixes(all: List<Song>, stats: StatsData, favorites: Collection<String>, labels: Labels, max: Int = 6): List<Mix> {
        val songs = all.filter { !it.isOnline }
        val top = topArtists(songs, stats, favorites)
        val seeds = if (top.isNotEmpty()) top else songs.groupBy { it.artist }.entries.sortedByDescending { it.value.size }.map { it.key }
        val used = HashSet<String>()
        val byArtist = songs.groupBy { it.artist }
        val mixes = mutableListOf<Mix>()
        for (artist in seeds) {
            if (mixes.size >= max) break
            if (artist in used) continue
            val own = byArtist[artist].orEmpty()
            if (own.isEmpty()) continue
            // Artists that share a genre, folder or era with the seed artist.
            val genres = own.mapNotNull { it.genre.lowercase().ifBlank { null } }.toSet()
            val folders = own.map { it.folder }.filter { it.isNotBlank() }.toSet()
            val years = own.map { it.year }.filter { it > 0 }
            val era = if (years.isEmpty()) null else years.average()
            val neighbours = byArtist.keys.asSequence()
                .filter { it != artist && it !in used }
                .map { other ->
                    val list = byArtist.getValue(other)
                    var score = 0
                    if (list.any { it.genre.lowercase() in genres }) score += 3
                    if (list.any { it.folder in folders }) score += 2
                    if (era != null && list.any { it.year > 0 && kotlin.math.abs(it.year - era) <= 4 }) score += 1
                    score += topRank(top, other)
                    other to score
                }
                .filter { it.second >= 2 }
                .sortedByDescending { it.second }
                .take(4).map { it.first }.toList()
            val artists = listOf(artist) + neighbours
            val pool = artists.flatMap { byArtist[it].orEmpty() }
            if (pool.size < 8) continue
            val loved = pool.sortedByDescending { plays(it, stats) }.take(30)
            val fresh = pool.filter { plays(it, stats) <= 1 }.shuffled().take(20)
            val list = spread((loved + fresh).distinctBy { it.id }.shuffled()).take(50)
            used += artist
            used += neighbours.take(2)
            val n = mixes.size + 1
            mixes += Mix(
                key = "daily$n",
                title = labels.dailyMix(n),
                subtitle = labels.andMore(artists.take(3).joinToString(", ")),
                songs = list,
                pro = n > 2,
            )
        }
        return mixes
    }

    private fun topRank(top: List<String>, artist: String): Int {
        val i = top.indexOf(artist)
        return when {
            i < 0 -> 0
            i < 10 -> 2
            else -> 1
        }
    }

    /** Recently played, newest first, one entry per song. */
    fun jumpBackIn(all: List<Song>, stats: StatsData, count: Int = 10): List<Song> =
        all.filter { last(it, stats) > 0 }.sortedByDescending { last(it, stats) }.take(count)

    /** Songs played a lot in the last 30 days. */
    fun onRepeat(all: List<Song>, stats: StatsData, now: Long = System.currentTimeMillis()): List<Song> =
        all.filter { plays(it, stats) >= 3 && now - last(it, stats) < 30 * DAY }
            .sortedByDescending { plays(it, stats) }.take(30)

    /** Favourites and old heavy rotation that haven't played for two months. */
    fun forgottenFavourites(all: List<Song>, stats: StatsData, favorites: Collection<String>, now: Long = System.currentTimeMillis()): List<Song> {
        val fav = favorites.toHashSet()
        return all.filter { (it.id in fav || plays(it, stats) >= 5) && now - last(it, stats) > 60 * DAY }
            .shuffled().take(30)
    }

    /** Never-played songs by the artists and genres they like most. */
    fun discover(all: List<Song>, stats: StatsData, favorites: Collection<String>): List<Song> {
        val top = topArtists(all, stats, favorites).take(15).toSet()
        val genres = all.filter { it.artist in top }.mapNotNull { it.genre.lowercase().ifBlank { null } }.toSet()
        return spread(all.filter { !it.isOnline && plays(it, stats) == 0 && (it.artist in top || it.genre.lowercase() in genres) }.shuffled()).take(30)
    }

    /** Added to the phone in the last 30 days. */
    fun newOnPhone(all: List<Song>, now: Long = System.currentTimeMillis()): List<Song> =
        all.filter { it.dateAddedSec > 0 && now - it.dateAddedSec * 1000 < 30 * DAY }
            .sortedByDescending { it.dateAddedSec }.take(50)

    /** One list per decade with at least 5 songs, newest decade first. */
    fun timeCapsules(all: List<Song>, labels: Labels): List<Mix> =
        all.filter { it.year in 1900..2100 }
            .groupBy { it.year / 10 * 10 }
            .filter { it.value.size >= 5 }
            .toSortedMap(compareByDescending { it })
            .map { (decade, list) -> Mix("decade$decade", labels.decade(decade), "", spread(list.shuffled()).take(50), pro = true) }
}
