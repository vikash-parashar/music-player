package com.vikash.mp3player.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import android.widget.Toast
import com.vikash.mp3player.data.Song
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.mp3player.data.CrashLog
import com.vikash.mp3player.monetization.Ads
import com.vikash.mp3player.play.AppUpdates
import com.vikash.mp3player.monetization.BannerAd

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun Context.hasAudioPermission() =
    ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

/** Permissions asked on first launch: music access, notifications (13+), and storage writes for downloads (9 and older). */
private val launchPermissions = buildList {
    add(audioPermission)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}.toTypedArray()

@Composable
fun MusicPlayerApp(vm: MainViewModel, onRemoveAds: () -> Unit) {
    val context = LocalContext.current
    val library by vm.library.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val adsRemoved by vm.billing.adsRemoved.collectAsStateWithLifecycle()
    val price by vm.billing.price.collectAsStateWithLifecycle()
    val adsReady by Ads.ready.collectAsStateWithLifecycle()
    val app = context.applicationContext as com.vikash.mp3player.MusicApp
    val proUnlocked by app.proUnlocked.collectAsStateWithLifecycle()
    val trialUntil by com.vikash.mp3player.monetization.ProTrial.until.collectAsStateWithLifecycle()
    val tipPrices by vm.billing.tipPrices.collectAsStateWithLifecycle()
    val tipsReceived by vm.billing.tipsReceived.collectAsStateWithLifecycle()
    var showVisualizer by rememberSaveable { mutableStateOf(false) }
    val discAnim by app.prefs.discAnimation.collectAsStateWithLifecycle()
    // The song starts at once; the CD change animation only plays on top.
    var discSong by remember { mutableStateOf<Song?>(null) }
    val playWithDisc: (List<Song>, Int, String) -> Unit = { songs, i, src ->
        vm.player.play(songs, i, src)
        if (discAnim) discSong = songs.getOrNull(i)
    }
    val videos by vm.videos.collectAsStateWithLifecycle()
    val favorites by vm.prefs.favorites.collectAsStateWithLifecycle()
    val themeMode by vm.prefs.themeMode.collectAsStateWithLifecycle()
    val seekStyleName by vm.prefs.seekStyle.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val abLoop by vm.abLoop.collectAsStateWithLifecycle()
    // Pro styles fall back to Classic if Pro is turned off.
    val seekStyle = SeekStyle.entries.firstOrNull { it.name == seekStyleName }
        ?.takeIf { !it.pro || adsRemoved } ?: SeekStyle.Classic

    var hasPermission by remember { mutableStateOf(context.hasAudioPermission()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    // Ringtone maker: the song stays set while the panel slides away.
    var ringtoneSong by remember { mutableStateOf<Song?>(null) }
    var tagSong by remember { mutableStateOf<Song?>(null) }
    var deleteSongs by remember { mutableStateOf<List<Song>?>(null) }
    var moveSongs by remember { mutableStateOf<List<Song>?>(null) }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val songSort by vm.prefs.songSort.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val hiddenFolders by vm.prefs.hiddenFolders.collectAsStateWithLifecycle()
    val playlistActions = remember(vm) {
        PlaylistActions(
            create = vm::createPlaylist,
            addTo = vm::addToPlaylist,
            removeFrom = vm::removeFromPlaylist,
            rename = vm::renamePlaylist,
            delete = vm::deletePlaylist,
        )
    }
    var showRingtone by remember { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showTransfer by rememberSaveable { mutableStateOf(false) }
    var showCarMode by rememberSaveable { mutableStateOf(false) }
    var showDuplicates by rememberSaveable { mutableStateOf(false) }
    var showRecognize by remember { mutableStateOf(false) }
    var showPro by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val openPro: () -> Unit = { showPro = true }
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = context.hasAudioPermission()
        if (hasPermission) vm.loadLibrary()
    }
    // First run shows the welcome screens; its last page asks for music access.
    var welcomeDone by remember { mutableStateOf(vm.prefs.welcomeDone || context.hasAudioPermission()) }
    LaunchedEffect(Unit) {
        if (welcomeDone && !hasPermission) permissionLauncher.launch(launchPermissions)
    }
    var crashReport by remember { mutableStateOf(CrashLog.pending(context)) }
    val updateReady by AppUpdates.ready.collectAsStateWithLifecycle()
    val updateText = stringResource(R.string.update_ready)
    val updateAction = stringResource(R.string.update_restart)
    LaunchedEffect(tipsReceived) {
        if (tipsReceived > 0) snackbar.showSnackbar(context.getString(R.string.tip_thanks))
    }
    LaunchedEffect(updateReady) {
        if (updateReady) {
            val result = snackbar.showSnackbar(updateText, actionLabel = updateAction, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) AppUpdates.complete()
        }
    }
    // Re-scan when returning to the app: picks up finished downloads and newly copied files.
    LifecycleResumeEffect(Unit) {
        hasPermission = context.hasAudioPermission()
        if (hasPermission) vm.loadLibrary()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val currentId = player.current?.mediaId
    val discoverActions = remember(vm) {
        DiscoverActions(
            play = playWithDisc,
            playNext = { vm.player.playNext(it); vm.notify(context.getString(R.string.msg_playing_next, it.title)) },
            addToQueue = { vm.player.addToQueue(it); vm.notify(context.getString(R.string.msg_added_to_queue)) },
            download = { song, folder -> vm.download(song, folder) },
        )
    }

    CompositionLocalProvider(LocalIsPlaying provides player.isPlaying) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { BrandHeader(showAdFreePill = !adsRemoved, onAdFreeClick = openPro, onRecognize = { showRecognize = true }) },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(
                        player,
                        onOpen = { showPlayer = true },
                        onToggle = vm.player::togglePlay,
                        onNext = vm.player::next,
                        onPrevious = vm.player::previous,
                    )
                    if (!adsRemoved && adsReady) BannerAd()
                    CompactNavBar(
                        tabs = listOf(
                            NavTab(stringResource(R.string.nav_library), Icons.Default.LibraryMusic),
                            NavTab(stringResource(R.string.nav_online), Icons.Default.Public),
                            NavTab(stringResource(R.string.nav_videos), Icons.Default.VideoLibrary),
                            NavTab(stringResource(R.string.nav_settings), Icons.Default.Settings),
                        ),
                        selected = tab,
                        onSelect = { tab = it },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> LibraryScreen(
                        state = library,
                        hasPermission = hasPermission,
                        currentId = currentId,
                        onRequestPermission = { permissionLauncher.launch(launchPermissions) },
                        onPlay = playWithDisc,
                        onPlayNext = { vm.player.playNext(it); vm.notify(context.getString(R.string.msg_playing_next, it.title)) },
                        onAddToQueue = { vm.player.addToQueue(it); vm.notify(context.getString(R.string.msg_added_to_queue)) },
                        favorites = favorites,
                        onToggleFavorite = { vm.toggleFavorite(it.id, it.title) },
                        onSetRingtone = { ringtoneSong = it; showRingtone = true },
                        onEditTags = { tagSong = it },
                        onDelete = { if (it.isNotEmpty()) deleteSongs = it },
                        onMove = { if (it.isNotEmpty()) moveSongs = it },
                        stats = stats,
                        onOpenStats = { showStats = true },
                        playlists = playlists,
                        playlistActions = playlistActions,
                        onAddAllToQueue = vm::addAllToQueue,
                        onAddFavorites = vm::addFavorites,
                        sort = songSort,
                        onSort = vm.prefs::setSongSort,
                    )
                    1 -> DiscoverScreen(
                        currentId = currentId,
                        actions = discoverActions,
                        showMusic = online.configured,
                    ) {
                        OnlineScreen(
                            state = online,
                            currentId = currentId,
                            onQueryChange = { vm.searchOnline(it) },
                            onLoadPopular = { vm.searchOnline("", debounce = false) },
                            onPlay = playWithDisc,
                            onPlayNext = discoverActions.playNext,
                            onAddToQueue = discoverActions.addToQueue,
                            onDownload = { vm.download(it) },
                        )
                    }
                    2 -> if (adsRemoved) VideosScreen(videos, onLoad = vm::loadVideos)
                         else LockedVideos(price = price, onGetPro = openPro)
                    else -> SettingsScreen(
                        adsRemoved = adsRemoved, price = price, onRemoveAds = openPro,
                        onTestPro = vm.billing::setTestPro,
                        seekStyle = seekStyle, onSeekStyle = { vm.prefs.setSeekStyle(it.name) },
                        themeMode = themeMode, onThemeMode = vm.prefs::setThemeMode,
                        onBackup = vm::exportBackup, onRestore = vm::restoreBackup,
                        onOpenTransfer = { showTransfer = true },
                        onOpenCarMode = { showCarMode = true },
                        onOpenDuplicates = { showDuplicates = true },
                        folders = folders,
                        hiddenFolders = hiddenFolders,
                        onFolderHidden = vm.prefs::setFolderHidden,
                        onLibraryChanged = vm::loadLibrary,
                        onOpenHelp = { showHelp = true },
                        proUnlocked = proUnlocked,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && player.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(
                player, vm.player, favorites, onToggleFavorite = vm::toggleFavorite, seekStyle = seekStyle,
                onSetRingtone = {
                    // Only files on the phone can be cut; online streams aren't in the library.
                    val song = library.songs.firstOrNull { it.id == player.current?.mediaId }
                    if (song != null) { ringtoneSong = song; showRingtone = true }
                    else Toast.makeText(context, context.getString(R.string.msg_ringtone_needs_local), Toast.LENGTH_SHORT).show()
                },
                abLoop = abLoop,
                onCycleAb = vm::cycleAbRepeat,
                onCarMode = { showCarMode = true },
                isPro = adsRemoved,
                onGetPro = openPro,
                onClose = { showPlayer = false },
                onVisualizer = { if (proUnlocked) showVisualizer = true else openPro() },
            )
        }

        AnimatedVisibility(
            visible = showStats,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            StatsScreen(stats, onClear = vm::clearStats, onClose = { showStats = false })
        }

        AnimatedVisibility(
            visible = showTransfer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            // New songs show up in the Library as soon as the screen closes.
            WifiTransferScreen(onClose = { showTransfer = false; vm.loadLibrary() })
        }

        AnimatedVisibility(
            visible = showRingtone && ringtoneSong != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ringtoneSong?.let { song ->
                key(song.id) { RingtoneEditorScreen(song, onClose = { showRingtone = false }) }
            }
        }

        AnimatedVisibility(
            visible = showDuplicates,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            DuplicatesScreen(library.songs, onDelete = { if (it.isNotEmpty()) deleteSongs = it }, onClose = { showDuplicates = false })
        }

        AnimatedVisibility(visible = showCarMode, enter = fadeIn(), exit = fadeOut()) {
            CarModeScreen(player, vm.player, library.songs, onPlay = vm.player::play, onClose = { showCarMode = false })
        }

        AnimatedVisibility(
            visible = showPro,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ProScreen(
                isPro = adsRemoved,
                playPrice = price,
                onBuy = onRemoveAds,
                onRestore = {
                    vm.billing.restore()
                    Toast.makeText(context, context.getString(R.string.pro_restoring), Toast.LENGTH_SHORT).show()
                },
                onTestPro = { vm.billing.setTestPro(true) },
                onClose = { showPro = false },
                trialAvailable = Ads.rewardedConfigured,
                trialUntil = trialUntil,
                onTrial = {
                    val activity = context as? android.app.Activity
                    if (activity == null) return@ProScreen
                    Ads.showRewarded(
                        activity,
                        onReward = {
                            com.vikash.mp3player.monetization.ProTrial.grant(context)
                            Toast.makeText(context, context.getString(R.string.trial_started), Toast.LENGTH_LONG).show()
                        },
                        onUnavailable = {
                            Toast.makeText(context, context.getString(R.string.trial_no_ad), Toast.LENGTH_SHORT).show()
                        },
                    )
                },
                tipPrices = tipPrices,
                onTip = { id ->
                    val activity = context as? android.app.Activity
                    if (activity == null || !vm.billing.purchaseTip(activity, id)) {
                        Toast.makeText(context, context.getString(R.string.tip_unavailable), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = showHelp,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            HelpScreen(isPro = adsRemoved, onClose = { showHelp = false })
        }

        if (showRecognize) RecognizeDialog(isPro = adsRemoved, onGetPro = openPro, onDismiss = { showRecognize = false })

        deleteSongs?.let { songs ->
            key(songs.map { it.id }) {
                DeleteSongsDialog(
                    songs,
                    onDismiss = { deleteSongs = null },
                    onDeleted = { count ->
                        deleteSongs = null
                        vm.loadLibrary()
                        Toast.makeText(context, context.resources.getQuantityString(R.plurals.msg_songs_deleted, count, count), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        moveSongs?.let { songs ->
            key(songs.map { it.id }) {
                MoveSongsDialog(
                    songs,
                    folders = folders.map { it.first },
                    onDismiss = { moveSongs = null },
                    onMoved = { count ->
                        moveSongs = null
                        vm.loadLibrary()
                        Toast.makeText(context, context.resources.getQuantityString(R.plurals.msg_songs_moved, count, count), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        tagSong?.let { song ->
            key(song.id) {
                TagEditorDialog(
                    song,
                    onDismiss = { tagSong = null },
                    onSaved = {
                        tagSong = null
                        vm.loadLibrary()
                        Toast.makeText(context, context.getString(R.string.msg_song_info_saved), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        AnimatedVisibility(visible = discSong != null, enter = fadeIn(), exit = fadeOut()) {
            // Keep the last song while fading out.
            val shown = remember { mutableStateOf<Song?>(null) }
            discSong?.let { shown.value = it }
            shown.value?.let { song ->
                key(song.id) { DiscChangeOverlay(song, onDone = { discSong = null }) }
            }
        }

        AnimatedVisibility(visible = showVisualizer, enter = fadeIn(), exit = fadeOut()) {
            VisualizerScreen(
                title = player.current?.title.orEmpty(),
                artist = player.current?.artist.orEmpty(),
                isPlaying = player.isPlaying,
                onTogglePlay = vm.player::togglePlay,
                onClose = { showVisualizer = false },
            )
        }

        val report = crashReport
        if (welcomeDone && report != null) {
            AlertDialog(
                onDismissRequest = { CrashLog.clear(context); crashReport = null },
                title = { Text(stringResource(R.string.crash_title)) },
                text = { Text(stringResource(R.string.crash_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching { context.startActivity(CrashLog.emailIntent(report)) }
                        CrashLog.clear(context)
                        crashReport = null
                    }) { Text(stringResource(R.string.crash_send)) }
                },
                dismissButton = {
                    TextButton(onClick = { CrashLog.clear(context); crashReport = null }) {
                        Text(stringResource(R.string.crash_skip))
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = !welcomeDone,
            enter = fadeIn(),
            exit = fadeOut() + slideOutVertically { -it / 4 },
        ) {
            WelcomeScreen(onAllow = {
                vm.prefs.setWelcomeDone()
                welcomeDone = true
                permissionLauncher.launch(launchPermissions)
            })
        }
    }
    }
}
