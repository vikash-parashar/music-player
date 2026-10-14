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
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val server = remember { WifiTransferServer(context.applicationContext) }
    val received by server.received.collectAsState()
    var address by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        starting = true
        address = withContext(Dispatchers.IO) { runCatching { server.start() }.getOrNull() }
        starting = false
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
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Send songs from your computer", style = MaterialTheme.typography.titleMedium)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ampli.Gradient).padding(20.dp),
                ) {
                    when {
                        starting -> Text("Starting…", color = Color.White)
                        address == null -> {
                            Text("Connect to Wi-Fi", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Your phone and computer need to be on the same Wi-Fi (or the computer on your phone's hotspot).",
                                color = Color.White.copy(alpha = 0.9f),
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { attempt++ }) { Text("Try again") }
                        }
                        else -> {
                            Text("On your computer, open this address:", color = Color.White.copy(alpha = 0.9f))
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
                        Text("How it works", style = MaterialTheme.typography.titleMedium)
                        Text("1. Put the computer on the same Wi-Fi as this phone.")
                        Text("2. Type the address above into any browser (Chrome, Safari, Edge).")
                        Text("3. Drag your music files onto the page.")
                        Text("4. Keep this screen open until every file says Sent.")
                        Text(
                            "Files go straight to Music/Ampli on this phone. Nothing is uploaded to the internet, and the server stops when you leave this screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (received.isNotEmpty()) {
                    Text("Received (${received.size})", style = MaterialTheme.typography.titleMedium)
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
