package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.data.Duplicates
import com.vikash.mp3player.data.Song
import java.util.Locale

/**
 * Lists songs saved more than once. The biggest copy of each is kept (usually the best quality);
 * the others are ticked for deletion, and the user can change any tick.
 */
@Composable
fun DuplicatesScreen(songs: List<Song>, onDelete: (List<Song>) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val groups = remember(songs) { Duplicates.find(songs) }
    var ticked by remember(groups) { mutableStateOf(groups.flatMap { it.drop(1) }.map { it.id }.toSet()) }
    val toDelete = remember(ticked, groups) { groups.flatten().filter { it.id in ticked } }
    val freed = toDelete.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Duplicate songs", style = MaterialTheme.typography.titleLarge)
        }
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No duplicates found. Every song on this phone is saved only once.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                "${groups.size} songs are saved more than once. The biggest copy is kept; ticked copies will be deleted.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.first().id }) { group ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "${group.first().title} · ${group.first().artist}",
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            group.forEach { song ->
                                val on = song.id in ticked
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { ticked = if (on) ticked - song.id else ticked + song.id }
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(on, { ticked = if (on) ticked - song.id else ticked + song.id })
                                    Column(Modifier.weight(1f)) {
                                        Text(song.folder.ifBlank { "Internal storage" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${formatDuration(song.durationMs)} · ${formatFileSize(song.sizeBytes)}" + if (on) " · delete" else " · keep",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (on) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onDelete(toDelete) },
                enabled = toDelete.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(if (toDelete.isEmpty()) "Nothing ticked" else "Delete ${toDelete.size} copies · free ${formatFileSize(freed)}")
            }
        }
    }
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "size unknown"
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    else -> "${bytes / 1000} KB"
}
