package com.vikash.mp3player.ui

import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.RingVolume
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SaveAlt
import com.vikash.mp3player.data.M3u
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import com.vikash.mp3player.data.INTERNAL_STORAGE
import com.vikash.mp3player.data.UNKNOWN_ALBUM
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.vikash.mp3player.data.SongSort
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.vikash.mp3player.data.UserPlaylist
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.vikash.mp3player.data.StatsData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.vikash.mp3player.ui.theme.Ampli
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.data.Song

private enum class LibraryTab(@StringRes val labelRes: Int, val key: (Song) -> String) {
    Songs(R.string.tab_songs, { "" }),
    Albums(R.string.tab_albums, { it.album.ifBlank { UNKNOWN_ALBUM } }),
    Artists(R.string.library_tab_artists, { it.artist }),
    Folders(R.string.tab_folders, { it.folder.ifBlank { INTERNAL_STORAGE } }),
    Playlists(R.string.library_tab_playlists, { "" }),
}

/** openGroup prefix for one of the user's own playlists. */
private const val USER_PLAYLIST = "playlist:"

/** openGroup prefix for a smart list, followed by the enum name (not the translated title). */
private const val SMART_LIST = "smart:"

/** Queue name for the whole Songs list. Matches PlayerConnection's default queue; shown translated. */
const val ALL_SONGS_QUEUE = "All songs"

/** Built-in lists that fill themselves from favorites, play history and date added. */
private enum class SmartList(@StringRes val titleRes: Int, val icon: ImageVector, @StringRes val emptyRes: Int) {
    Favorites(R.string.library_my_favorites, Icons.Default.Favorite, R.string.library_favorites_empty),
    MostPlayed(R.string.library_most_played, Icons.Default.Whatshot, R.string.library_most_played_empty),
    RecentlyPlayed(R.string.library_recently_played, Icons.Default.History, R.string.library_recently_played_empty),
    RecentlyAdded(R.string.library_recently_added, Icons.Default.NewReleases, R.string.library_recently_added_empty),
    NeverPlayed(R.string.library_never_played, Icons.Default.Explore, R.string.library_never_played_empty),
}

private const val SMART_LIMIT = 100

private fun SmartList.songs(all: List<Song>, favorites: List<String>, stats: StatsData): List<Song> {
    fun plays(s: Song) = stats.songs[s.id]?.plays ?: 0
    fun last(s: Song) = stats.songs[s.id]?.lastPlayedMs ?: 0L
    return when (this) {
        SmartList.Favorites -> {
            val byId = all.associateBy { it.id }
            favorites.mapNotNull { byId[it] }
        }
        SmartList.MostPlayed -> all.filter { plays(it) > 0 }
            .sortedWith(compareByDescending<Song> { plays(it) }.thenByDescending { last(it) }).take(SMART_LIMIT)
        SmartList.RecentlyPlayed -> all.filter { last(it) > 0 }.sortedByDescending { last(it) }.take(SMART_LIMIT)
        SmartList.RecentlyAdded -> all.sortedByDescending { it.dateAddedSec }.take(SMART_LIMIT)
        SmartList.NeverPlayed -> all.filter { plays(it) == 0 }
    }
}


