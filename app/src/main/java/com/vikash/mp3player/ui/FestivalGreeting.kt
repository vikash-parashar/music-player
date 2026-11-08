package com.vikash.mp3player.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikash.mp3player.R
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One-time festival greeting shown on app open during a festival window.
 * Diwali 2026: 8 Nov (shown 8–12 Nov, local time), once per install.
 */
object FestivalGreeting {
    private const val KEY = "festival_seen_diwali_2026"

    private fun inWindow(now: Calendar = Calendar.getInstance()): Boolean {
        val y = now.get(Calendar.YEAR)
        val m = now.get(Calendar.MONTH)
        val d = now.get(Calendar.DAY_OF_MONTH)
        return y == 2026 && m == Calendar.NOVEMBER && d in 8..12
    }

    fun shouldShow(c: Context): Boolean =
        inWindow() && !c.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markSeen(c: Context) {
        c.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

private val Gold = Color(0xFFFFC857)
private val Cream = Color(0xFFF6E7D2)
private val Sparks = listOf(Color(0xFFFFC857), Color(0xFFFF6B6B), Color(0xFF7BDFF2), Color(0xFFF7AEF8), Color(0xFFB8F2E6))

@Composable
fun DiwaliGreetingScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val anim = rememberInfiniteTransition(label = "diwali")
    val t by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "fw")
    val flicker by anim.animateFloat(0.9f, 1.12f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "fl")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF120A1F), Color(0xFF2A0F2A), Color(0xFF5A1A1F))))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // Fireworks: three bursts on staggered phases.
        Canvas(Modifier.fillMaxSize()) {
            val bursts = listOf(Triple(0.22f, 0.14f, 0f), Triple(0.72f, 0.1f, 0.33f), Triple(0.5f, 0.26f, 0.66f))
            bursts.forEachIndexed { bi, (bx, by, ph) ->
                val p = (t + ph) % 1f
                val r = size.minDimension * 0.22f * p
                val alpha = (1f - p).coerceIn(0f, 1f)
                val col = Sparks[bi % Sparks.size]
                for (i in 0 until 28) {
                    val a = 2 * PI * i / 28
                    val c = Offset(size.width * bx + (cos(a) * r).toFloat(), size.height * by + (sin(a) * r).toFloat() + r * r / size.height)
                    drawCircle(col.copy(alpha = alpha), radius = 3.dp.toPx(), center = c)
                }
            }
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("शुभ दीपावली", color = Gold, fontSize = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("HAPPY DIWALI", color = Color(0xFFFFD9A0), fontSize = 13.sp, letterSpacing = 4.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.diwali_msg), color = Cream, fontSize = 16.sp, lineHeight = 24.sp,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 340.dp),
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(5) { i -> Diya(if (i % 2 == 0) flicker else 2.02f - flicker) }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.diwali_sig), color = Color(0xFFE0B98A), fontSize = 13.sp)
            Spacer(Modifier.height(26.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Gold, Color(0xFFF08A24))))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.diwali_cta), color = Color(0xFF3A1405), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Diya(flame: Float) {
    Canvas(Modifier.size(width = 36.dp, height = 46.dp)) {
        val w = size.width
        val h = size.height
        // Glow
        drawCircle(Color(0x55FFB300), radius = w * 0.55f, center = Offset(w / 2, h * 0.42f))
        // Flame (teardrop), scaled by flicker
        val fh = h * 0.42f * flame
        val base = Offset(w / 2, h * 0.66f)
        val path = Path().apply {
            moveTo(base.x, base.y - fh)
            cubicTo(base.x + w * 0.2f, base.y - fh * 0.5f, base.x + w * 0.14f, base.y, base.x, base.y)
            cubicTo(base.x - w * 0.14f, base.y, base.x - w * 0.2f, base.y - fh * 0.5f, base.x, base.y - fh)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFF7A00), Color(0xFFFFC23A), Color(0xFFFFF7C2)), startY = base.y - fh, endY = base.y))
        // Clay cup
        drawArc(
            Brush.verticalGradient(listOf(Color(0xFFD9622B), Color(0xFF8E2F14)), startY = h * 0.6f, endY = h),
            startAngle = 0f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(0f, h * 0.6f - h * 0.38f), size = Size(w, h * 0.76f),
        )
    }
}
