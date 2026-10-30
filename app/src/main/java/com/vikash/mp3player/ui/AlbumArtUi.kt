package com.vikash.mp3player.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.AlertDialog
import com.vikash.mp3player.ui.skin.SkinButton as Button
import com.vikash.mp3player.ui.skin.SkinCard as Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.vikash.mp3player.ui.skin.SkinOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.AlbumArtFinder
import com.vikash.mp3player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs [action] once the user has agreed to send album and artist names to MusicBrainz and the
 * Cover Art Archive. The first time, a dialog asks; Continue is remembered, Cancel just closes it.
 */
private class ArtConsent(private val context: android.content.Context) {
    var pending by mutableStateOf<(() -> Unit)?>(null)

    fun ask(action: () -> Unit) {
        if (AlbumArtFinder.hasConsent(context)) action() else pending = action
    }
}

@Composable
private fun rememberArtConsent(): ArtConsent {
    val context = LocalContext.current
    return remember { ArtConsent(context.applicationContext) }
}

@Composable
private fun ArtConsentDialog(consent: ArtConsent) {
    val action = consent.pending ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { consent.pending = null },
        title = { Text(stringResource(R.string.album_art_consent_title)) },
        text = { Text(stringResource(R.string.album_art_consent_text)) },
        confirmButton = {
            Button(onClick = {
                AlbumArtFinder.setConsent(context)
                consent.pending = null
                action()
            }) { Text(stringResource(R.string.album_art_continue)) }
        },
        dismissButton = {
            TextButton(onClick = { consent.pending = null }) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** Header button on an album's screen: looks up the cover for that album. */
@Composable
fun FindAlbumArtButton(songs: List<Song>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val consent = rememberArtConsent()
    val local = remember(songs) { songs.filter { !it.isOnline && AlbumArtFinder.albumIdOf(it) != null } }
    if (local.isEmpty()) return

    if (busy) {
        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
    } else {
        IconButton(onClick = {
            consent.ask {
                busy = true
                scope.launch {
                    val message = findForAlbum(context, local)
                    busy = false
                    Toast.makeText(context, context.getString(message), Toast.LENGTH_LONG).show()
                }
            }
        }) { Icon(Icons.Default.ImageSearch, stringResource(R.string.album_art_find)) }
    }
    ArtConsentDialog(consent)
}

/** One album screen can hold several MediaStore albums of the same name; each gets a lookup. */
private suspend fun findForAlbum(context: android.content.Context, songs: List<Song>): Int {
    val byAlbum = songs.groupBy { AlbumArtFinder.albumIdOf(it)!! }
    val needed = withContext(Dispatchers.IO) {
        byAlbum.filter { (id, list) -> !AlbumArtFinder.savedFile(context, id).isFile && !AlbumArtFinder.hasRealArt(context, list.first()) }
    }
    if (needed.isEmpty()) return R.string.album_art_already
    var found = false
    var failed = false
    for ((id, list) in needed) {
        val artist = list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        when (AlbumArtFinder.find(context, id, list.first().album, artist)) {
            AlbumArtFinder.Outcome.Found -> found = true
            AlbumArtFinder.Outcome.Failed -> failed = true
            AlbumArtFinder.Outcome.NotFound -> Unit
        }
    }
    return when {
        found -> R.string.album_art_found
        failed -> R.string.album_art_offline
        else -> R.string.album_art_not_found
    }
}

/** Settings: walks every album with no art, one MusicBrainz request a second, with Cancel. */
@Composable
fun FindMissingArtCard(songs: List<Song>) {
    val context = LocalContext.current
    val batch by AlbumArtFinder.batch.collectAsState()
    val consent = rememberArtConsent()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.album_art_settings_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.album_art_settings_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            val b = batch
            if (b != null && b.running) {
                if (b.total > 0) {
                    LinearProgressIndicator(progress = { b.done.toFloat() / b.total }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (b.total == 0) stringResource(R.string.album_art_scanning)
                        else stringResource(R.string.album_art_progress, b.done, b.total, b.found),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = AlbumArtFinder::cancelBatch) { Text(stringResource(R.string.common_cancel)) }
                }
            } else {
                if (b != null) {
                    Text(
                        if (b.total == 0 && b.done == 0) stringResource(R.string.album_art_none_missing)
                        else stringResource(R.string.album_art_done, b.found, b.done),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = { consent.ask { AlbumArtFinder.startBatch(context, songs) } }, enabled = songs.isNotEmpty()) {
                    Text(stringResource(R.string.album_art_settings_button))
                }
            }
        }
    }
    ArtConsentDialog(consent)
}
