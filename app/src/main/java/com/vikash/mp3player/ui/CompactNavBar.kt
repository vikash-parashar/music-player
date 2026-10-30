package com.vikash.mp3player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.ui.theme.Lux

/** A bottom tab. [selectedIcon] (usually the filled version) is shown while the tab is active. */
data class NavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * The "minimal luxury" dock: a frosted pill floating above the screen. A warm glow glides to the
 * tapped tab on a soft spring, the tab's icon fills in and grows a little, its label turns gold and
 * a small gold dot appears under it. Lite mode drops the glow and keeps the rest.
 */
@Composable
fun CompactNavBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit) {
    val lite = LocalLiteMode.current
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).luxGlass(shape)) {
            val slot = maxWidth / tabs.size.coerceAtLeast(1)
            val orb = 58.dp
            val orbX by animateDpAsState(
                slot * selected + (slot - orb) / 2,
                spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                label = "orb",
            )
            if (!lite) Canvas(Modifier.offset(x = orbX, y = 3.dp).size(orb)) { drawCircle(luxGlowBrush()) }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, tab ->
                    val on = i == selected
                    val tint by animateColorAsState(
                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tint",
                    )
                    val labelColor by animateColorAsState(
                        if (on) Lux.GoldDeep else MaterialTheme.colorScheme.onSurfaceVariant, label = "label",
                    )
                    val pop by animateFloatAsState(if (on) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "pop")
                    val dot by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.5f), label = "dot")
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            if (on) tab.selectedIcon else tab.icon, null, tint = tint,
                            modifier = Modifier.size(22.dp).scale(pop),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            tab.label,
                            fontSize = 10.5.sp,
                            lineHeight = 12.sp,
                            color = labelColor,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.size(4.dp).scale(dot).background(Lux.GoldDeep, CircleShape))
                    }
                }
            }
        }
    }
}
