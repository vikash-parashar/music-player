package com.vikash.mp3player.monetization

import android.app.Activity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.vikash.mp3player.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob setup. Asks for GDPR/regional consent first (Google's UMP form, required for EEA/UK
 * users), then initialises the Mobile Ads SDK.
 */
object Ads {
    private val initStarted = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun gatherConsentAndInit(activity: Activity) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    refreshPrivacy(activity)
                    if (consent.canRequestAds()) init(activity)
                }
            },
            { if (consent.canRequestAds()) init(activity) },
        )
        // Consent from a previous session lets ads start loading right away.
        if (consent.canRequestAds()) init(activity)
    }

    /** True when a Rewarded ad unit is configured for this build. */
    val rewardedConfigured: Boolean get() = BuildConfig.ADMOB_REWARDED_ID.isNotBlank()

    /**
     * Loads and shows one opt-in rewarded ad. [onReward] runs only if the person watched it to the
     * end; [onUnavailable] runs when no ad could be loaded (offline, no consent, no fill).
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit) {
        if (!rewardedConfigured || !_ready.value) { onUnavailable(); return }
        RewardedAd.load(
            activity, BuildConfig.ADMOB_REWARDED_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (activity.isFinishing || activity.isDestroyed) return
                    ad.show(activity) { onReward() }
                }

                override fun onAdFailedToLoad(error: LoadAdError) = onUnavailable()
            },
        )
    }

    private val _privacyRequired = MutableStateFlow(false)
    /** True where the law requires a way to change ad consent later (EU/UK, some US states). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyRequired.asStateFlow()

    private fun refreshPrivacy(activity: Activity) {
        _privacyRequired.value = UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /** Settings > Privacy choices: Google's form to change ad consent. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { refreshPrivacy(activity) }
    }

    private fun init(activity: Activity) {
        if (!initStarted.compareAndSet(false, true)) return
        MobileAds.initialize(activity.applicationContext) { _ready.value = true }
    }
}

/** Adaptive banner that fills the screen width. Render it only when ads are on. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        val adView = remember(widthDp) {
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        }
        DisposableEffect(adView) { onDispose { adView.destroy() } }
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
    }
}
