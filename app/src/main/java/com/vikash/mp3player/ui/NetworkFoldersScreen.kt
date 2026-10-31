package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.mp3player.R
import com.vikash.mp3player.data.NetworkEntry
import com.vikash.mp3player.data.NetworkShare
import com.vikash.mp3player.data.NetworkShares
import com.vikash.mp3player.data.Song
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings > Network folders (Pro): play music straight from a PC, laptop or NAS on the same
 * Wi-Fi, without copying it to the phone. Works with Windows file sharing, macOS file sharing
 * (SMB), Samba and NAS boxes such as Synology or WD.
 */
@Composable
fun NetworkFoldersScreen(onPlay: (songs: List<Song>, index: Int, label: String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NetworkShares.load(context) }
    val shares by NetworkShares.shares.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<NetworkShare?>(null) }
    var adding by remember { mutableStateOf(false) }

    val current = open
    BackHandler(onBack = { if (current != null) open = null else onClose() })

    Surface(Modifier.fillMaxSize()) {
        if (current != null) {
            ShareBrowser(current, onPlay, onBack = { open = null })
            return@Surface
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                    Text(stringResource(R.string.net_title), style = MaterialTheme.typography.titleMedium)
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ampli.Gradient).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(stringResource(R.string.net_hero_title), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.net_hero_desc), color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                    items(shares, key = { it.id }) { share ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { open = share }
                                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Lan, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(share.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "\\\\${share.host}\\${share.share}" + share.path.takeIf { it.isNotBlank() }?.let { "\\" + it.replace('/', '\\') }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { NetworkShares.remove(context, share.id) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.net_remove))
                            }
                        }
                    }
                    if (shares.isEmpty()) item {
                        Text(stringResource(R.string.net_how), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    item { Spacer(Modifier.size(96.dp)) }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.net_add)) },
                modifier = Modifier.align(Alignment.BottomEnd).systemBarsPadding().padding(20.dp),
            )
        }
    }
    if (adding) AddShareDialog(onDismiss = { adding = false }, onSaved = { adding = false; open = it })
}

@Composable
private fun AddShareDialog(onDismiss: () -> Unit, onSaved: (NetworkShare) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val badAddress = stringResource(R.string.net_bad_address)

    AlertDialog(
        onDismissRequest = { if (!testing) onDismiss() },
        title = { Text(stringResource(R.string.net_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(address, { address = it; error = null }, singleLine = true,
                    label = { Text(stringResource(R.string.net_address)) },
                    placeholder = { Text("\\\\192.168.1.5\\Music") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.net_name)) })
                OutlinedTextField(user, { user = it; error = null }, singleLine = true, label = { Text(stringResource(R.string.net_user)) })
                OutlinedTextField(password, { password = it; error = null }, singleLine = true,
                    label = { Text(stringResource(R.string.net_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Text(stringResource(R.string.net_guest_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (testing) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(enabled = !testing && address.isNotBlank(), onClick = {
                val parsed = NetworkShares.parseAddress(address)
                if (parsed == null) { error = badAddress; return@Button }
                val (host, shareName, path) = parsed
                // DOMAIN\user is how Windows work PCs name accounts.
                val domain = user.substringBefore('\\', "")
                val share = NetworkShare(
                    name = name.ifBlank { shareName },
                    host = host, share = shareName, path = path,
                    user = user.substringAfter('\\'), password = password, domain = domain,
                )
                testing = true
                scope.launch {
                    val problem = withContext(Dispatchers.IO) { NetworkShares.test(share) }
                    testing = false
                    if (problem == null) {
                        NetworkShares.save(context, share)
                        onSaved(share)
                    } else error = context.getString(R.string.net_failed, problem)
                }
            }) { Text(stringResource(R.string.net_connect)) }
        },
        dismissButton = { TextButton(enabled = !testing, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ShareBrowser(share: NetworkShare, onPlay: (List<Song>, Int, String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val stack = remember(share.id) { mutableStateListOf(share.path) }
    val path = stack.last()
    var entries by remember { mutableStateOf<List<NetworkEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    BackHandler(onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() })

    LaunchedEffect(share.id, path, reload) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) { runCatching { NetworkShares.list(context, share, path) } }
        entries = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
        loading = false
    }
    val files = entries.filter { !it.isFolder }
    val songs = remember(files) { files.map { NetworkShares.toSong(share, it) } }
    val label = (share.name + "/" + path).trimEnd('/')

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
            }
            Column(Modifier.weight(1f)) {
                Text(path.substringAfterLast('/').ifBlank { share.name }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (songs.isNotEmpty()) TextButton(onClick = { onPlay(songs, 0, label) }) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.net_play_all, songs.size))
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            error != null -> EmptyMessage(context.getString(R.string.net_failed, error)) {
                Button(onClick = { reload++ }) { Text(stringResource(R.string.common_try_again)) }
            }
            !loading && entries.isEmpty() -> EmptyMessage(stringResource(R.string.net_empty))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.path }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (e.isFolder) stack.add(e.path)
                            else onPlay(songs, files.indexOf(e).coerceAtLeast(0), label)
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (e.isFolder) Icons.Default.Folder else Icons.Default.MusicNote, null,
                            tint = if (e.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!e.isFolder && e.sizeBytes > 0) Text(
                                "%.1f MB · %s".format(e.sizeBytes / 1_048_576.0, e.name.substringAfterLast('.').uppercase()),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
