package com.vikash.mp3player.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SupportAgent
import com.vikash.mp3player.ui.skin.SkinButton as Button
import com.vikash.mp3player.ui.skin.SkinCard as Card
import com.vikash.mp3player.ui.skin.SkinCheckbox as Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.vikash.mp3player.ui.skin.SkinOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.FeatureIdea
import com.vikash.mp3player.data.Feedback
import com.vikash.mp3player.data.FeedbackKind
import com.vikash.mp3player.data.MyReport
import kotlinx.coroutines.launch

private enum class HelpPage { Home, Bug, Feature, Support, Mine }

/** Settings > Help & feedback: report a bug, ask for a feature, contact support, see your requests. */
@Composable
fun HelpScreen(isPro: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val feedback = remember { Feedback(context.applicationContext) }
    var page by rememberSaveable { mutableStateOf(HelpPage.Home) }
    BackHandler { if (page == HelpPage.Home) onClose() else page = HelpPage.Home }
    LaunchedEffect(Unit) {
        val sent = runCatching { feedback.flushQueue() }.getOrDefault(0)
        if (sent > 0) Toast.makeText(context, context.getString(R.string.help_queue_sent), Toast.LENGTH_SHORT).show()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (page == HelpPage.Home) onClose() else page = HelpPage.Home }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
                Text(
                    stringResource(
                        when (page) {
                            HelpPage.Home -> R.string.help_title
                            HelpPage.Bug -> R.string.help_bug
                            HelpPage.Feature -> R.string.help_feature
                            HelpPage.Support -> R.string.help_support
                            HelpPage.Mine -> R.string.help_mine
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (page) {
                    HelpPage.Home -> {
                        HelpEntry(Icons.Default.BugReport, stringResource(R.string.help_bug), stringResource(R.string.help_bug_desc)) { page = HelpPage.Bug }
                        HelpEntry(Icons.Default.Lightbulb, stringResource(R.string.help_feature), stringResource(R.string.help_feature_desc)) { page = HelpPage.Feature }
                        HelpEntry(Icons.Default.SupportAgent, stringResource(R.string.help_support), stringResource(R.string.help_support_desc)) { page = HelpPage.Support }
                        HelpEntry(Icons.Default.Inbox, stringResource(R.string.help_mine), stringResource(R.string.help_mine_desc)) { page = HelpPage.Mine }
                        Text(stringResource(R.string.help_privacy), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HelpPage.Bug -> ReportForm(feedback, FeedbackKind.Bug, isPro) { page = HelpPage.Home }
                    HelpPage.Feature -> {
                        ReportForm(feedback, FeedbackKind.Feature, isPro) { page = HelpPage.Home }
                        if (feedback.hasServer) TopFeatures(feedback)
                    }
                    HelpPage.Support -> ReportForm(feedback, FeedbackKind.Support, isPro) { page = HelpPage.Home }
                    HelpPage.Mine -> MyRequests(feedback)
                }
            }
        }
    }
}

@Composable
private fun HelpEntry(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReportForm(feedback: Feedback, kind: FeedbackKind, isPro: Boolean, onSent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(kind) { mutableStateOf("") }
    var body by rememberSaveable(kind) { mutableStateOf("") }
    var contact by rememberSaveable(kind) { mutableStateOf("") }
    var orderId by rememberSaveable(kind) { mutableStateOf("") }
    var screenshot by remember(kind) { mutableStateOf<Uri?>(null) }
    var logs by rememberSaveable(kind) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) screenshot = it }

    OutlinedTextField(
        value = title, onValueChange = { title = it.take(120) }, singleLine = true,
        label = { Text(stringResource(if (kind == FeedbackKind.Support) R.string.help_field_subject else R.string.help_field_title)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = body, onValueChange = { body = it.take(4000) }, minLines = 4,
        label = {
            Text(stringResource(when (kind) {
                FeedbackKind.Bug -> R.string.help_field_what_happened
                FeedbackKind.Feature -> R.string.help_field_describe
                FeedbackKind.Support -> R.string.help_field_message
            }))
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = contact, onValueChange = { contact = it.take(120) }, singleLine = true,
        label = { Text(stringResource(R.string.help_field_contact)) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (kind == FeedbackKind.Support) OutlinedTextField(
        value = orderId, onValueChange = { orderId = it.take(60) }, singleLine = true,
        label = { Text(stringResource(R.string.help_field_order)) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (kind == FeedbackKind.Bug) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                Text(stringResource(if (screenshot == null) R.string.help_add_screenshot else R.string.help_screenshot_added))
            }
        }
        if (feedback.hasServer) Row(Modifier.clickable { logs = !logs }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = logs, onCheckedChange = { logs = it })
            Text(stringResource(R.string.help_attach_logs))
        }
    }
    if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
    Button(
        enabled = !sending && title.trim().length >= 3 && body.isNotBlank(),
        onClick = {
            sending = true
            scope.launch {
                val result = feedback.send(kind, title, body, contact, orderId, isPro, screenshot, logs)
                sending = false
                when (result) {
                    is Feedback.SendResult.Sent -> {
                        Toast.makeText(context, context.getString(R.string.help_sent, result.ref), Toast.LENGTH_LONG).show()
                        onSent()
                    }
                    Feedback.SendResult.Queued -> {
                        Toast.makeText(context, context.getString(R.string.help_queued), Toast.LENGTH_LONG).show()
                        onSent()
                    }
                    Feedback.SendResult.RateLimited ->
                        Toast.makeText(context, context.getString(R.string.help_rate_limited), Toast.LENGTH_LONG).show()
                    is Feedback.SendResult.Email -> try {
                        context.startActivity(Intent.createChooser(result.intent, context.getString(R.string.help_send_email)))
                        onSent()
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, context.getString(R.string.help_no_email_app), Toast.LENGTH_LONG).show()
                    }
                    is Feedback.SendResult.Failed ->
                        Toast.makeText(context, context.getString(R.string.help_failed, result.message), Toast.LENGTH_LONG).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.help_send)) }
}

@Composable
private fun TopFeatures(feedback: Feedback) {
    val scope = rememberCoroutineScope()
    var ideas by remember { mutableStateOf<List<FeatureIdea>?>(null) }
    var voted by remember { mutableStateOf(feedback.votedIds()) }
    LaunchedEffect(Unit) { ideas = runCatching { feedback.features() }.getOrDefault(emptyList()) }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.help_top_requests), style = MaterialTheme.typography.titleMedium)
    val list = ideas
    when {
        list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        list.isEmpty() -> Text(stringResource(R.string.help_no_requests_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> list.forEach { idea ->
            val mine = idea.id.toString() in voted
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = !mine,
                    onClick = {
                        scope.launch {
                            if (feedback.vote(idea.id)) {
                                voted = feedback.votedIds()
                                ideas = ideas?.map { if (it.id == idea.id) it.copy(votes = it.votes + 1) else it }
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.ArrowDropUp, stringResource(R.string.help_vote),
                        tint = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${idea.votes}", fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(idea.title)
                    if (idea.status.isNotBlank()) Text(idea.status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MyRequests(feedback: Feedback) {
    if (!feedback.hasServer) {
        Text(stringResource(R.string.help_mine_email), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var reports by remember { mutableStateOf<List<MyReport>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { feedback.myReports() }.onSuccess { reports = it }.onFailure { failed = true }
    }
    val list = reports
    when {
        failed -> Text(stringResource(R.string.discover_offline))
        list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        list.isEmpty() -> Text(stringResource(R.string.help_mine_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> list.forEach { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${r.ref} · ${r.status}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(r.title, style = MaterialTheme.typography.titleMedium)
                    r.latestReply?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.help_reply, it), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
