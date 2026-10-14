package com.vikash.mp3player.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.data.StatsData
import com.vikash.mp3player.ui.theme.Ampli
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PLAY_LINK = "https://play.google.com/store/apps/details?id=com.vikash.ampli"

private data class Summary(
    val monthMs: Long,
    val totalMs: Long,
    val totalPlays: Int,
    val songsPlayed: Int,
    val topSongs: List<Triple<String, String, Int>>, // title, artist, plays
    val topArtists: List<Pair<String, Long>>, // artist, listened ms
)

private fun StatsData.summary(unknownArtist: String): Summary {
    val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    val played = songs.values.filter { it.plays > 0 }
    return Summary(
        monthMs = monthMs[month] ?: 0L,
        totalMs = monthMs.values.sum(),
        totalPlays = played.sumOf { it.plays },
        songsPlayed = played.size,
        topSongs = played.sortedByDescending { it.plays }.take(5).map { Triple(it.title, it.artist, it.plays) },
        topArtists = songs.values.filter { it.artist.isNotBlank() && !it.artist.startsWith("Unknown") && it.artist != unknownArtist }
            .groupBy { it.artist }
            .map { (artist, list) -> artist to list.sumOf { it.listenedMs } }
            .sortedByDescending { it.second }
            .take(5),
    )
}

private fun hours(context: Context, ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> context.getString(R.string.stats_hours_minutes, h, m)
        else -> context.getString(R.string.stats_minutes, m)
    }
}

/** "Ampli Wrapped": listening stats from this phone, with a one-tap share. */
@Composable
fun StatsScreen(stats: StatsData, onClear: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val unknownArtist = stringResource(R.string.library_unknown_artist)
    val s = remember(stats, unknownArtist) { stats.summary(unknownArtist) }
    var confirmClear by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val text = buildString {
                        appendLine(context.getString(R.string.stats_share_header))
                        appendLine(context.getString(R.string.stats_share_month, hours(context, s.monthMs)))
                        s.topSongs.firstOrNull()?.let { appendLine(context.getString(R.string.stats_share_top_song, it.first, it.second)) }
                        s.topArtists.firstOrNull()?.let { appendLine(context.getString(R.string.stats_share_top_artist, it.first)) }
                        appendLine()
                        appendLine(context.getString(R.string.stats_share_footer))
                        append(PLAY_LINK)
                    }
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.stats_share_title)))
                }) { Icon(Icons.Default.Share, stringResource(R.string.stats_share)) }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ampli.Gradient).padding(20.dp),
                ) {
                    Text(stringResource(R.string.stats_this_month), color = Color.White.copy(alpha = 0.85f))
                    Text(hours(context, s.monthMs), style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.stats_all_time, hours(context, s.totalMs)) + " · " +
                            pluralStringResource(R.plurals.stats_plays, s.totalPlays, s.totalPlays) + " · " +
                            pluralStringResource(R.plurals.library_song_count, s.songsPlayed, s.songsPlayed),
                        color = Color.White,
                    )
                }

                if (s.totalPlays == 0) {
                    Text(
                        stringResource(R.string.stats_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.stats_top_songs), style = MaterialTheme.typography.titleMedium)
                            s.topSongs.forEachIndexed { i, (title, artist, plays) ->
                                RankRow(i + 1, title, artist, pluralStringResource(R.plurals.stats_plays, plays, plays))
                            }
                        }
                    }
                    if (s.topArtists.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.stats_top_artists), style = MaterialTheme.typography.titleMedium)
                            s.topArtists.forEachIndexed { i, (artist, ms) -> RankRow(i + 1, artist, null, hours(context, ms)) }
                        }
                    }
                }

                Text(
                    stringResource(R.string.stats_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.stats_clear)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.stats_clear_title)) },
            text = { Text(stringResource(R.string.stats_clear_text)) },
            confirmButton = {
                Button(
                    onClick = { onClear(); confirmClear = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.common_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun RankRow(rank: Int, title: String, subtitle: String?, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
