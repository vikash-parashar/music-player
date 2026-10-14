package com.vikash.mp3player.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.vikash.mp3player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

enum class FeedbackKind(val wire: String) { Bug("bug"), Feature("feature"), Support("support") }

/** One of the user's own reports, as the Ampli Owner server returns it. */
@Serializable
data class MyReport(
    val id: Long = 0,
    val ref: String = "",
    val kind: String = "",
    val title: String = "",
    val status: String = "",
    val reply: String? = null,
    val replies: List<ReportReply> = emptyList(),
) {
    /** The newest answer from the Ampli team, if any. */
    val latestReply: String? get() = replies.lastOrNull()?.body ?: reply
}

@Serializable
data class ReportReply(val body: String = "")

/** A feature other users asked for, with its vote count. */
@Serializable
data class FeatureIdea(val id: Long = 0, val title: String = "", val votes: Int = 0, val status: String = "")

/**
 * Help & feedback: sends bug reports, feature requests and support questions to the Ampli Owner
 * server. Until that server's address is set (OWNER_API_URL), everything goes by email instead.
 * Sends no personal data unless the user types it; the install id is a random, app-only UUID.
 */
class Feedback(private val context: Context) {

    sealed interface SendResult {
        data class Sent(val ref: String) : SendResult
        /** No connection: kept on the phone and sent the next time Help & feedback opens. */
        data object Queued : SendResult
        data object RateLimited : SendResult
        /** No server yet: open this email draft instead. */
        data class Email(val intent: Intent) : SendResult
        data class Failed(val message: String) : SendResult
    }

