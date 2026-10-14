package com.vikash.mp3player.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * Ampli Convert: copies the sound out of a video the user owns into a music file, without
 * re-encoding, so it is fast and loses no quality. AAC sound becomes .m4a, MP3 sound stays .mp3,
 * Opus/Vorbis (WebM videos) become .webm audio. Saved to Music/Ampli Convert.
 */
object AudioExtractor {

    const val FOLDER = "Ampli Convert"

    sealed interface Result {
        data class Saved(val name: String) : Result
        data object NoSound : Result
        data object Unsupported : Result
        data class Failed(val message: String) : Result
    }

    private enum class Kind(val ext: String, val mime: String) {
        M4a("m4a", "audio/mp4"), Mp3("mp3", "audio/mpeg"), Webm("webm", "audio/webm")
    }

    suspend fun extract(context: Context, video: Uri, onProgress: (Float) -> Unit): Result = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, video, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext Result.NoSound
            val format = extractor.getTrackFormat(track)
            val kind = when (format.getString(MediaFormat.KEY_MIME)) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> Kind.M4a
                MediaFormat.MIMETYPE_AUDIO_MPEG -> Kind.Mp3
                MediaFormat.MIMETYPE_AUDIO_OPUS, MediaFormat.MIMETYPE_AUDIO_VORBIS -> Kind.Webm
                else -> return@withContext Result.Unsupported
            }
            extractor.selectTrack(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val name = baseName(context, video) + "." + kind.ext
            val target = createTarget(context, name, kind.mime)
            try {
                when (kind) {
                    Kind.Mp3 -> target.openStream(context).use { copyRaw(extractor, it, durationUs, onProgress) }
                    else -> {
                        val muxerFormat = if (kind == Kind.M4a) MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 else MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                        target.openMuxer(context, muxerFormat).let { (muxer, closeable) ->
                            try {
                                remux(extractor, format, muxer, durationUs, onProgress)
                            } finally {
                                runCatching { muxer.release() }
                                closeable?.close()
                            }
                        }
                    }
                }
                target.publish(context)
                Result.Saved(name)
            } catch (e: Exception) {
                target.discard(context)
                throw e
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            extractor.release()
        }
    }

    private suspend fun remux(extractor: MediaExtractor, format: MediaFormat, muxer: MediaMuxer, durationUs: Long, onProgress: (Float) -> Unit) {
        val out = muxer.addTrack(format)
        muxer.start()
        val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 1 shl 20
        val buffer = ByteBuffer.allocate(size.coerceAtLeast(64 * 1024))
        val info = MediaCodec.BufferInfo()
        while (true) {
            coroutineContext.ensureActive()
            val read = extractor.readSampleData(buffer, 0)
            if (read < 0) break
            info.set(0, read, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            muxer.writeSampleData(out, buffer, info)
            if (durationUs > 0) onProgress((extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f))
            extractor.advance()
        }
        muxer.stop()
    }

    /** MP3 frames carry their own headers, so writing them one after another makes a valid file. */
    private suspend fun copyRaw(extractor: MediaExtractor, out: OutputStream, durationUs: Long, onProgress: (Float) -> Unit) {
        val buffer = ByteBuffer.allocate(1 shl 20)
        val bytes = ByteArray(1 shl 20)
        while (true) {
            coroutineContext.ensureActive()
            val read = extractor.readSampleData(buffer, 0)
            if (read < 0) break
            buffer.get(bytes, 0, read)
            buffer.clear()
            out.write(bytes, 0, read)
            if (durationUs > 0) onProgress((extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f))
            extractor.advance()
        }
    }

    private fun baseName(context: Context, uri: Uri): String {
        val display = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Video"
        return display.substringBeforeLast('.').replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100).ifBlank { "Video" }
    }

    // ------------------------------------------------------------------ where the file goes

    private sealed interface Target {
        fun openStream(context: Context): OutputStream
        /** The muxer plus anything to close after it. */
        fun openMuxer(context: Context, format: Int): Pair<MediaMuxer, java.io.Closeable?>
        fun publish(context: Context)
        fun discard(context: Context)
    }

    /** Android 10+: a MediaStore entry that stays hidden until it is complete. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private class StoreTarget(val uri: Uri) : Target {
        override fun openStream(context: Context): OutputStream =
            requireNotNull(context.contentResolver.openOutputStream(uri, "w")) { "Can't write file" }

        override fun openMuxer(context: Context, format: Int): Pair<MediaMuxer, java.io.Closeable?> {
            val pfd = requireNotNull(context.contentResolver.openFileDescriptor(uri, "rw")) { "Can't write file" }
            return MediaMuxer(pfd.fileDescriptor, format) to pfd
        }

        override fun publish(context: Context) {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        }

        override fun discard(context: Context) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    /** Android 9 and older: a plain file in Music/Ampli Convert, then a media scan. */
    private class FileTarget(val file: File, val mime: String) : Target {
        override fun openStream(context: Context): OutputStream = FileOutputStream(file)
        override fun openMuxer(context: Context, format: Int): Pair<MediaMuxer, java.io.Closeable?> =
            MediaMuxer(file.absolutePath, format) to null

        override fun publish(context: Context) {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        }

        override fun discard(context: Context) {
            file.delete()
        }
    }

    private fun createTarget(context: Context, name: String, mime: String): Target {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = requireNotNull(
                context.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ) { "Can't create file" }
            return StoreTarget(uri)
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER).apply { mkdirs() }
        var file = File(dir, name)
        var n = 2
        while (file.exists()) file = File(dir, name.substringBeforeLast('.') + " ($n)." + name.substringAfterLast('.')).also { n++ }
        return FileTarget(file, mime)
    }
}
