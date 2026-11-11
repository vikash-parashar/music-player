package com.vikash.mp3player.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.vikash.mp3player.R
import kotlinx.coroutines.launch

/**
 * Opens when a Glass Cassette widget is added, and again when it is long-pressed and reconfigured
 * (Android 12+). The person picks one of the 17 looks and the pen colour for the song name.
 */
class CassetteStyleActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the launcher's default (on Android 12+ the widget is still added).
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val startLook = GlassCassette.lookIndex(this, widgetId)
        val startPen = GlassCassette.penChoice(this, widgetId)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Picker(startLook, startPen) { look, pen -> done(look, pen) }
            }
        }
    }

    private fun done(look: Int, pen: Int) {
        GlassCassette.save(this, widgetId, look, pen)
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@CassetteStyleActivity).getGlanceIdBy(widgetId)
                CassetteWidget().update(this@CassetteStyleActivity, glanceId)
            }
            runCatching { WidgetState.redraw(this@CassetteStyleActivity) }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}

@Composable
private fun Picker(startLook: Int, startPen: Int, onDone: (Int, Int) -> Unit) {
    var look by remember { mutableIntStateOf(startLook) }
    var pen by remember { mutableIntStateOf(startPen) }
    val penNames = listOf(R.string.cassette_pen_blue, R.string.cassette_pen_green, R.string.cassette_pen_red)
    Column(
        Modifier.fillMaxSize().background(Color(0xFF1B1730)).safeDrawingPadding().padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.cassette_pick_title), color = Color.White, fontSize = 22.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
        )
        Text(stringResource(R.string.cassette_pen), color = Color(0xFFB9B1CC), fontSize = 13.sp)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = pen < 0, onClick = { pen = -1 },
                label = { Text(stringResource(R.string.cassette_pen_auto)) },
            )
            penNames.forEachIndexed { i, name ->
                FilterChip(
                    selected = pen == i, onClick = { pen = i },
                    label = { Text(stringResource(name)) },
                    leadingIcon = {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(GlassCassette.PENS[i])))
                    },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(GlassCassette.LOOKS) { i, item ->
                val selected = i == look
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFFF6C85F) else Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                        .clickable { look = i }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // A little wallpaper behind, so the glass reads as glass.
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(
                            Brush.linearGradient(listOf(Color(0xFFFF9A6B), Color(0xFF7A5A8E), Color(0xFF5AD1C2))),
                        ).padding(6.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(GlassCassette.BODY_W / GlassCassette.BODY_H)) {
                            Image(painterResource(item.back), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            Image(painterResource(item.front), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                    Text(
                        "${i + 1}. ${item.name}", color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Button(
            onClick = { onDone(look, pen) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) { Text(stringResource(R.string.cassette_use)) }
    }
}
