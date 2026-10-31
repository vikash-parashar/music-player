package com.vikash.mp3player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.StatsData

/** Which ready-made mix a Car mode card starts. */
internal enum class CarMoodKind { Shuffle, Favourites, MostPlayed, TopArtist, RecentlyAdded, OldIsGold }

/**
 * One Car mode card. [songs] are in play order; [shuffle] means mix them up again on every tap,
 * so "Shuffle all" never starts with the same song twice. [artist] is set only for the top singer card.
 */
internal data class CarMood(
    val kind: CarMoodKind,
    val songs: List<Song>,
    val shuffle: Boolean,
    val artist: String = "",
)

internal object CarMoods {
    /** Songs from this year or earlier count as "Old is gold". */
    const val OLD_YEAR = 2005
    private const val MIN_SONGS = 3
    private const val MIN_OLD = 5
    private const val LIMIT = 50

    /**
     * Builds the cards from the phone's own songs. A card with too few songs is left out, so a new
     * install with no play history still shows Shuffle all and Recently added.
     */
    fun build(all: List<Song>, favorites: List<String>, stats: StatsData, unknownArtist: String): List<CarMood> {
        val local = all.filter { !it.isOnline }
        if (local.isEmpty()) return emptyList()
        fun plays(s: Song) = stats.songs[s.id]?.plays ?: 0
        fun last(s: Song) = stats.songs[s.id]?.lastPlayedMs ?: 0L
        val out = mutableListOf<CarMood>()

        out += CarMood(CarMoodKind.Shuffle, local, shuffle = true)

        val byId = local.associateBy { it.id }
        val favs = favorites.mapNotNull { byId[it] }
        if (favs.isNotEmpty()) out += CarMood(CarMoodKind.Favourites, favs, shuffle = true)

        val most = local.filter { plays(it) > 0 }
            .sortedWith(compareByDescending<Song> { plays(it) }.thenByDescending { last(it) }).take(LIMIT)
        if (most.size >= MIN_SONGS) out += CarMood(CarMoodKind.MostPlayed, most, shuffle = false)

        // Top singer: the one played most; before there is any history, the one with the most songs.
        val byArtist = local.filter { it.artist.isNotBlank() && it.artist != unknownArtist }.groupBy { it.artist }
            .filterValues { it.size >= MIN_SONGS }
        byArtist.maxWithOrNull(compareBy<Map.Entry<String, List<Song>>> { e -> e.value.sumOf { plays(it) } }.thenBy { it.value.size })
            ?.let { (artist, songs) -> out += CarMood(CarMoodKind.TopArtist, songs, shuffle = true, artist = artist) }

        val recent = local.filter { it.dateAddedSec > 0 }.sortedByDescending { it.dateAddedSec }.take(LIMIT)
        if (recent.size >= MIN_SONGS) out += CarMood(CarMoodKind.RecentlyAdded, recent, shuffle = false)

        val old = local.filter { it.year in 1..OLD_YEAR }
        if (old.size >= MIN_OLD) out += CarMood(CarMoodKind.OldIsGold, old, shuffle = true)
        return out
    }
}

private data class MoodStyle(val icon: ImageVector, val from: Color, val to: Color)

private fun CarMoodKind.style() = when (this) {
    CarMoodKind.Shuffle -> MoodStyle(Icons.Default.Shuffle, Color(0xFFE11D48), Color(0xFF7F1D1D))
    CarMoodKind.Favourites -> MoodStyle(Icons.Default.Favorite, Color(0xFFDB2777), Color(0xFF581C87))
    CarMoodKind.MostPlayed -> MoodStyle(Icons.Default.Whatshot, Color(0xFFEA580C), Color(0xFF7C2D12))
    CarMoodKind.TopArtist -> MoodStyle(Icons.Default.Person, Color(0xFF7C3AED), Color(0xFF1E1B4B))
    CarMoodKind.RecentlyAdded -> MoodStyle(Icons.Default.NewReleases, Color(0xFF0891B2), Color(0xFF164E63))
    CarMoodKind.OldIsGold -> MoodStyle(Icons.Default.Radio, Color(0xFFD97706), Color(0xFF78350F))
}

/** The card's name as shown and as the queue name ("Arijit Singh mix"). */
@Composable
internal fun CarMood.label(): String = when (kind) {
    CarMoodKind.Shuffle -> stringResource(R.string.car_mood_shuffle)
    CarMoodKind.Favourites -> stringResource(R.string.car_mood_favourites)
    CarMoodKind.MostPlayed -> stringResource(R.string.car_mood_most_played)
    CarMoodKind.TopArtist -> stringResource(R.string.car_mood_artist_mix, artist)
    CarMoodKind.RecentlyAdded -> stringResource(R.string.car_mood_recently_added)
    CarMoodKind.OldIsGold -> stringResource(R.string.car_mood_old_is_gold)
}

/**
 * A row of big swipeable cards; one tap starts that mix. The row runs edge to edge past the
 * screen padding ([bleed]) so the next card peeks in and invites a swipe. Its own sideways drags
 * are taken by the row, so swiping the cards doesn't also skip the song.
 */
@Composable
internal fun CarMoodRow(
    moods: List<CarMood>,
    active: CarMoodKind?,
    bleed: Dp,
    onPick: (CarMood, String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        val i = moods.indexOfFirst { it.kind == active }
        if (i >= 0) listState.animateScrollToItem(i)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().bleed(bleed),
        contentPadding = PaddingValues(horizontal = bleed),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(moods, key = { _, m -> m.kind.name }) { _, mood ->
            val label = mood.label()
            MoodCard(mood, label, playing = mood.kind == active) { onPick(mood, label) }
        }
    }
}

@Composable
private fun MoodCard(mood: CarMood, label: String, playing: Boolean, onClick: () -> Unit) {
    val style = mood.kind.style()
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.size(width = 150.dp, height = 92.dp).clip(shape)
            .background(Brush.linearGradient(listOf(style.from, style.to)))
            .then(if (playing) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        if (playing) {
            Text(
                stringResource(R.string.car_mood_playing), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color(0x59000000))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.car_mood_songs, mood.songs.size, mood.songs.size),
                color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
            )
        }
    }
}

/** Lets a child draw [h] wider on both sides than its parent's padding allows. */
internal fun Modifier.bleed(h: Dp) = layout { measurable, constraints ->
    val extra = h.roundToPx() * 2
    val w = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
