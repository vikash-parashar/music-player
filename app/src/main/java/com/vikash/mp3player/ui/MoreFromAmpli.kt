package com.vikash.mp3player.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.BuildConfig
import com.vikash.mp3player.R

/** The other Ampli apps, shown in Settings (never this app itself). */
object MoreApps {
    const val SITE = "https://ampliapps.in"
    const val CARHUB_URL = "$SITE/#carhub"

    data class App(
        val name: String,
        val packageName: String,
        @StringRes val line: Int,
        @DrawableRes val icon: Int,
        val accent: Color,
        val soft: Color,
        val site: String,
        /** Flip to true when the app is live on Google Play. */
        val live: Boolean,
    )

    val apps = listOf(
        App("Ampli Car Hub", "com.vikash.ampli.drive", R.string.more_apps_carhub, R.drawable.ampli_carhub_icon, Color(0xFF0E7490), Color(0xFFCFFAFE), CARHUB_URL, live = false),
        App("Ampli Naukri", "com.vikash.ampli.naukri", R.string.more_apps_naukri, R.drawable.ampli_naukri_icon, Color(0xFFC2410C), Color(0xFFFFEDD5), "$SITE/#naukri", live = false),
    )

    fun open(context: Context, app: App) {
        val intents = if (app.live) listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")),
        ) else listOf(Intent(Intent.ACTION_VIEW, Uri.parse(app.site)))
        for (i in intents) {
            try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: ActivityNotFoundException) { }
        }
    }
}

/**
 * "Help the developer grow": the chai tips, at the top of Settings so people can find them.
 * Tips go through Google Play and unlock nothing.
 */
@Composable
fun SupportDeveloperCard(tipPrices: Map<String, String>, tipsReceived: Int, onTip: (String) -> Unit) {
    val steam by rememberInfiniteTransition(label = "chai").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "steam",
    )
    Column {
        SectionLabel(stringResource(R.string.support_dev_label))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFFE11D48))))
                .padding(16.dp),
        ) {
            Text("☕", fontSize = 32.sp, modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { translationY = -steam * 10f; rotationZ = -4f * steam })
            Column {
                Text(stringResource(R.string.tip_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 44.dp))
                Text(
                    stringResource(if (tipsReceived > 0) R.string.support_dev_thanks else R.string.support_dev_desc),
                    color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp, end = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TipPill(stringResource(R.string.tip_pill_small, tipPrices[BuildConfig.TIP_SMALL_ID] ?: "₹49"), false, Modifier.weight(1f)) { onTip(BuildConfig.TIP_SMALL_ID) }
                    TipPill(stringResource(R.string.tip_pill_large, tipPrices[BuildConfig.TIP_LARGE_ID] ?: "₹99"), false, Modifier.weight(1f)) { onTip(BuildConfig.TIP_LARGE_ID) }
                    TipPill(stringResource(R.string.tip_pill_xl, tipPrices[BuildConfig.TIP_XL_ID] ?: "₹199"), true, Modifier.weight(1f)) { onTip(BuildConfig.TIP_XL_ID) }
                }
            }
        }
    }
}

@Composable
private fun TipPill(label: String, strong: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(CircleShape).background(if (strong) Color.White else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (strong) Color(0xFFBE123C) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Cards for the other Ampli apps. "Coming soon" opens ampliapps.in until an app is on Play. */
@Composable
fun MoreFromAmpliCard() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.more_apps_label))
        MoreApps.apps.forEach { app ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    .clickable { MoreApps.open(context, app) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(app.icon), null, Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(app.line), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    stringResource(if (app.live) R.string.more_apps_get else R.string.more_apps_coming_soon),
                    color = app.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(app.soft).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}
