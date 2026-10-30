package com.vikash.mp3player.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Mix
import com.vikash.mp3player.data.MixEngine
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.StatsData
import com.vikash.mp3player.ui.skin.skinSurface
import com.vikash.mp3player.ui.skin.LocalSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Everything the Home tab shows, built off the main thread. */
private data class HomeData(
    val quick: List<Mix> = emptyList(),
    val daily: List<Mix> = emptyList(),
    val lists: List<Mix> = emptyList(),
    val jumpBack: List<Song> = emptyList(),
    val decades: List<Mix> = emptyList(),
)

/**
 * Home: Spotify-style "made for you" lists from the songs on the phone and how they are played.
 * Daily Mix 1 and 2 are free; the rest of the Daily Mixes and Time capsule need Pro.
 */
@Composable
fun HomeScreen(
    songs: List<Song>,
    stats: StatsData,
    favorites: List<String>,
    isPro: Boolean,
    onPlay: (songs: List<Song>, index: Int, label: String) -> Unit,
    onGetPro: () -> Unit,
    /** Opens the Stories tab (Ampli Stories row). */
    onOpenStories: () -> Unit = {},
) {
    val context = LocalContext.current
    val stories by com.vikash.mp3player.data.StoriesRepository.series.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { com.vikash.mp3player.data.StoriesRepository.load(context) }
    val labels = MixEngine.Labels(
        dailyMix = { context.getString(R.string.home_daily_mix, it) },
        andMore = { context.getString(R.string.home_and_more, it) },
        decade = { context.getString(R.string.home_decade, it) },
    )
    val data by produceState(HomeData(), songs, stats, favorites) {
        value = withContext(Dispatchers.Default) {
            val local = songs.filter { !it.isOnline }
            val favSet = favorites.toHashSet()
            val daily = MixEngine.dailyMixes(local, stats, favorites, labels)
            val onRepeat = MixEngine.onRepeat(local, stats)
            val forgotten = MixEngine.forgottenFavourites(local, stats, favorites)
            val discover = MixEngine.discover(local, stats, favorites)
            val fresh = MixEngine.newOnPhone(local)
            val favs = local.filter { it.id in favSet }
            val lists = listOfNotNull(
                Mix("repeat", context.getString(R.string.home_on_repeat), context.getString(R.string.home_on_repeat_sub), onRepeat).takeIf { onRepeat.size >= 3 },
                Mix("discover", context.getString(R.string.home_discover), context.getString(R.string.home_discover_sub), discover).takeIf { discover.size >= 5 },
                Mix("forgotten", context.getString(R.string.home_forgotten), context.getString(R.string.home_forgotten_sub), forgotten).takeIf { forgotten.size >= 3 },
                Mix("new", context.getString(R.string.home_new), context.getString(R.string.home_new_sub), fresh).takeIf { fresh.isNotEmpty() },
            )
            val quick = (listOfNotNull(
                Mix("favs", context.getString(R.string.library_my_favorites), "", MixEngine.spread(favs.shuffled())).takeIf { favs.isNotEmpty() },
            ) + daily.take(2) + lists + daily.drop(2)).take(6)
            HomeData(quick, daily, lists, MixEngine.jumpBackIn(local, stats), MixEngine.timeCapsules(local, labels))
        }
    }

    fun open(mix: Mix) {
        if (mix.songs.isEmpty()) return
        if (mix.pro && !isPro) onGetPro() else onPlay(mix.songs, 0, mix.title)
    }

    if (songs.none { !it.isOnline }) {
        EmptyMessage(stringResource(R.string.home_empty))
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Greeting() }
        if (data.quick.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.quick.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { mix -> QuickTile(mix, locked = mix.pro && !isPro, Modifier.weight(1f)) { open(mix) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (data.daily.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_made_for_you)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.daily, key = { it.key }) { mix -> MixCard(mix, locked = mix.pro && !isPro) { open(mix) } }
                }
            }
        }
        if (stories.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_stories_title)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(stories, key = { "story-" + it.id }) { s ->
                        StoryCover(s, Modifier.width(150.dp).height(200.dp), onClick = onOpenStories)
                    }
                }
            }
        }
        if (data.jumpBack.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_jump_back)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.jumpBack, key = { it.id }) { song ->
                        val label = stringResource(R.string.home_jump_back)
                        SongCard(song) { onPlay(data.jumpBack, data.jumpBack.indexOf(song), label) }
                    }
                }
            }
        }
        if (data.lists.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_your_lists)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.lists, key = { it.key }) { mix -> MixCard(mix, locked = false) { open(mix) } }
                }
            }
        }
        if (data.decades.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_time_capsule)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.decades, key = { it.key }) { mix -> DecadeCard(mix, locked = !isPro) { open(mix) } }
                }
            }
        }
    }
}

