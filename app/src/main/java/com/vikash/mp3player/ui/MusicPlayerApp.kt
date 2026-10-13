package com.vikash.mp3player.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import android.widget.Toast
import com.vikash.mp3player.data.Song
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.mp3player.monetization.Ads
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
    val videos by vm.videos.collectAsStateWithLifecycle()
    val favorites by vm.prefs.favorites.collectAsStateWithLifecycle()
    val themeMode by vm.prefs.themeMode.collectAsStateWithLifecycle()
    val seekStyleName by vm.prefs.seekStyle.collectAsStateWithLifecycle()
    // Pro styles fall back to Classic if Pro is turned off.
    val seekStyle = SeekStyle.entries.firstOrNull { it.name == seekStyleName }
        ?.takeIf { !it.pro || adsRemoved } ?: SeekStyle.Classic

    var hasPermission by remember { mutableStateOf(context.hasAudioPermission()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    // Ringtone maker: the song stays set while the panel slides away.
    var ringtoneSong by remember { mutableStateOf<Song?>(null) }
    var showRingtone by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = context.hasAudioPermission()
        if (hasPermission) vm.loadLibrary()
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(launchPermissions)
    }
    // Re-scan when returning to the app: picks up finished downloads and newly copied files.
    LifecycleResumeEffect(Unit) {
        hasPermission = context.hasAudioPermission()
        if (hasPermission) vm.loadLibrary()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val currentId = player.current?.mediaId

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { BrandHeader(showAdFreePill = !adsRemoved, onAdFreeClick = { tab = 3 }) },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(player, onOpen = { showPlayer = true }, onToggle = vm.player::togglePlay, onNext = vm.player::next)
                    if (!adsRemoved && adsReady) BannerAd()
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Library") },
                        )
                        NavigationBarItem(
                            selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.Public, null) }, label = { Text("Online") },
                        )
                        NavigationBarItem(
                            selected = tab == 2, onClick = { tab = 2 },
                            icon = { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("Videos") },
                        )
                        NavigationBarItem(
                            selected = tab == 3, onClick = { tab = 3 },
                            icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") },
                        )
                    }
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
                        onPlay = vm.player::play,
                        onPlayNext = { vm.player.playNext(it); vm.notify("Playing \"${it.title}\" next") },
                        onAddToQueue = { vm.player.addToQueue(it); vm.notify("Added to queue") },
                        favorites = favorites,
                        onToggleFavorite = { vm.toggleFavorite(it.id, it.title) },
                        onSetRingtone = { ringtoneSong = it; showRingtone = true },
                    )
                    1 -> OnlineScreen(
                        state = online,
                        currentId = currentId,
                        onQueryChange = { vm.searchOnline(it) },
                        onLoadPopular = { vm.searchOnline("", debounce = false) },
                        onPlay = vm.player::play,
                        onPlayNext = { vm.player.playNext(it); vm.notify("Playing \"${it.title}\" next") },
                        onAddToQueue = { vm.player.addToQueue(it); vm.notify("Added to queue") },
                        onDownload = vm::download,
                    )
                    2 -> if (adsRemoved) VideosScreen(videos, onLoad = vm::loadVideos)
                         else LockedVideos(price = price, onGetPro = onRemoveAds)
                    else -> SettingsScreen(
                        adsRemoved = adsRemoved, price = price, onRemoveAds = onRemoveAds,
                        onTestPro = vm.billing::setTestPro,
                        seekStyle = seekStyle, onSeekStyle = { vm.prefs.setSeekStyle(it.name) },
                        themeMode = themeMode, onThemeMode = vm.prefs::setThemeMode,
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
                    else Toast.makeText(context, "Ringtones can be made from songs saved on this phone.", Toast.LENGTH_SHORT).show()
                },
                onClose = { showPlayer = false },
            )
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
    }
}
