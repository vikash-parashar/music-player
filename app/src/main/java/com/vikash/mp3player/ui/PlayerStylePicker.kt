package com.vikash.mp3player.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vikash.mp3player.R
import com.vikash.mp3player.data.PlayerStyle

@StringRes
fun playerStyleLabel(style: PlayerStyle): Int = when (style) {
    PlayerStyle.Record -> R.string.pstyle_record
    PlayerStyle.Cassette -> R.string.pstyle_cassette
    PlayerStyle.Boombox -> R.string.pstyle_boombox
    PlayerStyle.Jukebox -> R.string.pstyle_jukebox
    PlayerStyle.ReelToReel -> R.string.pstyle_reel
    PlayerStyle.Neon -> R.string.pstyle_neon
    PlayerStyle.Lyrics -> R.string.pstyle_lyrics
    PlayerStyle.Radio -> R.string.pstyle_radio
    PlayerStyle.ClickWheel -> R.string.pstyle_wheel
}

private fun styleIcon(style: PlayerStyle): String = when (style) {
    PlayerStyle.Record -> "💿"
    PlayerStyle.Cassette -> "📼"
    PlayerStyle.Boombox -> "📻"
    PlayerStyle.Jukebox -> "🎰"
    PlayerStyle.ReelToReel -> "🎞"
    PlayerStyle.Neon -> "🌀"
    PlayerStyle.Lyrics -> "🎤"
    PlayerStyle.Radio -> "📡"
    PlayerStyle.ClickWheel -> "⭕"
}

/** Bottom sheet with every Now Playing style. Picking a Pro style without Pro opens the Pro screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStyleSheet(
    current: PlayerStyle,
    unlocked: Boolean,
    onPick: (PlayerStyle) -> Unit,
    onGetPro: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.pstyle_title), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(stringResource(R.string.pstyle_desc), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), textAlign = TextAlign.Center)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(PlayerStyle.entries) { style ->
                    val selected = style == current
                    val locked = style.pro && !unlocked
                    OutlinedCard(
                        onClick = { if (locked) onGetPro() else { onPick(style); onDismiss() } },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.fillMaxWidth().height(104.dp).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(styleIcon(style), style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(playerStyleLabel(style)), style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center, maxLines = 2)
                            Text(
                                stringResource(if (style.pro) R.string.pstyle_pro else R.string.pstyle_free),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (style.pro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