    private val prefs = context.getSharedPreferences("ampli_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val base = BuildConfig.OWNER_API_URL.trimEnd('/')

    val hasServer: Boolean get() = base.isNotEmpty()

    val installId: String
        get() = prefs.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_INSTALL, it).apply() }

    suspend fun send(
        kind: FeedbackKind,
        title: String,
        body: String,
        contact: String,
        orderId: String,
        isPro: Boolean,
        screenshot: Uri?,
        attachLogs: Boolean,
    ): SendResult = withContext(Dispatchers.IO) {
        val report = buildJsonObject {
            put("product", "mp3")
            put("kind", kind.wire)
            put("title", title.trim().take(120))
            put("body", body.trim().take(4000))
            if (contact.isNotBlank()) put("contact", contact.trim())
            put("install_id", installId)
            put("app_version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("os", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            put("locale", Locale.getDefault().toLanguageTag())
            put("pro", isPro)
            if (kind == FeedbackKind.Support && orderId.isNotBlank()) put("order_id", orderId.trim())
        }
        if (!hasServer) return@withContext SendResult.Email(emailIntent(kind, title, body, contact, orderId, screenshot))
        try {
            val (code, text) = request("POST", "$base/v1/public/reports", report.toString())
            when (code) {
                in 200..299 -> {
                    val obj = json.parseToJsonElement(text) as? JsonObject
                    val id = obj?.get("id")?.toString()?.trim('"')
                    val ref = obj?.get("ref")?.toString()?.trim('"').orEmpty()
                    if (id != null) {
                        screenshot?.let { runCatching { uploadImage(id, it) } }
                        if (attachLogs) runCatching { upload(id, "logs.txt", "text/plain", appLogs().toByteArray()) }
                    }
                    SendResult.Sent(ref.ifBlank { "#$id" })
                }
                429 -> SendResult.RateLimited
                else -> SendResult.Failed("HTTP $code")
            }
        } catch (_: IOException) {
            queue(report.toString())
            SendResult.Queued
        }
    }

    /** Sends reports saved while offline. Returns how many went out. */
    suspend fun flushQueue(): Int = withContext(Dispatchers.IO) {
        if (!hasServer) return@withContext 0
        val pending = prefs.getStringSet(KEY_QUEUE, emptySet()).orEmpty()
        var sent = 0
        val left = pending.filterNot { body ->
            val ok = runCatching { request("POST", "$base/v1/public/reports", body).first in 200..299 }.getOrDefault(false)
            if (ok) sent++
            ok
        }.toSet()
        prefs.edit().putStringSet(KEY_QUEUE, left).apply()
        sent
    }

    suspend fun myReports(): List<MyReport> = withContext(Dispatchers.IO) {
        val (code, text) = request("GET", "$base/v1/public/reports?install_id=${URLEncoder.encode(installId, "UTF-8")}", null)
        check(code in 200..299) { "HTTP $code" }
        decodeList<MyReport>(text, "reports")
    }

    suspend fun features(): List<FeatureIdea> = withContext(Dispatchers.IO) {
        val (code, text) = request("GET", "$base/v1/public/features?product=mp3", null)
        check(code in 200..299) { "HTTP $code" }
        decodeList<FeatureIdea>(text, "features").sortedByDescending { it.votes }
    }

    suspend fun vote(featureId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            request("POST", "$base/v1/public/features/$featureId/vote", buildJsonObject { put("install_id", installId) }.toString()).first in 200..299
        }.getOrDefault(false).also { ok -> if (ok) prefs.edit().putStringSet(KEY_VOTED, votedIds() + featureId.toString()).apply() }
    }

    fun votedIds(): Set<String> = prefs.getStringSet(KEY_VOTED, emptySet()).orEmpty()

    /** Accepts a bare JSON array or an object holding the array under [key]. */
    private inline fun <reified T> decodeList(text: String, key: String): List<T> {
        val root: JsonElement = json.parseToJsonElement(text)
        val array = root as? JsonArray ?: (root as? JsonObject)?.get(key) as? JsonArray ?: return emptyList()
        return array.mapNotNull { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() }
    }

    private fun queue(body: String) {
        val now = prefs.getStringSet(KEY_QUEUE, emptySet()).orEmpty()
        prefs.edit().putStringSet(KEY_QUEUE, (now + body).toList().takeLast(20).toSet()).apply()
    }

    private fun request(method: String, url: String, body: String?): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            body?.let { conn.outputStream.use { o -> o.write(it.toByteArray()) } }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    /** Screenshots are shrunk to a JPEG under 2 MB before upload. */
    private fun uploadImage(reportId: String, uri: Uri) {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
        var scale = 1f
        var bytes: ByteArray
        do {
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
            bytes = ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 80, out); out.toByteArray() }
            scale *= 0.7f
        } while (bytes.size > MAX_ATTACHMENT && scale > 0.1f)
        upload(reportId, "screenshot.jpg", "image/jpeg", bytes)
    }

    private fun upload(reportId: String, name: String, mime: String, data: ByteArray) {
        if (data.size > MAX_ATTACHMENT) return
        val boundary = "ampli" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL("$base/v1/public/reports/$reportId/attachments").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            conn.outputStream.use { o ->
                o.write("--$boundary\r\nContent-Disposition: form-data; name=\"install_id\"\r\n\r\n$installId\r\n".toByteArray())
                o.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\nContent-Type: $mime\r\n\r\n".toByteArray())
                o.write(data)
                o.write("\r\n--$boundary--\r\n".toByteArray())
            }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    /** This app's own recent log lines (Android only lets an app read its own). */
    private fun appLogs(): String = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "500", "--pid=${android.os.Process.myPid()}"))
        p.inputStream.bufferedReader().use { it.readText() }.takeLast(MAX_ATTACHMENT / 2)
    }.getOrDefault("")

    private fun emailIntent(kind: FeedbackKind, title: String, body: String, contact: String, orderId: String, screenshot: Uri?): Intent {
        val subject = "[Ampli MP3] ${kind.name}: ${title.trim()}"
        val text = buildString {
            appendLine(body.trim())
            appendLine()
            appendLine("---")
            if (contact.isNotBlank()) appendLine("Reply to: ${contact.trim()}")
            if (orderId.isNotBlank()) appendLine("Play order: ${orderId.trim()}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            appendLine("Ref: ${installId.take(8)}")
        }
        val to = BuildConfig.SUPPORT_EMAIL.takeIf { it.isNotBlank() }?.let { arrayOf(it) } ?: emptyArray()
        val intent = if (screenshot != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, screenshot)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        }
        return intent.apply {
            putExtra(Intent.EXTRA_EMAIL, to)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    private companion object {
        const val KEY_INSTALL = "install_id"
        const val KEY_QUEUE = "feedback_queue"
        const val KEY_VOTED = "feature_votes"
        const val MAX_ATTACHMENT = 2 * 1024 * 1024
    }
}
