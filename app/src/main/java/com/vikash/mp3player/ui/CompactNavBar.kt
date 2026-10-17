package com.vikash.mp3player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NavTab(val label: String, val icon: ImageVector)

/**
 * Slim 56dp bottom bar (Material's is 80dp) so more songs fit on screen. The selected tab gets
 * a rose pill that stretches in with a spring, and its icon pops slightly.
 */
@Composable
fun CompactNavBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val on = i == selected
                val pillWidth by animateDpAsState(if (on) 52.dp else 28.dp, spring(dampingRatio = 0.6f), label = "pill")
                val pillColor by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, label = "pillColor",
                )
                val tint by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tint",
                )
                val pop by animateFloatAsState(if (on) 1.12f else 1f, spring(dampingRatio = 0.45f), label = "pop")
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.height(28.dp).width(pillWidth).clip(RoundedCornerShape(14.dp)).background(pillColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon, null, tint = tint, modifier = Modifier.size(22.dp).scale(pop))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        color = tint,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
