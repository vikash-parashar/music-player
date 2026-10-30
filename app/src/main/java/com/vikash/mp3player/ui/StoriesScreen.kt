package com.vikash.mp3player.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.StoryAccess
import com.vikash.mp3player.data.StoryEpisode
import com.vikash.mp3player.data.StorySeries
import com.vikash.mp3player.data.StoriesRepository
import com.vikash.mp3player.monetization.Ads
import java.util.Locale

private val Crimson = Color(0xFFA31C1B)

private fun StorySeries.blurb() =
    if (Locale.getDefault().language == "hi" || descriptionEn.isBlank()) description else descriptionEn

@Composable
private fun genreLabel(genre: String) = when (genre) {
    "kids" -> stringResource(R.string.stories_genre_kids)
    "fantasy" -> stringResource(R.string.stories_genre_fantasy)
    "horror" -> stringResource(R.string.stories_genre_horror)
    else -> genre.replaceFirstChar { it.uppercase() }
}

/**
 * Ampli Stories: Pocket FM-style Hindi audio series. The first episodes of each series are free;
 * the rest open with a rewarded ad (a few a day) or the monthly Stories Pass.
 */
@Composable
fun StoriesSection(
    currentId: String?,
    pass: Boolean,
    passPrice: String?,
    onPlay: (List<Song>, Int, String) -> Unit,
    onBuyPass: () -> Unit,
) {
    val context = LocalContext.current
    val series by StoriesRepository.series.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { StoriesRepository.load(context) }
    val access = remember { StoryAccess(context) }
    val unlocked by access.unlocked.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = series.firstOrNull { it.id == openId }
    var locked by remember { mutableStateOf<Pair<StorySeries, StoryEpisode>?>(null) }

    val play: (StorySeries, StoryEpisode) -> Unit = { s, e ->
        if (!access.isOpen(s, e.n, pass)) {
            locked = s to e
        } else {
            // Queue this episode and the open ones after it, so a locked episode never autoplays.
            val run = s.episodes.dropWhile { it.n < e.n }.takeWhile { access.isOpen(s, it.n, pass) }
            access.setLastEpisode(s.id, e.n)
            onPlay(run.map(s::song), 0, s.title)
        }
    }

    AnimatedContent(open, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stories") { shown ->
        if (shown == null) {
            StoriesHome(series, pass, passPrice, onOpen = { openId = it.id }, onBuyPass = onBuyPass)
        } else {
            BackHandler { openId = null }
            SeriesDetail(
                shown, currentId, isOpen = { n -> access.isOpen(shown, n, pass) || shown.mediaId(n) in unlocked },
                lastEpisode = access.lastEpisode(shown.id), onBack = { openId = null }, onPlay = { play(shown, it) },
            )
        }
    }

    locked?.let { (s, e) ->
        val left = access.adUnlocksLeft()
        AlertDialog(
            onDismissRequest = { locked = null },
            title = { Text(stringResource(R.string.stories_locked_title, e.n)) },
            text = { Text(stringResource(R.string.stories_locked_body, s.freeEpisodes)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { locked = null; onBuyPass() }) {
                        Text(passPrice?.let { stringResource(R.string.stories_pass_buy_price, it) } ?: stringResource(R.string.stories_pass_buy))
                    }
                    if (Ads.rewardedConfigured && left > 0) {
                        TextButton(onClick = {
                            locked = null
                            val activity = context as? Activity ?: return@TextButton
                            Ads.showRewarded(
                                activity,
                                onReward = {
                                    access.unlockWithAd(s, e.n)
                                    Toast.makeText(context, context.getString(R.string.stories_unlocked, e.n), Toast.LENGTH_SHORT).show()
                                    play(s, e)
                                },
                                onUnavailable = {
                                    Toast.makeText(context, context.getString(R.string.trial_no_ad), Toast.LENGTH_SHORT).show()
                                },
                            )
                        }) { Text(stringResource(R.string.stories_watch_ad, left)) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { locked = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun StoriesHome(
    series: List<StorySeries>,
    pass: Boolean,
    passPrice: String?,
    onOpen: (StorySeries) -> Unit,
    onBuyPass: () -> Unit,
) {
    if (series.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text(stringResource(R.string.stories_kicker).uppercase(), fontSize = 11.sp, letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.stories_title), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
                    fontSize = 40.sp, lineHeight = 42.sp, color = Crimson)
                Text(stringResource(R.string.stories_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!pass) item { PassBanner(passPrice, onBuyPass) }
        item { StoryCover(series.first(), Modifier.fillMaxWidth().aspectRatio(16f / 10f), big = true) { onOpen(series.first()) } }
        items(series.drop(1).chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s -> StoryCover(s, Modifier.weight(1f).aspectRatio(3f / 4f)) { onOpen(s) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Text(stringResource(R.string.stories_ai_note), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PassBanner(price: String?, onBuy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Crimson).clickable(onClick = onBuy).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.stories_pass_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(stringResource(R.string.stories_pass_desc), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(price?.let { stringResource(R.string.stories_pass_per_month, it) } ?: stringResource(R.string.stories_pass_cta),
                color = Crimson, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** Editorial cover drawn from the series colours: dark ground, a red light beam, a big serif title. */
@Composable
fun StoryCover(series: StorySeries, modifier: Modifier, big: Boolean = false, onClick: () -> Unit) {
    val bg = Color(series.bg)
    val fg = Color(series.fg)
    val accent = Color(series.accent)
    Box(
        modifier.clip(RoundedCornerShape(if (big) 22.dp else 16.dp))
            .background(Brush.verticalGradient(listOf(bg, Color.Black)))
            .border(1.dp, fg.copy(alpha = 0.12f), RoundedCornerShape(if (big) 22.dp else 16.dp))
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val beam = Path().apply {
                moveTo(w * 0.62f, 0f); lineTo(w * 0.78f, 0f); lineTo(w * 0.58f, h); lineTo(w * 0.36f, h); close()
            }
            drawPath(beam, Brush.verticalGradient(listOf(accent.copy(alpha = 0.05f), accent.copy(alpha = 0.75f)), 0f, h))
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), Offset(w * 0.48f, h), w * 0.5f), w * 0.5f, Offset(w * 0.48f, h))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(if (big) 18.dp else 12.dp)) {
            Text(genreLabel(series.genre).uppercase(), color = fg.copy(alpha = 0.75f), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Text(series.title, color = fg, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
                fontSize = if (big) 34.sp else 22.sp, lineHeight = if (big) 36.sp else 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.stories_cover_meta, series.episodes.size, series.freeEpisodes),
                color = fg.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1,
            )
        }
        if (big) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(18.dp).size(52.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
    }
}

@Composable
private fun SeriesDetail(
    series: StorySeries,
    currentId: String?,
    isOpen: (Int) -> Boolean,
    lastEpisode: Int,
    onBack: () -> Unit,
    onPlay: (StoryEpisode) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Box {
                StoryCover(series, Modifier.fillMaxWidth().height(230.dp).padding(12.dp), big = true) {
                    onPlay(series.episodes.firstOrNull { it.n == lastEpisode } ?: series.episodes.first())
                }
                IconButton(onClick = onBack, modifier = Modifier.padding(16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = Color.White)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(series.author, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(series.blurb(), style = MaterialTheme.typography.bodyMedium)
                val resume = series.episodes.firstOrNull { it.n == lastEpisode }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onPlay(resume ?: series.episodes.first()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Crimson, contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (resume != null) stringResource(R.string.stories_continue, resume.n) else stringResource(R.string.stories_play_first))
                    }
                    if (resume != null && resume.n > 1) {
                        OutlinedButton(onClick = { onPlay(series.episodes.first()) }) { Text(stringResource(R.string.stories_from_start)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.stories_episodes, series.episodes.size), fontWeight = FontWeight.Bold)
            }
        }
        items(series.episodes, key = { it.n }) { e ->
            val open = isOpen(e.n)
            val playing = currentId == series.mediaId(e.n)
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(e) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("%02d".format(e.n), fontWeight = FontWeight.Bold, color = if (playing) Crimson else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (playing) Crimson else MaterialTheme.colorScheme.onSurface)
                    val mins = (e.durationSec + 30) / 60
                    Text(
                        buildString {
                            if (mins > 0) append(stringResource(R.string.stories_minutes, mins))
                            if (e.n <= series.freeEpisodes) { if (isNotEmpty()) append(" · "); append(stringResource(R.string.stories_free)) }
                        },
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (open) Icons.Filled.PlayArrow else Icons.Filled.Lock,
                    if (open) stringResource(R.string.stories_play) else stringResource(R.string.stories_locked),
                    tint = if (open) Crimson else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
