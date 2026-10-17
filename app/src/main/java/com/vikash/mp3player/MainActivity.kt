package com.vikash.mp3player

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vikash.mp3player.monetization.Ads
import com.vikash.mp3player.monetization.BillingManager
import com.vikash.mp3player.play.AppUpdates
import com.vikash.mp3player.play.PlayReview
import com.vikash.mp3player.ui.MainViewModel
import com.vikash.mp3player.ui.MusicPlayerApp
import com.vikash.mp3player.ui.theme.Mp3PlayerTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // Flexible update flow: Play shows its own sheet; the download runs in the background.
    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val billing = (application as MusicApp).billing
        billing.start()
        // Users who paid never see the consent form or load the ads SDK.
        if (!billing.adsRemoved.value) Ads.gatherConsentAndInit(this)

        if (savedInstanceState == null) {
            val engagement = (application as MusicApp).engagement
            engagement.recordLaunch()
            if (engagement.shouldCheckUpdate()) {
                engagement.markUpdateChecked()
                AppUpdates.check(this, updateLauncher)
            }
            if (engagement.shouldAskReview()) {
                // Ask after the user has had time to enjoy the app, never on the first screen.
                window.decorView.postDelayed({
                    if (!isFinishing && !isDestroyed) PlayReview.ask(this) { engagement.markReviewAsked() }
                }, 30_000)
            }
        }

        setContent {
            val app = application as MusicApp
            val themeMode by app.prefs.themeMode.collectAsState()
            val pro by app.proUnlocked.collectAsState()
            val amoled by app.prefs.amoledBlack.collectAsState()
            val wallpaper by app.prefs.wallpaperColors.collectAsState()
            // Pro looks fall back to the brand theme if Pro (or the trial) ends.
            Mp3PlayerTheme(themeMode, amoledBlack = pro && amoled, wallpaperColors = pro && wallpaper) {
                MusicPlayerApp(vm, onRemoveAds = {
                    when (billing.purchaseRemoveAds(this)) {
                        BillingManager.Buy.OPENED -> Unit
                        BillingManager.Buy.LOADING -> Toast.makeText(this, R.string.billing_loading, Toast.LENGTH_SHORT).show()
                        BillingManager.Buy.UNAVAILABLE -> Toast.makeText(
                            this,
                            getString(if (BuildConfig.DEBUG) R.string.pro_purchase_test_build else R.string.pro_purchase_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdates.refresh()
    }
}
