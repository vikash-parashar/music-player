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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    var hasPermission by remember { mutableStateOf(context.hasAudioPermission()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
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
            topBar = { BrandHeader(showAdFreePill = !adsRemoved, onAdFreeClick = { tab = 2 }) },
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
                    else -> SettingsScreen(adsRemoved = adsRemoved, price = price, onRemoveAds = onRemoveAds)
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && player.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(player, vm.player, onClose = { showPlayer = false })
        }
    }
}
