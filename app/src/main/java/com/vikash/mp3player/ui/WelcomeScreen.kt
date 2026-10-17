package com.vikash.mp3player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import com.vikash.mp3player.ui.theme.Ampli
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val PAGES = 4

/**
 * First-run welcome: three swipeable pages (what Ampli is, what it can do, why it needs music
 * access) on a night-to-rose backdrop with a breathing glow, parallax pages and a pill indicator.
 * The last page's button asks for the permission.
 */
@Composable
fun WelcomeScreen(onAllow: () -> Unit) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1

    // Swallow back so the library underneath never flashes through.
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    val glow = rememberInfiniteTransition(label = "glow")
    val glowScale by glow.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ampli.Night, Color(0xFF2A0F1C))))
            // Eat touches so nothing behind the overlay reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
                .size(340.dp)
                .scale(glowScale)
                .background(Brush.radialGradient(listOf(Ampli.Rose.copy(alpha = 0.35f), Color.Transparent)), CircleShape),
        )

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                val skipAlpha by animateFloatAsState(if (last) 0f else 1f, label = "skip")
                TextButton(
                    onClick = { scope.launch { pager.animateScrollToPage(PAGES - 1) } },
                    enabled = !last,
                    modifier = Modifier.graphicsLayer { alpha = skipAlpha },
                ) { Text(stringResource(R.string.welcome_skip), color = Color.White.copy(alpha = 0.75f)) }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - offset * 0.7f
                            translationX = offset * size.width * 0.25f * if (page < pager.currentPage) -1 else 1
                            val s = 1f - offset * 0.08f
                            scaleX = s; scaleY = s
                        }
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        0 -> IntroPage()
                        1 -> FeaturesPage()
                        2 -> PrivacyPage()
                        else -> PermissionPage()
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(PAGES) { i ->
                    val selected = i == pager.currentPage
                    val width by animateDpAsState(if (selected) 28.dp else 8.dp, spring(dampingRatio = 0.6f), label = "dotW")
                    val color by animateColorAsState(if (selected) Ampli.Rose else Color.White.copy(alpha = 0.3f), label = "dotC")
                    Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(width).clip(CircleShape).background(color))
                }
            }

            PressButton(
                onClick = { if (last) onAllow() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            ) {
                AnimatedContent(
                    targetState = last,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
                    },
                    label = "cta",
                ) { isLast ->
                    Text(
                        stringResource(if (isLast) R.string.welcome_allow else R.string.welcome_next),
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PressButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "press")
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(Ampli.Rose, Ampli.RoseDeep)))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun IntroPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(132.dp))
        Spacer(Modifier.height(32.dp))
        Title(stringResource(R.string.welcome_1_title))
        Spacer(Modifier.height(14.dp))
        Body(stringResource(R.string.welcome_1_body))
    }
}

@Composable
private fun FeaturesPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Title(stringResource(R.string.welcome_2_title))
        Spacer(Modifier.height(28.dp))
        FeatureRow(Icons.Default.GraphicEq, stringResource(R.string.welcome_2_eq))
        FeatureRow(Icons.Default.NotificationsActive, stringResource(R.string.welcome_2_ringtone))
        FeatureRow(Icons.Default.DirectionsCar, stringResource(R.string.welcome_2_car))
        FeatureRow(Icons.Default.Share, stringResource(R.string.welcome_2_share))
    }
}

/** What Ampli sends and why (Owner hook spec v1.1 §6), shown once before the permission ask. */
@Composable
private fun PrivacyPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(Ampli.Rose.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Shield, null, tint = Ampli.Blush, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(32.dp))
        Title(stringResource(R.string.welcome_privacy_title))
        Spacer(Modifier.height(14.dp))
        Body(stringResource(R.string.welcome_privacy_body))
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.welcome_privacy_contact), color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(Ampli.Rose.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.LibraryMusic, null, tint = Ampli.Blush, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(32.dp))
        Title(stringResource(R.string.welcome_3_title))
        Spacer(Modifier.height(14.dp))
        Body(stringResource(R.string.welcome_3_body))
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.welcome_3_private), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ampli.Rose.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Ampli.Blush, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun Title(text: String) = Text(
    text, color = Color.White, textAlign = TextAlign.Center,
    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
)

@Composable
private fun Body(text: String) = Text(
    text, color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center, fontSize = 16.sp, lineHeight = 23.sp,
)
