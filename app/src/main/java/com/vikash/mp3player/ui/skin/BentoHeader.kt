package com.vikash.mp3player.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.StatsData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Bento look only: the top of the Library becomes a board of tiles of different sizes, listening
 * time this month (with the last six months as bars), songs, favourites, playlists and the most
 * played song. Tapping it opens the full stats.
 */
@Composable
fun BentoHeader(songs: Int, favourites: Int, playlists: Int, stats: StatsData, onOpenStats: () -> Unit) {
    val s = LocalSkin.current
    val months = remember(stats) {
        val fmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val cal = Calendar.getInstance()
        val keys = (0 until 6).map { i ->
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, -i)
            fmt.format(Date(c.timeInMillis))
        }.reversed()
        keys.map { stats.monthMs[it] ?: 0L }
    }
    val thisMonth = months.last()
    val top = remember(stats) { stats.songs.values.maxByOrNull { it.plays }?.takeIf { it.plays > 0 } }
    val hours = thisMonth / 3_600_000
    val mins = (thisMonth / 60_000) % 60
    val tileShape = RoundedCornerShape(s.corner)
    val tile = Modifier.background(s.card, tileShape).border(1.dp, s.edge, tileShape).padding(12.dp)
    val mut = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onOpenStats),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Hero tile: listening time + bars
            Column(
                Modifier.weight(2f).fillMaxHeight().background(s.tactileAccent, tileShape).padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.bento_listened), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                Text("${hours}h ${mins}m", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                val max = (months.maxOrNull() ?: 0L).coerceAtLeast(1L)
                Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    months.forEach { ms ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight((0.12f + 0.88f * ms / max.toFloat()).coerceIn(0.12f, 1f))
                                .background(Color(0xFF3B82F6), RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.bento_songs), songs.toString(), tile.weight(1f).fillMaxWidth(), mut)
                StatTile(stringResource(R.string.bento_favourites), favourites.toString(), tile.weight(1f).fillMaxWidth(), mut)
            }
        }
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.bento_playlists), playlists.toString(), tile.weight(1f).fillMaxHeight(), mut)
            Column(tile.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.bento_top), style = MaterialTheme.typography.labelSmall, color = mut)
                Text(
                    top?.let { "${it.title} · ${it.artist}" } ?: "—",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, muted: Color) {
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
