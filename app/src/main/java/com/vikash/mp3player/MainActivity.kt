package com.vikash.mp3player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vikash.mp3player.monetization.Ads
import com.vikash.mp3player.ui.MainViewModel
import com.vikash.mp3player.ui.MusicPlayerApp
import com.vikash.mp3player.ui.theme.Mp3PlayerTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val billing = (application as MusicApp).billing
        billing.start()
        // Users who paid never see the consent form or load the ads SDK.
        if (!billing.adsRemoved.value) Ads.gatherConsentAndInit(this)

        setContent {
            Mp3PlayerTheme {
                MusicPlayerApp(vm, onRemoveAds = { billing.purchaseRemoveAds(this) })
            }
        }
    }
}
