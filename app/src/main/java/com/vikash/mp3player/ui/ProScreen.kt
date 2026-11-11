package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Palette
import com.vikash.mp3player.ui.skin.SkinButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.vikash.mp3player.ui.skin.SkinOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.BuildConfig
import com.vikash.mp3player.R
import com.vikash.mp3player.monetization.BillingManager
import com.vikash.mp3player.monetization.ProTrial
import com.vikash.mp3player.recognize.SongRecognizer
import com.vikash.mp3player.ui.theme.Ampli

private const val ProTrialHours = ProTrial.HOURS

/** One of the three things on sale: the looks pack, Ampli Pro, or Pro + Supporter. */
private data class Tier(val id: String, val name: Int, val desc: Int, val price: String, val best: Boolean = false, val owned: Boolean = false)

/**
 * The Ampli Pro plan: what it unlocks, the one-time price and the Buy button. Every "Get Pro"
 * in the app opens this. The price comes from Google Play; until Play answers (or in test
 * builds, which can't reach Play) the planned launch price is shown.
 */
@Composable
fun ProScreen(
    isPro: Boolean,
    playPrice: String?,
    onBuy: (String) -> Unit,
    onRestore: () -> Unit,
    onTestPro: () -> Unit,
    onClose: () -> Unit,
    trialAvailable: Boolean = false,
    trialUntil: Long = 0L,
    onTrial: () -> Unit = {},
    tipPrices: Map<String, String> = emptyMap(),
    onTip: (String) -> Unit = {},
    supporter: Boolean = false,
    looksPack: Boolean = false,
    supporterPrice: String? = null,
    packPrice: String? = null,
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { com.vikash.mp3player.data.OwnerHooks.event("pro_feature_opened") }
    BackHandler(onBack = onClose)
    val price = playPrice ?: stringResource(R.string.pro_display_price)
    val tiers = listOf(
        Tier(BillingManager.LOOKS_PACK_ID, R.string.tier_pack_name, R.string.tier_pack_desc, packPrice ?: stringResource(R.string.tier_pack_price), owned = looksPack),
        Tier(BuildConfig.REMOVE_ADS_PRODUCT_ID, R.string.tier_pro_name, R.string.tier_pro_desc, price, best = true),
        Tier(BillingManager.SUPPORTER_ID, R.string.tier_supporter_name, R.string.tier_supporter_desc, supporterPrice ?: stringResource(R.string.tier_supporter_price)),
    )
    var picked by rememberSaveable { mutableStateOf(BuildConfig.REMOVE_ADS_PRODUCT_ID) }
    val choice = tiers.firstOrNull { it.id == picked && !it.owned } ?: tiers[1]

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Hero
            Box(Modifier.fillMaxWidth().background(Ampli.Gradient)) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = Color.White)
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(104.dp))
                    Text(stringResource(R.string.pro_name), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.pro_tagline), color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    PromiseBadges()
                }
            }

            if (isPro) {
                ThankYou(supporter)
            } else {
                TrialBanner(trialUntil)
                Text(
                    stringResource(R.string.tier_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                )
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiers.forEach { t -> TierCard(t, selected = t.id == choice.id) { if (!t.owned) picked = t.id } }
                }
                Text(
                    stringResource(R.string.pro_plan_devices),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            // What Pro unlocks
            Text(
                stringResource(R.string.pro_unlocks),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Feature(Icons.Default.Block, R.string.pro_feature_no_ads, R.string.pro_feature_no_ads_desc)
                Feature(Icons.Default.Cast, R.string.pro_feature_cast, R.string.pro_feature_cast_desc)
                Feature(
                    Icons.Default.Hearing,
                    if (SongRecognizer.isConfigured) R.string.pro_feature_recognize else R.string.pro_feature_recognize_soon,
                    R.string.pro_feature_recognize_desc,
                )
                Feature(Icons.Default.VideoLibrary, R.string.pro_feature_videos, R.string.pro_feature_videos_desc)
                Feature(Icons.Default.AutoAwesome, R.string.pro_feature_styles, R.string.pro_feature_styles_desc)
                Feature(Icons.Default.Equalizer, R.string.pro_feature_sound, R.string.pro_feature_sound_desc)
                Feature(Icons.Default.Palette, R.string.pro_feature_looks, R.string.pro_feature_looks_desc)
                Feature(Icons.Default.GraphicEq, R.string.pro_feature_visualizer, R.string.pro_feature_visualizer_desc)
            }

            // What stays free
            Surface(
                Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.pro_free_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.pro_free_list),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // "Buy the developer a chai": optional tips, they unlock nothing.
            Surface(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalCafe, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tip_title), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.tip_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onTip(BuildConfig.TIP_SMALL_ID) }) {
                            Text(stringResource(R.string.tip_small, tipPrices[BuildConfig.TIP_SMALL_ID] ?: "₹49"))
                        }
                        OutlinedButton(onClick = { onTip(BuildConfig.TIP_LARGE_ID) }) {
                            Text(stringResource(R.string.tip_large, tipPrices[BuildConfig.TIP_LARGE_ID] ?: "₹99"))
                        }
                    }
                }
            }
        }

        // Buy bar
        Surface(tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isPro) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (supporter) Icons.Default.Favorite else Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (supporter) R.string.pro_supporter_badge else R.string.pro_you_have),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Ampli.Gradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(
                            onClick = { onBuy(choice.id) },
                            modifier = Modifier.fillMaxSize(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                        ) { Text(stringResource(R.string.tier_buy, stringResource(choice.name), choice.price), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    val trialActive = trialUntil > System.currentTimeMillis()
                    if (trialActive && !ProTrial.inWelcome()) {
                        Spacer(Modifier.height(6.dp))
                        val ends = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(trialUntil))
                        Text(stringResource(R.string.trial_active, ends), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    } else if (!trialActive && trialAvailable) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = onTrial, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.OndemandVideo, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.trial_button, ProTrialHours))
                        }
                    }
                    TextButton(onClick = onRestore) { Text(stringResource(R.string.pro_restore)) }
                    if (BuildConfig.DEBUG) {
                        OutlinedButton(onClick = onTestPro) { Text(stringResource(R.string.pro_test_unlock)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: Int, desc: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Free Pro: 9 days left" while the welcome trial runs, so people know what they will lose. */
@Composable
private fun TrialBanner(trialUntil: Long) {
    if (!ProTrial.inWelcome() || trialUntil <= System.currentTimeMillis()) return
    val days = ProTrial.daysLeft()
    Surface(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.trial_welcome_left, days, days),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.trial_welcome_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TierCard(t: Tier, selected: Boolean, onPick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth().border(
            if (selected) 2.dp else 1.dp,
            if (selected) primary else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(18.dp),
        ),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (selected) 3.dp else 0.dp,
    ) {
        Column {
            if (t.best) {
                Box(Modifier.fillMaxWidth().background(Ampli.Gradient).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.tier_best), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        t.owned -> Icons.Default.CheckCircle
                        selected -> Icons.Default.RadioButtonChecked
                        else -> Icons.Default.RadioButtonUnchecked
                    },
                    null,
                    tint = if (selected || t.owned) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(t.name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(t.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (t.owned) stringResource(R.string.tier_owned) else t.price,
                        fontSize = if (t.best) 24.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (t.best) primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (!t.owned) {
                        Text(stringResource(R.string.pro_plan_one_time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** What a buyer sees here once Pro is theirs; supporters get their own thank-you. */
@Composable
private fun ThankYou(supporter: Boolean) {
    Surface(
        Modifier.fillMaxWidth().padding(16.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (supporter) Icons.Default.Favorite else Icons.Default.CheckCircle, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (supporter) R.string.pro_supporter_badge else R.string.pro_you_have),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (supporter) R.string.supporter_thanks else R.string.pro_thanks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
