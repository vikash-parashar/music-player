package com.vikash.mp3player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.data.UserPlaylist

/** Everything the Library needs to change the user's playlists. */
data class PlaylistActions(
    val create: (name: String, songs: List<Song>) -> Unit,
    val addTo: (playlistId: String, songs: List<Song>) -> Unit,
    val removeFrom: (playlistId: String, song: Song) -> Unit,
    val rename: (playlistId: String, name: String) -> Unit,
    val delete: (playlistId: String) -> Unit,
)

/** Pick a playlist for [songs], or make a new one with them. */
@Composable
fun AddToPlaylistDialog(
    songs: List<Song>,
    playlists: List<UserPlaylist>,
    actions: PlaylistActions,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    if (creating) {
        PlaylistNameDialog(
            title = "New playlist",
            initial = "",
            confirm = "Create",
            onConfirm = { actions.create(it, songs); onDismiss() },
            onDismiss = onDismiss,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (songs.size == 1) "Add to playlist" else "Add ${songs.size} songs to playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    DialogRow("New playlist", Icons.Default.Add) { creating = true }
                }
                items(playlists, key = { it.id }) { p ->
                    DialogRow("${p.name}  ·  ${p.songIds.size}", Icons.AutoMirrored.Filled.QueueMusic) {
                        actions.addTo(p.id, songs)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DialogRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Name a new playlist, or rename one. */
@Composable
fun PlaylistNameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Playlist name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
