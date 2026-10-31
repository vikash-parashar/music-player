package com.vikash.mp3player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.LastFm
import com.vikash.mp3player.ui.skin.SkinButton as Button
import com.vikash.mp3player.ui.skin.SkinCard as Card
import com.vikash.mp3player.ui.skin.SkinOutlinedButton as OutlinedButton
import com.vikash.mp3player.ui.skin.SkinSwitch as Switch
import kotlinx.coroutines.launch

/** Settings: sign in to Last.fm so played songs are scrobbled to the person's profile. */
@Composable
fun LastFmCard() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { LastFm.init(context); LastFm.flush(context) }
    val s by LastFm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lastfm_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (s.user != null) Switch(checked = s.enabled, onCheckedChange = { LastFm.setEnabled(context, it) })
            }
            val signedIn = s.user
            when {
                !s.configured -> Text(
                    stringResource(R.string.lastfm_soon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                signedIn != null -> {
                    Text(
                        stringResource(R.string.lastfm_signed_in, signedIn, s.sentToday),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s.pending > 0) {
                        Text(
                            stringResource(R.string.lastfm_pending, s.pending),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { LastFm.signOut(context) }) { Text(stringResource(R.string.lastfm_sign_out)) }
                }
                else -> {
                    Text(
                        stringResource(R.string.lastfm_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = user, onValueChange = { user = it }, singleLine = true,
                        label = { Text(stringResource(R.string.lastfm_user)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, singleLine = true,
                        label = { Text(stringResource(R.string.lastfm_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        enabled = !busy && user.isNotBlank() && password.isNotEmpty(),
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                error = LastFm.signIn(context, user, password)
                                busy = false
                                if (error == null) password = ""
                            }
                        },
                    ) { Text(stringResource(if (busy) R.string.lastfm_signing_in else R.string.lastfm_sign_in)) }
                }
            }
        }
    }
}
