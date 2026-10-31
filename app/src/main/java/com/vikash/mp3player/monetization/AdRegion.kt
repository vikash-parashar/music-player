package com.vikash.mp3player.monetization

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Where the person is, roughly, for ad volume only. In the US, Canada, UK, EU/EEA, Switzerland,
 * Australia, New Zealand and Japan, people expect a quieter app: there we drop the bottom banner
 * and keep just the native card on the player and the optional rewarded ads.
 *
 * Uses the SIM country, then the network country, then the phone's language region. No
 * location permission and nothing is sent anywhere.
 */
object AdRegion {
    private val QUIET = setOf(
        "US", "CA", "GB", "IE", "AU", "NZ", "JP", "CH", "NO", "IS", "LI",
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IT",
        "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
    )

    @Volatile private var cached: String? = null

    fun country(context: Context): String {
        cached?.let { return it }
        val tm = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val c = listOfNotNull(
            runCatching { tm?.simCountryIso }.getOrNull(),
            runCatching { tm?.networkCountryIso }.getOrNull(),
            Locale.getDefault().country,
        ).map { it.trim().uppercase(Locale.ROOT) }.firstOrNull { it.length == 2 }.orEmpty()
        cached = c
        return c
    }

    /** True where the bottom banner is left out. */
    fun fewerAds(context: Context): Boolean = country(context) in QUIET
}
