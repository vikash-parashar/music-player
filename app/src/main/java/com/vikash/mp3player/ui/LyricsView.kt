package com.vikash.mp3player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.data.Lyrics
import com.vikash.mp3player.data.LyricsRepository
import kotlinx.coroutines.launch

/**
 * Lyrics for the current song, shown in place of the artwork. Synced lyrics (LRC) highlight
 * the line being sung and scroll along; tap a line to jump there.
 */
@Composable
fun LyricsView(songId: String, positionMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lyrics by remember(songId) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(songId) { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var pasting by remember { mutableStateOf(false) }
    var message by remember(songId) { mutableStateOf<String?>(null) }

    LaunchedEffect(songId, reload) {
        loading = true
        lyrics = LyricsRepository.load(context, songId)
        loading = false
    }

    fun saveText(text: String?) {
        scope.launch {
            val ok = text != null && LyricsRepository.save(context, songId, text)
            message = if (ok) null else context.getString(R.string.lyrics_file_empty)
            if (ok) reload++
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { saveText(LyricsRepository.readText(context, uri)) }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) { LyricsRepository.setLyricsFolder(context, tree); reload++ }
    }
    val folderMissing = remember(reload) { LyricsRepository.folderNeeded && LyricsRepository.lyricsFolder(context) == null }

    Box(modifier, contentAlignment = Alignment.Center) {
        val current = lyrics
        when {
            loading -> CircularProgressIndicator()
            current == null -> Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.lyrics_none), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.lyrics_none_desc),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.lyrics_choose_file)) }
                    OutlinedButton(onClick = { pasting = true }) { Text(stringResource(R.string.lyrics_paste)) }
                }
                if (folderMissing) {
                    // Android 13+ can't read .lrc files beside songs without this one-time pick.
                    Text(
                        stringResource(R.string.lyrics_folder_desc),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { pickFolder.launch(null) }) { Text(stringResource(R.string.lyrics_folder_pick)) }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            else -> {
                val onRemove: (() -> Unit)? = if (current.fromUser) {
                    fun() { scope.launch { LyricsRepository.remove(context, songId); reload++ } }
                } else null
                SyncedLines(current, positionMs, onSeek, onRemove)
            }
        }
    }

    if (pasting) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { Text(stringResource(R.string.lyrics_paste_title)) },
            text = {
                OutlinedTextField(
                    text, { text = it },
                    placeholder = { Text(stringResource(R.string.lyrics_paste_hint)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                )
            },
            confirmButton = {
                Button(onClick = { saveText(text); pasting = false }, enabled = text.isNotBlank()) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun SyncedLines(lyrics: Lyrics, positionMs: Long, onSeek: (Long) -> Unit, onRemove: (() -> Unit)?) {
    val lines = lyrics.lines
    val active = if (!lyrics.synced) -1 else lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= positionMs }
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem((active - 2).coerceAtLeast(0))
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lines) { i, line ->
            val isActive = i == active
            Text(
                line.text.ifEmpty { "♪" },
                textAlign = TextAlign.Center,
                fontSize = if (isActive) 22.sp else 18.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    lyrics.synced && i < active -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = line.timeMs != null) { line.timeMs?.let(onSeek) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        if (onRemove != null) item {
            TextButton(onClick = onRemove, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.lyrics_remove)) }
        }
    }
}
