package com.vikash.mp3player.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.transfer.WifiTransferServer
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Send songs from your computer": shows the address to open on a laptop on the same Wi-Fi. */
@Composable
fun WifiTransferScreen(onClose: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { com.vikash.mp3player.data.OwnerHooks.event("feature_wifi_transfer") }
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val server = remember { WifiTransferServer(context.applicationContext) }
    val received by server.received.collectAsState()
    var address by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var stoppedIdle by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        starting = true
        stoppedIdle = false
        address = withContext(Dispatchers.IO) { runCatching { server.start() }.getOrNull() }
        starting = false
        // Close the door after 10 minutes with nothing sent, so the phone isn't left listening on the Wi-Fi.
        while (address != null) {
            kotlinx.coroutines.delay(30_000)
            if (server.idleMillis() > 10 * 60_000L) {
                server.stop()
                address = null
                stoppedIdle = true
            }
        }
    }
    DisposableEffect(Unit) {
        // Keep the screen on so the phone doesn't sleep in the middle of a big transfer.
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            server.stop()
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                Text(stringResource(R.string.transfer_title), style = MaterialTheme.typography.titleMedium)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ampli.Gradient).padding(20.dp),
                ) {
                    when {
                        starting -> Text(stringResource(R.string.transfer_starting), color = Color.White)
                        address == null -> {
                            Text(
                                stringResource(if (stoppedIdle) R.string.transfer_idle_title else R.string.transfer_connect_wifi),
                                color = Color.White, style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                stringResource(if (stoppedIdle) R.string.transfer_idle_desc else R.string.transfer_same_wifi),
                                color = Color.White.copy(alpha = 0.9f),
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { attempt++ }) { Text(stringResource(R.string.common_try_again)) }
                        }
                        else -> {
                            Text(stringResource(R.string.transfer_open_address), color = Color.White.copy(alpha = 0.9f))
                            Spacer(Modifier.height(8.dp))
                            SelectionContainer {
                                Text(
                                    address.orEmpty().removePrefix("http://"),
                                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.transfer_how_it_works), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.transfer_step1))
                        Text(stringResource(R.string.transfer_step2))
                        Text(stringResource(R.string.transfer_step3))
                        Text(stringResource(R.string.transfer_step4))
                        Text(
                            stringResource(R.string.transfer_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (received.isNotEmpty()) {
                    Text(stringResource(R.string.transfer_received, received.size), style = MaterialTheme.typography.titleMedium)
                    received.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Text(name, Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
