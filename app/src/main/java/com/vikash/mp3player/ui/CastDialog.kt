package com.vikash.mp3player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vikash.mp3player.R
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext

/** Pick a Chromecast, Google/Nest speaker or Android TV to play on, or stop casting. */
@Composable
fun CastDialog(isPro: Boolean, onGetPro: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val castReady = remember {
        @Suppress("DEPRECATION")
        runCatching { CastContext.getSharedInstance(context) }.isSuccess
    }
    if (!castReady) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.cast_unavailable_title)) },
            text = { Text(stringResource(R.string.cast_unavailable_text)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
        )
        return
    }
    if (!isPro) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Cast, null) },
            title = { Text(stringResource(R.string.cast_pro_title)) },
            text = { Text(stringResource(R.string.cast_pro_text)) },
            confirmButton = { Button(onClick = { onDismiss(); onGetPro() }) { Text(stringResource(R.string.pro_get)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_not_now)) } },
        )
        return
    }

    val router = remember { MediaRouter.getInstance(context) }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }
    var routes by remember { mutableStateOf(emptyList<MediaRouter.RouteInfo>()) }
    var selected by remember { mutableStateOf<MediaRouter.RouteInfo?>(null) }

    DisposableEffect(router) {
        fun refresh() {
            routes = router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
            selected = router.selectedRoute.takeIf { !it.isDefault && it.matchesSelector(selector) }
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
        onDispose { router.removeCallback(callback) }
    }

    val current = selected
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current != null) stringResource(R.string.cast_playing_on, current.name) else stringResource(R.string.cast_pick_device)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (current == null) {
                    if (routes.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.cast_searching))
                        }
                    }
                    routes.forEach { route ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                router.selectRoute(route)
                                onDismiss()
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Cast, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(route.name)
                                route.description?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.cast_same_wifi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CastConnected, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.cast_connected_text, current.name))
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) {
                OutlinedButton(onClick = { router.unselect(MediaRouter.UNSELECT_REASON_STOPPED); onDismiss() }) { Text(stringResource(R.string.cast_stop)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
