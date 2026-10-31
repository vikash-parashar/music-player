package com.vikash.mp3player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R

/**
 * The three promises shown up front on the welcome and Pro screens: no account, works offline,
 * and what you play stays on the phone. Wraps to more lines for longer languages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromiseBadges(modifier: Modifier = Modifier, tint: Color = Color.White) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Badge(Icons.Default.PersonOff, stringResource(R.string.promise_no_account), tint)
        Badge(Icons.Default.CloudOff, stringResource(R.string.promise_offline), tint)
        Badge(Icons.Default.Lock, stringResource(R.string.promise_private), tint)
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String, tint: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