@Composable
private fun Greeting() {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val res = when (hour) {
        in 5..11 -> R.string.home_good_morning
        in 12..16 -> R.string.home_good_afternoon
        in 17..21 -> R.string.home_good_evening
        else -> R.string.home_good_night
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        Text(stringResource(res), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.home_greeting_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
    )
}

/** Card surface that follows the Pro look when one is on. */
@Composable
private fun Modifier.homeCard(shape: RoundedCornerShape, classic: Boolean, color: Color): Modifier =
    if (classic) clip(shape).background(color) else skinSurface(shape)

@Composable
private fun QuickTile(mix: Mix, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val classic = LocalSkin.current.isClassic
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.height(56.dp).homeCard(shape, classic, MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Collage(mix.songs, 56.dp, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        Text(
            mix.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (locked) Icon(Icons.Default.Lock, null, Modifier.padding(end = 10.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MixCard(mix: Mix, locked: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        Box {
            Collage(mix.songs, 150.dp, RoundedCornerShape(12.dp))
            // Title band at the bottom of the cover, like Spotify's Daily Mix art.
            Box(
                Modifier.matchParentSizeCompat(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))),
            )
            Text(
                mix.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape)
                    .background(if (locked) Color.Black.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (locked) Icons.Default.Lock else Icons.Default.PlayArrow,
                    stringResource(if (locked) R.string.home_unlock_pro else R.string.common_play),
                    tint = if (locked) Color.White else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (mix.subtitle.isNotBlank()) Text(
            mix.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun DecadeCard(mix: Mix, locked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(width = 130.dp, height = 84.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFF7C3AED))))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(mix.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomStart))
        Icon(
            if (locked) Icons.Default.Lock else Icons.Default.AutoAwesome,
            null, tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
        )
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable(onClick = onClick)) {
        Artwork(song.artworkUri, 120.dp)
        Text(song.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 2x2 grid of the first four different covers, or one cover when there are fewer. */
@Composable
private fun Collage(songs: List<Song>, size: Dp, shape: RoundedCornerShape) {
    val arts: List<Uri?> = songs.distinctBy { it.album.ifBlank { it.id } }.take(4).map { it.artworkUri }
    Box(Modifier.size(size).clip(shape)) {
        if (arts.size < 4) {
            Artwork(arts.firstOrNull(), size, Modifier.clip(shape))
        } else {
            val half = size / 2
            Column {
                Row { Artwork(arts[0], half, Modifier.clip(RoundedCornerShape(0.dp))); Artwork(arts[1], half, Modifier.clip(RoundedCornerShape(0.dp))) }
                Row { Artwork(arts[2], half, Modifier.clip(RoundedCornerShape(0.dp))); Artwork(arts[3], half, Modifier.clip(RoundedCornerShape(0.dp))) }
            }
        }
    }
}

/** Same size as the cover below it (a Box child can't use matchParentSize outside BoxScope helpers here). */
private fun Modifier.matchParentSizeCompat(size: Dp): Modifier = this.size(size)
