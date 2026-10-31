package com.vikash.mp3player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.AutoEq

/** Search list of headphone correction curves; picking one sets the 10-band equaliser. */
@Composable
fun AutoEqDialog(current: String?, onPick: (AutoEq.Profile) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all = remember { AutoEq.profiles(context) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query, all) { AutoEq.search(all, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.autoeq_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.autoeq_desc, all.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.autoeq_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (shown.isEmpty()) {
                    Text(stringResource(R.string.autoeq_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(shown, key = { it.name + it.source }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                p.name,
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (p.name == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(p.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
                Text(stringResource(R.string.autoeq_credit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.autoeq_close)) } },
    )
}