@Composable
fun LibraryScreen(
    state: LibraryState,
    hasPermission: Boolean,
    currentId: String?,
    onRequestPermission: () -> Unit,
    onPlay: (songs: List<Song>, startIndex: Int, source: String) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    favorites: List<String>,
    onToggleFavorite: (Song) -> Unit,
    onSetRingtone: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onDelete: (List<Song>) -> Unit,
    onMove: (List<Song>) -> Unit,
    stats: StatsData,
    onOpenStats: () -> Unit,
    playlists: List<UserPlaylist>,
    playlistActions: PlaylistActions,
    onAddAllToQueue: (List<Song>) -> Unit,
    onAddFavorites: (List<Song>) -> Unit,
    sort: SongSort,
    onSort: (SongSort) -> Unit,
) {
    if (!hasPermission) {
        EmptyMessage(stringResource(R.string.library_permission_needed)) {
            Button(onClick = onRequestPermission) { Text(stringResource(R.string.library_allow_access)) }
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val current = LibraryTab.entries[tab]

    BackHandler(enabled = openGroup != null) { openGroup = null }

    var addingSongs by remember { mutableStateOf<List<Song>?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<UserPlaylist?>(null) }
    var deleting by remember { mutableStateOf<UserPlaylist?>(null) }
    val onAddToPlaylist: (List<Song>) -> Unit = { addingSongs = it }

    // M3U playlist files: export one of the user's playlists, or import one from another player.
    val m3uContext = LocalContext.current
    var exportingSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) {
            val ok = M3u.export(m3uContext, uri, exportingSongs)
            android.widget.Toast.makeText(
                m3uContext,
                m3uContext.getString(if (ok) R.string.m3u_exported else R.string.m3u_failed),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = M3u.import(m3uContext, uri, state.songs)
            if (result == null || result.second.isEmpty()) {
                android.widget.Toast.makeText(m3uContext, m3uContext.getString(R.string.m3u_nothing_found), android.widget.Toast.LENGTH_LONG).show()
            } else {
                playlistActions.create(result.first, result.second)
            }
        }
    }

    val filtered = remember(state.songs, filter, sort) {
        val matching = if (filter.isBlank()) state.songs else state.songs.filter {
            it.title.contains(filter, true) || it.artist.contains(filter, true) ||
                it.album.contains(filter, true) || it.folder.contains(filter, true)
        }
        sort.apply(matching)
    }
    val sortMenu: @Composable () -> Unit = {
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.library_sort_songs)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SongSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        leadingIcon = { if (option == sort) Icon(Icons.Default.Check, null) },
                        onClick = { onSort(option); open = false },
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Default.Clear, stringResource(R.string.common_clear)) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            LibraryTab.entries.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i; openGroup = null }, text = { Text(stringResource(t.labelRes)) })
            }
        }

        when {
            state.loading && state.songs.isEmpty() ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.padding(32.dp))
                }
            state.songs.isEmpty() -> EmptyMessage(stringResource(R.string.library_no_music))
            current == LibraryTab.Playlists && openGroup == null -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        PlaylistRow(
                            stringResource(R.string.library_stats_title), stringResource(R.string.library_stats_subtitle),
                            Icons.Default.Insights, onClick = onOpenStats,
                        )
                    }
                    items(SmartList.entries.toList(), key = { it.name }) { list ->
                        val count = remember(state.songs, favorites, stats, list) { list.songs(state.songs, favorites, stats).size }
                        PlaylistRow(
                            stringResource(list.titleRes),
                            when {
                                count > 0 -> pluralStringResource(R.plurals.library_song_count, count, count)
                                list == SmartList.Favorites -> stringResource(R.string.library_favorites_hint)
                                else -> stringResource(R.string.library_empty_for_now)
                            },
                            list.icon,
                            onClick = { openGroup = SMART_LIST + list.name },
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.library_your_playlists), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    item {
                        PlaylistRow(stringResource(R.string.playlist_new), stringResource(R.string.library_new_playlist_subtitle), Icons.Default.Add, onClick = { creatingPlaylist = true })
                    }
                    item {
                        PlaylistRow(
                            stringResource(R.string.m3u_import), stringResource(R.string.m3u_import_subtitle), Icons.Default.FileOpen,
                            onClick = { importLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "audio/*", "text/*", "application/octet-stream")) },
                        )
                    }
                    items(playlists, key = { it.id }) { p ->
                        PlaylistRow(
                            p.name,
                            if (p.songIds.isEmpty()) stringResource(R.string.library_playlist_empty_short)
                            else pluralStringResource(R.plurals.library_song_count, p.songIds.size, p.songIds.size),
                            Icons.AutoMirrored.Filled.QueueMusic,
                            onClick = { openGroup = USER_PLAYLIST + p.id },
                        )
                    }
                }
            }
            current == LibraryTab.Playlists && openGroup?.startsWith(USER_PLAYLIST) == true -> {
                val playlist = playlists.firstOrNull { it.id == openGroup?.removePrefix(USER_PLAYLIST) }
                if (playlist == null) {
                    LaunchedEffect(Unit) { openGroup = null } // it was just deleted
                } else {
                    val songs = remember(filtered, playlist) {
                        val byId = filtered.associateBy { it.id }
                        playlist.songIds.mapNotNull { byId[it] }
                    }
                    val headerActions: @Composable () -> Unit = {
                        if (songs.isNotEmpty()) IconButton(onClick = {
                            exportingSongs = songs
                            exportLauncher.launch("${playlist.name}.m3u")
                        }) { Icon(Icons.Default.SaveAlt, stringResource(R.string.m3u_export)) }
                        IconButton(onClick = { renaming = playlist }) { Icon(Icons.Default.Edit, stringResource(R.string.playlist_rename)) }
                        IconButton(onClick = { deleting = playlist }) { Icon(Icons.Default.Delete, stringResource(R.string.playlist_delete)) }
                    }
                    if (songs.isEmpty()) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { openGroup = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                headerActions()
                            }
                            EmptyMessage(stringResource(R.string.library_playlist_empty))
                        }
                    } else {
                        SongList(
                            title = playlist.name,
                            songs = songs,
                            currentId = currentId,
                            onBack = { openGroup = null },
                            onPlay = onPlay,
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            favorites = favorites,
                            onToggleFavorite = onToggleFavorite,
                            onSetRingtone = onSetRingtone,
                            onEditTags = onEditTags,
                            onDelete = onDelete,
                            onMove = onMove,
                            onAddToPlaylist = onAddToPlaylist,
                            onAddAllToQueue = onAddAllToQueue,
                            onAddFavorites = onAddFavorites,
                            onRemove = { playlistActions.removeFrom(playlist.id, it) },
                            headerActions = headerActions,
                        )
                    }
                }
            }
            current == LibraryTab.Playlists && openGroup != null -> {
                val list = SmartList.entries.firstOrNull { SMART_LIST + it.name == openGroup } ?: SmartList.Favorites
                val listTitle = stringResource(list.titleRes)
                val songs = remember(filtered, favorites, stats, list) { list.songs(filtered, favorites, stats) }
                if (songs.isEmpty()) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { openGroup = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                            Text(listTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        EmptyMessage(stringResource(list.emptyRes))
                    }
                } else {
                    SongList(
                        title = listTitle,
                        songs = songs,
                        currentId = currentId,
                        onBack = { openGroup = null },
                        onPlay = onPlay,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        favorites = favorites,
                        onToggleFavorite = onToggleFavorite,
                        onSetRingtone = onSetRingtone,
                        onEditTags = onEditTags,
                        onDelete = onDelete,
                        onMove = onMove,
                        onAddToPlaylist = onAddToPlaylist,
                        onAddAllToQueue = onAddAllToQueue,
                        onAddFavorites = onAddFavorites,
                    )
                }
            }
            current == LibraryTab.Songs || openGroup != null -> {
                val songs = if (openGroup == null) filtered else filtered.filter { current.key(it) == openGroup }
                SongList(
                    headerActions = sortMenu,
                    title = openGroup?.let { groupLabel(it) },
                    songs = songs,
                    currentId = currentId,
                    onBack = { openGroup = null },
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    favorites = favorites,
                    onToggleFavorite = onToggleFavorite,
                    onSetRingtone = onSetRingtone,
                    onEditTags = onEditTags,
                    onDelete = onDelete,
                    onMove = onMove,
                    onAddToPlaylist = onAddToPlaylist,
                    onAddAllToQueue = onAddAllToQueue,
                    onAddFavorites = onAddFavorites,
                )
            }
            else -> {
                val groups = remember(filtered, current) {
                    filtered.groupBy(current.key).toSortedMap(String.CASE_INSENSITIVE_ORDER)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(groups.entries.toList(), key = { it.key }) { (name, songs) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openGroup = name }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(songs.first().artworkUri, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(groupLabel(name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    pluralStringResource(R.plurals.library_song_count, songs.size, songs.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    addingSongs?.let { list ->
        AddToPlaylistDialog(list, playlists, playlistActions, onDismiss = { addingSongs = null })
    }
    if (creatingPlaylist) {
        PlaylistNameDialog(stringResource(R.string.playlist_new), "", stringResource(R.string.common_create),
            onConfirm = { playlistActions.create(it, emptyList()); creatingPlaylist = false },
            onDismiss = { creatingPlaylist = false })
    }
    renaming?.let { p ->
        PlaylistNameDialog(stringResource(R.string.playlist_rename), p.name, stringResource(R.string.common_save),
            onConfirm = { playlistActions.rename(p.id, it); renaming = null },
            onDismiss = { renaming = null })
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.playlist_delete_title, p.name)) },
            text = { Text(stringResource(R.string.playlist_delete_text)) },
            confirmButton = {
                Button(onClick = { playlistActions.delete(p.id); deleting = null; openGroup = null }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun PlaylistRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Ampli.Gradient),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongList(
    title: String?,
    songs: List<Song>,
    currentId: String?,
    onBack: () -> Unit,
    onPlay: (songs: List<Song>, startIndex: Int, source: String) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    favorites: List<String>,
    onToggleFavorite: (Song) -> Unit,
    onSetRingtone: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onDelete: (List<Song>) -> Unit,
    onMove: (List<Song>) -> Unit,
    onAddToPlaylist: (List<Song>) -> Unit,
    onAddAllToQueue: (List<Song>) -> Unit,
    onAddFavorites: (List<Song>) -> Unit,
    /** Set inside one of the user's playlists: adds "Remove from playlist". */
    onRemove: ((Song) -> Unit)? = null,
    /** Extra buttons in the header, e.g. rename/delete for a playlist. */
    headerActions: (@Composable () -> Unit)? = null,
) {
    // Multi-select: long-press a song to start, tap to add or remove more.
    var selected by remember(title) { mutableStateOf(emptySet<String>()) }
    val selecting = selected.isNotEmpty()
    val picked = remember(selected, songs) { songs.filter { it.id in selected } }
    BackHandler(enabled = selecting) { selected = emptySet() }
    fun toggle(song: Song) { selected = if (song.id in selected) selected - song.id else selected + song.id }
    val source = title ?: ALL_SONGS_QUEUE
    val playNextLabel = stringResource(R.string.song_play_next)
    val addToQueueLabel = stringResource(R.string.song_add_to_queue)
    val addToPlaylistLabel = stringResource(R.string.song_add_to_playlist)
    val removeFromPlaylistLabel = stringResource(R.string.song_remove_from_playlist)
    val addFavoriteLabel = stringResource(R.string.favorites_add)
    val removeFavoriteLabel = stringResource(R.string.favorites_remove)
    val setRingtoneLabel = stringResource(R.string.song_set_ringtone)
    val editInfoLabel = stringResource(R.string.song_edit_info)
    val deleteLabel = stringResource(R.string.song_delete_from_phone)
    val moveLabel = stringResource(R.string.song_move)
    val shareLabel = stringResource(R.string.song_share)
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize()) {
        stickyHeader {
            Surface(tonalElevation = if (selecting) 3.dp else 0.dp) {
                if (selecting) {
                    // Two rows: the count gets the full width (on narrow phones a single row squeezed
                    // "1 selected" into a one-letter-wide column), and the actions scroll sideways.
                    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, stringResource(R.string.library_cancel_selection)) }
                        Text(stringResource(R.string.library_selected_count, selected.size), style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selected = if (selected.size == songs.size) emptySet() else songs.map { it.id }.toSet() }) {
                            Icon(Icons.Default.SelectAll, stringResource(R.string.library_select_all))
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onPlay(picked, 0, source); selected = emptySet() }) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.library_play_selected))
                        }
                        IconButton(onClick = { onAddAllToQueue(picked); selected = emptySet() }) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.library_queue_selected))
                        }
                        IconButton(onClick = { onAddToPlaylist(picked); selected = emptySet() }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.library_playlist_selected))
                        }
                        IconButton(onClick = { onAddFavorites(picked); selected = emptySet() }) {
                            Icon(Icons.Default.Favorite, stringResource(R.string.library_favorite_selected))
                        }
                        IconButton(onClick = { onMove(picked.filter { !it.isOnline }); selected = emptySet() }) {
                            Icon(Icons.Default.DriveFileMove, stringResource(R.string.library_move_selected))
                        }
                        IconButton(onClick = { shareSongs(context, picked); selected = emptySet() }) {
                            Icon(Icons.Default.Share, stringResource(R.string.library_share_selected))
                        }
                        IconButton(onClick = { onDelete(picked.filter { !it.isOnline }); selected = emptySet() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.library_delete_selected))
                        }
                    }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (title != null) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        } else {
                            Text(pluralStringResource(R.plurals.library_songs_long_press, songs.size, songs.size), modifier = Modifier.weight(1f).padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        headerActions?.invoke()
                        IconButton(onClick = { if (songs.isNotEmpty()) onPlay(songs.shuffled(), 0, source) }) {
                            Icon(Icons.Default.Shuffle, stringResource(R.string.library_shuffle_all))
                        }
                    }
                }
            }
        }
        items(songs.size, key = { songs[it].id }) { i ->
            val song = songs[i]
            val fav = song.id in favorites
            SongRow(
                song = song,
                isCurrent = song.id == currentId,
                onClick = { if (selecting) toggle(song) else onPlay(songs, i, source) },
                actions = buildList {
                    add(SongAction(playNextLabel, Icons.AutoMirrored.Filled.PlaylistPlay) { onPlayNext(song) })
                    add(SongAction(addToQueueLabel, Icons.AutoMirrored.Filled.QueueMusic) { onAddToQueue(song) })
                    add(SongAction(addToPlaylistLabel, Icons.AutoMirrored.Filled.PlaylistAdd) { onAddToPlaylist(listOf(song)) })
                    if (onRemove != null) add(SongAction(removeFromPlaylistLabel, Icons.Default.RemoveCircleOutline) { onRemove(song) })
                    add(SongAction(if (fav) removeFavoriteLabel else addFavoriteLabel, if (fav) Icons.Default.HeartBroken else Icons.Default.Favorite) { onToggleFavorite(song) })
                    add(SongAction(setRingtoneLabel, Icons.Default.RingVolume) { onSetRingtone(song) })
                    add(SongAction(editInfoLabel, Icons.Default.Edit) { onEditTags(song) })
                    if (!song.isOnline) add(SongAction(shareLabel, Icons.Default.Share) { shareSongs(context, listOf(song)) })
                    if (!song.isOnline) add(SongAction(moveLabel, Icons.Default.DriveFileMove) { onMove(listOf(song)) })
                    if (!song.isOnline) add(SongAction(deleteLabel, Icons.Default.Delete, danger = true) { onDelete(listOf(song)) })
                },
                isFavorite = fav,
                onToggleFavorite = { onToggleFavorite(song) },
                onSwipePlayNext = { onPlayNext(song) },
                onLongClick = { toggle(song) },
                selected = if (selecting) song.id in selected else null,
            )
        }
    }
}
