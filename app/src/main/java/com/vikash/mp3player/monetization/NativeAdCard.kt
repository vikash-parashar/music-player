package com.vikash.mp3player.monetization

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.vikash.mp3player.BuildConfig
import com.vikash.mp3player.R

/** True when a Native ad unit is configured for this build. */
val nativeAdConfigured: Boolean get() = BuildConfig.ADMOB_NATIVE_ID.isNotBlank()

/**
 * Small native ad card (icon, "Ad" label, headline, one line of text, button). Shows nothing until
 * an ad has loaded, so a failed or empty request leaves no blank space. Render it only when ads are on.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    if (!nativeAdConfigured) return
    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        var disposed = false
        val loader = AdLoader.Builder(context, BuildConfig.ADMOB_NATIVE_ID)
            .forNativeAd { loaded ->
                if (disposed) loaded.destroy() else { ad?.destroy(); ad = loaded }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) { /* no fill: the card stays hidden */ }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            disposed = true
            ad?.destroy()
            ad = null
        }
    }

    val current = ad ?: return
    val colors = MaterialTheme.colorScheme
    val surface = colors.surfaceVariant.toArgb()
    val outline = colors.outlineVariant.toArgb()
    val onSurface = colors.onSurface.toArgb()
    val muted = colors.onSurfaceVariant.toArgb()
    val primary = colors.primary.toArgb()
    val onPrimary = colors.onPrimary.toArgb()
    val radius = with(LocalDensity.current) { 16f * density }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.native_ad_card, null) as NativeAdView
        },
        update = { view ->
            view.background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(surface)
                setStroke((radius / 16f).toInt().coerceAtLeast(1), outline)
            }
            val headline = view.findViewById<TextView>(R.id.native_ad_headline)
            val body = view.findViewById<TextView>(R.id.native_ad_body)
            val icon = view.findViewById<ImageView>(R.id.native_ad_icon)
            val cta = view.findViewById<Button>(R.id.native_ad_cta)
            val label = view.findViewById<TextView>(R.id.native_ad_label)

            label.setTextColor(onPrimary)
            label.background = GradientDrawable().apply { cornerRadius = radius / 4f; setColor(primary) }
            headline.setTextColor(onSurface)
            body.setTextColor(muted)
            cta.backgroundTintList = ColorStateList.valueOf(primary)
            cta.setTextColor(onPrimary)

            headline.text = current.headline
            view.headlineView = headline

            body.text = current.body.orEmpty()
            body.visibility = if (current.body.isNullOrBlank()) View.GONE else View.VISIBLE
            view.bodyView = body

            val img = current.icon?.drawable
            icon.setImageDrawable(img)
            icon.visibility = if (img == null) View.GONE else View.VISIBLE
            view.iconView = icon

            cta.text = current.callToAction.orEmpty()
            cta.visibility = if (current.callToAction.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
            view.callToActionView = cta

            view.setNativeAd(current)
        },
    )
}
