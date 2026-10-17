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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/**
 * Pseudonymous numbers for the Ampli Owner app (hook spec v1.2): which ad campaign an install came
 * from, completed purchases, and simple feature counters. No name, email, phone or ad ID: only a
 * random install id (UUID v4). Everything is skipped while OWNER_API_URL is empty.
 *
 * Nothing is sent before the first-run screens (with the privacy page) are finished. Calls that
 * fail are kept in a queue and retried on later app starts with backoff (1 h, 6 h, then daily for
 * up to 30 days); a 400 or 422 means the server will never take it, so it is dropped.
 *
 * All of this lives in its own prefs file, which is left out of backups (see backup rules), so a
 * restored phone gets a new install id instead of copying the old one.
 */
object OwnerHooks {
    private const val PRODUCT = "mp3"
    private const val PREFS = "owner_hooks"
    private const val KEY_INSTALL = "install_id"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_INSTALL_DONE = "install_sent"
    private const val KEY_QUEUE = "queue"
    private const val KEY_EVENTS = "events"
    private const val KEY_PLAYED = "played_count"
    private const val KEY_USAGE = "usage_on"

    private const val HOUR = 60L * 60L * 1000L
    private const val MAX_AGE = 30L * 24L * HOUR

    /** The only names the Owner server accepts for this app (spec §3); anything else fails the whole batch. */
    private val ALLOWED = setOf(
        "played_10", "pro_feature_opened", "pro_trial", "feature_eq", "feature_record_player",
        "feature_sleep_timer", "feature_ringtone", "feature_wifi_transfer",
    )

    private val base = BuildConfig.OWNER_API_URL.trimEnd('/')
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context

    val enabled: Boolean get() = base.isNotEmpty()

    private fun store(context: Context): SharedPreferences {
        if (!::prefs.isInitialized) {
            app = context.applicationContext
            prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Older builds kept the id in ampli_prefs (shared with Help & feedback): carry it over once.
            if (!prefs.contains(KEY_INSTALL)) {
                val shared = app.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
                val old = shared.getString("install_id", null)
                if (old != null && runCatching { UUID.fromString(old) }.isSuccess) prefs.edit().putString(KEY_INSTALL, old).apply()
                // Remove it there, since ampli_prefs is backed up and this id must not be.
                shared.edit().remove("install_id").apply()
            }
        }
        return prefs
    }

    /** The random install id (UUID v4, lowercase). Help & feedback and Settings > About use the same one. */
    fun installId(context: Context): String {
        val p = store(context)
        return p.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().also { p.edit().putString(KEY_INSTALL, it).apply() }
    }

    /**
     * Called from Application.onCreate. [onboarded] is true when the first-run screens were
     * already finished (or the user is upgrading from a build that had none).
     */
    fun init(context: Context, onboarded: Boolean) {
        val p = store(context)
        if (onboarded && !p.getBoolean(KEY_ONBOARDED, false)) p.edit().putBoolean(KEY_ONBOARDED, true).apply()
        if (enabled && p.getBoolean(KEY_ONBOARDED, false)) start()
    }

