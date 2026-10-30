package com.vikash.mp3player.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vikash.mp3player.ui.skin.SkinButton as Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.PlayerStyle

/** Free users can preview each Pro player style once a day, for a few seconds. */
object StylePreview {
    const val SECONDS = 6
    private const val PREFS = "style_previews"

    /** Returns true (and records it) if [style] hasn't been previewed today. */
    fun tryUse(context: Context, style: PlayerStyle): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toEpochDay()
        if (prefs.getLong(style.id, -1L) == today) return false
        prefs.edit().putLong(style.id, today).apply()
        return true
    }
}

/** Gold "PRO PREVIEW · 5s" tag in the corner of the player picture. */
@Composable
fun StylePreviewBadge(secondsLeft: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.pstyle_preview_badge, secondsLeft),
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

/** Shown over the player picture after a preview ends: unlock with Pro, or go back to Record. */
@Composable
fun StyleUnlockCard(styleName: String, onUnlock: () -> Unit, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.pstyle_locked_title, styleName),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.pstyle_locked_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onUnlock) {
                Text(stringResource(R.string.pstyle_unlock) + " · " + stringResource(R.string.pro_display_price))
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.pstyle_back_record), color = Color.White.copy(alpha = 0.8f)) }
        }
    }
}
