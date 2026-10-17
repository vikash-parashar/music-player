package com.vikash.mp3player.data

import android.content.Context
import android.content.SharedPreferences
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.vikash.mp3player.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/**
 * Pseudonymous numbers for the Ampli Owner app (spec v1 additions): which ad campaign an install came
 * from, completed purchases, and simple feature counters. No name, email, phone or ad ID: only a random install id
 * kept in app prefs (the same one Help & feedback uses). Everything is skipped while OWNER_API_URL is
 * empty, and failed calls are retried on the next app start.
 */
object OwnerHooks {
    private const val PRODUCT = "mp3"
    private const val KEY_INSTALL = "install_id"
    private const val KEY_INSTALL_SENT = "owner_install_sent"
    private const val KEY_EVENTS = "owner_events"
    private const val KEY_PURCHASES = "owner_purchases"
    private const val KEY_PLAYED = "owner_played_count"
    private const val KEY_USAGE = "owner_usage_on"
    /** The only names the Owner server accepts for this app (spec v1.1 §3); anything else would fail the whole batch. */
    private val ALLOWED = setOf(
        "played_10", "pro_feature_opened", "pro_trial", "feature_eq", "feature_record_player",
        "feature_sleep_timer", "feature_ringtone", "feature_wifi_transfer",
    )

    private val base = BuildConfig.OWNER_API_URL.trimEnd('/')
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context

    val enabled: Boolean get() = base.isNotEmpty()

    private val installId: String
        get() = prefs.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_INSTALL, it).apply() }

    private val appVersion get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    /** Called once from Application.onCreate: sends the install referrer once, plus anything queued. */
    fun init(context: Context) {
        app = context.applicationContext
        prefs = app.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
        if (!enabled) return
        if (!prefs.getBoolean(KEY_INSTALL_SENT, false)) sendInstall()
        scope.launch { flushPurchases(); flushEvents() }
    }

    /** The random install id (UUID v4), shown in Settings > About so people can ask for deletion. */
    fun installId(context: Context): String {
        val p = context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
        return p.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().also { p.edit().putString(KEY_INSTALL, it).apply() }
    }

    /** Settings > Share usage statistics. Installs and purchases are always sent (needed to check purchases). */
    fun usageOn(context: Context): Boolean = context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE).getBoolean(KEY_USAGE, true)

    fun setUsageOn(context: Context, on: Boolean) {
        val p = context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
        p.edit().putBoolean(KEY_USAGE, on).apply()
        if (!on) p.edit().remove(KEY_EVENTS).apply()
    }

    /** Counts a feature use. Sent in one batch, at most once per app start (on the next start). */
    fun event(name: String) {
        if (!::prefs.isInitialized || !enabled || name !in ALLOWED || !prefs.getBoolean(KEY_USAGE, true)) return
        val now = prefs.getStringSet(KEY_EVENTS, emptySet()).orEmpty()
        if (name !in now && now.size < 20) prefs.edit().putStringSet(KEY_EVENTS, now + name).apply()
    }

    /** Counts songs played; sends "played_10" the first time it reaches 10. */
    fun songPlayed() {
        if (!::prefs.isInitialized || !enabled || !prefs.getBoolean(KEY_USAGE, true)) return
        val n = prefs.getInt(KEY_PLAYED, 0)
        if (n >= 10) return
        prefs.edit().putInt(KEY_PLAYED, n + 1).apply()
        if (n + 1 == 10) event("played_10")
    }

    /**
     * A purchase Play has completed. Only the token goes up: the server checks it with Google Play and
     * prices it from its own table (spec v1.1 §2). Retries are safe, the token is unique.
     */
    fun purchase(kind: String, sku: String, purchaseToken: String) {
        if (!::prefs.isInitialized || !enabled) return
        val body = buildJsonObject {
            put("product", PRODUCT)
            put("install_id", installId)
            put("sku", sku)
            put("purchase_token", purchaseToken)
            put("kind", kind)
        }.toString()
        val queued = prefs.getStringSet(KEY_PURCHASES, emptySet()).orEmpty()
        prefs.edit().putStringSet(KEY_PURCHASES, (queued + body).toList().takeLast(30).toSet()).apply()
        scope.launch { flushPurchases() }
    }

    private fun sendInstall() {
        val client = InstallReferrerClient.newBuilder(app).build()
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    val raw = if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                        runCatching { client.installReferrer.installReferrer }.getOrNull().orEmpty()
                    } else ""
                    // Keep only the campaign tags (utm_*), nothing else the referrer may carry.
                    val referrer = raw.split('&').filter { it.startsWith("utm_") }.joinToString("&")
                    runCatching { client.endConnection() }
                    // Sideloaded builds have no referrer; still counted so tests show up.
                    scope.launch {
                        val body = buildJsonObject {
                            put("product", PRODUCT)
                            put("install_id", installId)
                            put("referrer", referrer)
                            put("app_version", appVersion)
                            put("country", Locale.getDefault().country)
                        }.toString()
                        if (post("/v1/public/installs", body)) prefs.edit().putBoolean(KEY_INSTALL_SENT, true).apply()
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {}
            })
        }
    }

    private fun flushPurchases() {
        val queued = prefs.getStringSet(KEY_PURCHASES, emptySet()).orEmpty()
        if (queued.isEmpty()) return
        // 2xx = stored; 4xx = the server will never take it (unknown sku, token Google rejected), so drop it too.
        val sent = queued.filter { postStatus("/v1/public/purchases", it).let { c -> c in 200..299 || c in 400..499 && c != 429 } }.toSet()
        if (sent.isNotEmpty()) prefs.edit().putStringSet(KEY_PURCHASES, prefs.getStringSet(KEY_PURCHASES, emptySet()).orEmpty() - sent).apply()
    }

    private fun flushEvents() {
        val events = prefs.getStringSet(KEY_EVENTS, emptySet()).orEmpty()
        if (events.isEmpty()) return
        val body = buildJsonObject {
            put("product", PRODUCT)
            put("install_id", installId)
            put("events", JsonArray(events.take(20).map { JsonPrimitive(it) }))
        }.toString()
        if (post("/v1/public/events", body)) {
            prefs.edit().putStringSet(KEY_EVENTS, prefs.getStringSet(KEY_EVENTS, emptySet()).orEmpty() - events).apply()
        }
    }

    private fun post(path: String, body: String): Boolean = postStatus(path, body) in 200..299

    /** HTTP status of the call, or -1 when it never reached the server. */
    private fun postStatus(path: String, body: String): Int = runCatching {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(-1)
}