    /** The first-run screens (with the privacy page) were just finished: sending may begin. */
    fun onboardingDone(context: Context) {
        store(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
        if (enabled) start()
    }

    private fun start() {
        if (!prefs.getBoolean(KEY_INSTALL_DONE, false)) sendInstall()
        queueEvents()
        scope.launch { flush() }
    }

    /**
     * Settings > Share usage statistics. Installs and purchases are always sent (needed to check
     * purchases and stop fraud). Off by default where the ads consent form is required (EU/UK etc.).
     */
    fun usageOn(context: Context): Boolean =
        store(context).getBoolean(KEY_USAGE, !com.vikash.mp3player.monetization.Ads.privacyRequired(context))

    fun setUsageOn(context: Context, on: Boolean) {
        store(context).edit().putBoolean(KEY_USAGE, on).apply()
        if (!on) prefs.edit().remove(KEY_EVENTS).apply()
    }

    /** Counts a feature use. Sent in one batch on the next app start. */
    fun event(name: String) {
        if (!::prefs.isInitialized || !enabled || name !in ALLOWED || !usageOn(app)) return
        val now = prefs.getStringSet(KEY_EVENTS, emptySet()).orEmpty()
        if (name !in now) prefs.edit().putStringSet(KEY_EVENTS, now + name).apply()
    }

    /** Counts songs played; sends "played_10" the first time it reaches 10. */
    fun songPlayed() {
        if (!::prefs.isInitialized || !enabled) return
        val n = prefs.getInt(KEY_PLAYED, 0)
        if (n >= 10) return
        prefs.edit().putInt(KEY_PLAYED, n + 1).apply()
        if (n + 1 == 10) event("played_10")
    }

    /**
     * A purchase Play has completed. Only the token goes up: the server checks it with Google Play
     * and prices it from its own table (spec §2). Retries are safe, the token is unique.
     */
    fun purchase(kind: String, sku: String, purchaseToken: String) {
        if (!::prefs.isInitialized || !enabled) return
        val body = buildJsonObject {
            put("product", PRODUCT)
            put("install_id", installId(app))
            put("sku", sku)
            put("purchase_token", purchaseToken)
            put("kind", kind)
        }
        enqueue("/v1/public/purchases", body)
        // Before onboarding nothing is sent; the queue waits for it.
        if (prefs.getBoolean(KEY_ONBOARDED, false)) scope.launch { flush() }
    }

    // ------------------------------------------------------------------ queue

    /** Moves the counters gathered since the last start into the send queue as one batch. */
    private fun queueEvents() {
        val events = prefs.getStringSet(KEY_EVENTS, emptySet()).orEmpty().filter { it in ALLOWED }
        prefs.edit().remove(KEY_EVENTS).apply()
        if (events.isEmpty() || !usageOn(app)) return
        enqueue("/v1/public/events", buildJsonObject {
            put("product", PRODUCT)
            put("install_id", installId(app))
            put("events", JsonArray(events.take(20).map { JsonPrimitive(it) }))
        })
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
                    if (prefs.getBoolean(KEY_INSTALL_DONE, false)) return
                    prefs.edit().putBoolean(KEY_INSTALL_DONE, true).apply()
                    enqueue("/v1/public/installs", buildJsonObject {
                        put("product", PRODUCT)
                        put("install_id", installId(app))
                        put("referrer", referrer)
                        put("app_version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        put("country", Locale.getDefault().country)
                    })
                    scope.launch { flush() }
                }

                override fun onInstallReferrerServiceDisconnected() {}
            })
        }
    }

    private fun enqueue(path: String, body: JsonObject) {
        val item = buildJsonObject {
            put("path", path)
            put("body", body)
            put("first", System.currentTimeMillis())
            put("tries", 0)
            put("next", 0L)
        }.toString()
        synchronized(this) {
            val q = prefs.getStringSet(KEY_QUEUE, emptySet()).orEmpty()
            prefs.edit().putStringSet(KEY_QUEUE, (q + item).toList().takeLast(50).toSet()).apply()
        }
    }

    /** Sends what is due. 2xx done; 400/422 dropped; anything else (409, 429, 5xx, no answer) waits and retries. */
    private suspend fun flush() = lock.withLock {
        val now = System.currentTimeMillis()
        val queue = prefs.getStringSet(KEY_QUEUE, emptySet()).orEmpty()
        val keep = mutableSetOf<String>()
        val handled = mutableSetOf<String>()
        for (raw in queue) {
            handled += raw
            val item = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue
            val first = item["first"]?.jsonPrimitive?.long ?: now
            val tries = item["tries"]?.jsonPrimitive?.long ?: 0L
            val next = item["next"]?.jsonPrimitive?.long ?: 0L
            if (now - first > MAX_AGE) continue
            if (now < next) { keep += raw; continue }
            val path = item["path"]?.jsonPrimitive?.content ?: continue
            val code = post(path, item["body"].toString())
            when {
                code in 200..299 || code == 400 || code == 422 -> Unit
                else -> {
                    val wait = when (tries) { 0L -> HOUR; 1L -> 6 * HOUR; else -> 24 * HOUR }
                    keep += buildJsonObject {
                        item.forEach { (k, v) -> if (k != "tries" && k != "next") put(k, v) }
                        put("tries", tries + 1)
                        put("next", now + wait)
                    }.toString()
                }
            }
        }
        synchronized(this) {
            // Items queued while this ran are kept as they are.
            val current = prefs.getStringSet(KEY_QUEUE, emptySet()).orEmpty()
            prefs.edit().putStringSet(KEY_QUEUE, (current - handled) + keep).apply()
        }
    }

    /** HTTP status of the call, or -1 when it never reached the server. */
    private fun post(path: String, body: String): Int = runCatching {
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
