package com.vikash.mp3player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import com.vikash.mp3player.ui.skin.SkinButton as Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.Bookmark

/** Bookmarks for the song that's playing: add one at the current spot, tap one to jump there. */
@Composable
fun BookmarksDialog(
    positionMs: Long,
    bookmarks: List<Bookmark>,
    onAdd: (positionMs: Long, note: String) -> Unit,
    onJump: (Long) -> Unit,
    onRemove: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    // The spot is fixed when the dialog opens, so typing a note doesn't move it.
    val at = remember { positionMs }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmarks_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.bookmarks_note_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onAdd(at, note); note = "" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) { Text(stringResource(R.string.bookmarks_add_at, formatDuration(at).ifEmpty { "0:00" })) }
                if (bookmarks.isEmpty()) {
                    Text(stringResource(R.string.bookmarks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(bookmarks.size) { i ->
                            val b = bookmarks[i]
                            Row(
                                Modifier.fillMaxWidth().clickable { onJump(b.positionMs); onDismiss() }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(formatDuration(b.positionMs).ifEmpty { "0:00" }, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 12.dp))
                                Text(b.note.ifBlank { stringResource(R.string.bookmarks_untitled) }, Modifier.weight(1f), maxLines = 2)
                                IconButton(onClick = { onRemove(b) }) { Icon(Icons.Default.Delete, stringResource(R.string.common_delete)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
    )
}
