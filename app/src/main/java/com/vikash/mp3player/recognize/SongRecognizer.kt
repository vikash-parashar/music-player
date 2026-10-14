package com.vikash.mp3player.recognize

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.StringRes
import com.vikash.mp3player.BuildConfig
import com.vikash.mp3player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * "Name that song": records a few seconds through the mic and asks AudD which song it is.
 * Ampli only shows the name and links to official apps; nothing is downloaded.
 */
object SongRecognizer {

    data class Match(
        val title: String,
        val artist: String,
        val album: String?,
        val songLink: String?,
        val spotifyUrl: String?,
        val appleMusicUrl: String?,
    )

    sealed interface Result {
        data class Found(val match: Match) : Result
        data object NotFound : Result
        /** [messageRes] is a string resource, shown translated by the UI. */
        data class Failed(@StringRes val messageRes: Int) : Result
    }

    /** False in builds without the AudD key (added as a GitHub secret, never in the code). */
    val isConfigured: Boolean get() = BuildConfig.AUDD_API_TOKEN.isNotBlank()

    const val LISTEN_SECONDS = 8

    /** Records [LISTEN_SECONDS] seconds. Needs the RECORD_AUDIO permission. */
    suspend fun record(context: Context): File? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "recognize.m4a").apply { delete() }
        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44_100)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            delay(LISTEN_SECONDS * 1000L)
            recorder.stop()
            file.takeIf { it.length() > 0 }
        } catch (e: Exception) {
            null
        } finally {
            recorder.release()
        }
    }

    suspend fun identify(sample: File): Result = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.Failed(R.string.recognize_coming_soon_short)
        val boundary = "----ampli" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL("https://api.audd.io/").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                field("api_token", BuildConfig.AUDD_API_TOKEN)
                field("return", "apple_music,spotify")
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n")
                sample.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            if (conn.responseCode !in 200..299) return@withContext Result.Failed(R.string.recognize_service_error)
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } catch (e: Exception) {
            Result.Failed(R.string.recognize_no_internet)
        } finally {
            conn.disconnect()
            sample.delete()
        }
    }

    internal fun parse(body: String): Result {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return Result.Failed(R.string.recognize_bad_answer)
        if (root.str("status") != "success") return Result.Failed(R.string.recognize_not_working)
        val result = root["result"] as? JsonObject ?: return Result.NotFound
        val title = result.str("title") ?: return Result.NotFound
        return Result.Found(
            Match(
                title = title,
                artist = result.str("artist").orEmpty(),
                album = result.str("album"),
                songLink = result.str("song_link"),
                spotifyUrl = (result["spotify"] as? JsonObject)?.let { (it["external_urls"] as? JsonObject)?.str("spotify") },
                appleMusicUrl = (result["apple_music"] as? JsonObject)?.str("url"),
            )
        )
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
}
