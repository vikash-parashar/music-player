package com.vikash.mp3player.data

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

/** Where a finished clip can be used. One clip can be all three at once. */
enum class RingtoneTarget(val label: String, val type: Int, val column: String, val folder: String, val what: String) {
    Call("Call ringtone", RingtoneManager.TYPE_RINGTONE, MediaStore.Audio.AudioColumns.IS_RINGTONE,
        Environment.DIRECTORY_RINGTONES, "call ringtone"),
    Notification("Notification (SMS)", RingtoneManager.TYPE_NOTIFICATION, MediaStore.Audio.AudioColumns.IS_NOTIFICATION,
        Environment.DIRECTORY_NOTIFICATIONS, "notification sound"),
    Alarm("Alarm", RingtoneManager.TYPE_ALARM, MediaStore.Audio.AudioColumns.IS_ALARM,
        Environment.DIRECTORY_ALARMS, "alarm sound"),
}

/** Thrown for files we can't cut without re-encoding (FLAC, OGG, WAV...). The message is shown as is. */
class UnsupportedClipFormat(message: String) : Exception(message)

/**
 * Cuts a section of a song without re-encoding, so quality is untouched and it is fast:
 * AAC (M4A/MP4/AAC) samples are copied into a new .m4a with MediaMuxer, and MP3 frames
 * are copied byte for byte (MediaMuxer can't write MP3).
 */
class RingtoneMaker(private val context: Context) {

    /** Coarse peak levels (0..1) for the editor's waveform. [onPartial] gets the bars filled so far. */
    suspend fun waveform(uri: Uri, bars: Int, onPartial: (FloatArray) -> Unit = {}): FloatArray =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(context, uri, null)
                val track = audioTrack(extractor)
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                require(durationUs > 0)
                val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
                decoder.configure(format, null, null, 0)
                decoder.start()

                val peaks = FloatArray(bars)
                val info = MediaCodec.BufferInfo()
                var pcmFloat = false
                var inputDone = false
                var lastPartial = 0
                while (true) {
                    coroutineContext.ensureActive()
                    if (!inputDone) {
                        val i = decoder.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = extractor.readSampleData(decoder.getInputBuffer(i)!!, 0)
                            if (n < 0) {
                                decoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val o = decoder.dequeueOutputBuffer(info, 10_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = decoder.outputFormat
                        pcmFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    } else if (o >= 0) {
                        if (info.size > 0) {
                            val buf = decoder.getOutputBuffer(o)!!.apply {
                                position(info.offset); limit(info.offset + info.size)
                            }.slice().order(ByteOrder.nativeOrder())
                            val bar = (info.presentationTimeUs * bars / durationUs).toInt().coerceIn(0, bars - 1)
                            var peak = peaks[bar]
                            // Every 8th sample is plenty for a picture and keeps decoding fast.
                            if (pcmFloat) {
                                val fb = buf.asFloatBuffer()
                                var k = 0
                                while (k < fb.limit()) { peak = max(peak, abs(fb.get(k))); k += 8 }
                            } else {
                                val sb = buf.asShortBuffer()
                                var k = 0
                                while (k < sb.limit()) { peak = max(peak, abs(sb.get(k).toInt()) / 32768f); k += 8 }
                            }
                            peaks[bar] = peak
                            if (bar - lastPartial >= bars / 10) { lastPartial = bar; onPartial(peaks.copyOf()) }
                        }
                        decoder.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
                val top = peaks.max().coerceAtLeast(0.01f)
                FloatArray(bars) { (peaks[it] / top).coerceIn(0.04f, 1f) }
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                extractor.release()
            }
        }

    /**
     * Cuts [startMs, endMs] of [song], saves it to MediaStore (Ringtones/, Notifications/ or
     * Alarms/), and returns its content URI. [onProgress] goes from 0 to 1.
     */
    suspend fun makeClip(
        song: Song,
        startMs: Long,
        endMs: Long,
        targets: Set<RingtoneTarget>,
        onProgress: (Float) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val mime = MediaExtractor().run {
            try {
                setDataSource(context, song.uri, null)
                getTrackFormat(audioTrack(this)).getString(MediaFormat.KEY_MIME).orEmpty()
            } catch (e: UnsupportedClipFormat) {
                throw e
            } catch (e: Exception) {
                throw UnsupportedClipFormat("Ampli couldn't read this song. It may have been moved or deleted.")
            } finally {
                release()
            }
        }
        val title = "${song.title} (Ampli)"
        val temp = File(context.cacheDir, "ringtone-${System.currentTimeMillis()}.tmp")
        try {
            val (ext, outMime) = when (mime) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> {
                    cutAac(song.uri, startMs * 1000, endMs * 1000, temp, onProgress)
                    "m4a" to "audio/mp4"
                }
                MediaFormat.MIMETYPE_AUDIO_MPEG -> {
                    temp.outputStream().buffered().use { out ->
                        out.write(id3Tag(title, song.artist))
                        cutMp3(song.uri, startMs * 1000, endMs * 1000, out, onProgress)
                    }
                    "mp3" to "audio/mpeg"
                }
                else -> throw UnsupportedClipFormat(
                    "Ringtones can be made from MP3 and M4A/AAC songs. " +
                        "This one is ${mime.substringAfter('/').uppercase().ifBlank { "an unknown format" }}."
                )
            }
            onProgress(1f)
            save(temp, "${safeName(title)}.$ext", outMime, title, song.artist, targets)
        } finally {
            temp.delete()
        }
    }

    /** True when Android lets Ampli change the default sounds ("Modify system settings"). */
    fun canSetDefaults(): Boolean = Settings.System.canWrite(context)

    /** False if the phone refused (some makers lock the default sounds). */
    fun setAsDefault(uri: Uri, targets: Set<RingtoneTarget>): Boolean = runCatching {
        targets.forEach { RingtoneManager.setActualDefaultRingtoneUri(context, it.type, uri) }
    }.isSuccess

    private fun audioTrack(extractor: MediaExtractor): Int =
        (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw UnsupportedClipFormat("This file has no audio Ampli can cut.")

    private suspend fun cutAac(uri: Uri, startUs: Long, endUs: Long, out: File, onProgress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = audioTrack(extractor)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val m = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            val outTrack = m.addTrack(format)
            m.start()
            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
                max(format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), 64 * 1024) else 1024 * 1024
            val buffer = ByteBuffer.allocate(size)
            val info = MediaCodec.BufferInfo()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var firstUs = -1L
            while (true) {
                coroutineContext.ensureActive()
                val n = extractor.readSampleData(buffer, 0)
                if (n < 0) break
                val t = extractor.sampleTime
                if (t > endUs) break
                if (t >= startUs || firstUs < 0) {
                    if (firstUs < 0) firstUs = t
                    val key = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, n, t - firstUs, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    m.writeSampleData(outTrack, buffer, info)
                    onProgress(((t - startUs).toFloat() / (endUs - startUs)).coerceIn(0f, 0.99f))
                }
                extractor.advance()
            }
            if (firstUs < 0) throw UnsupportedClipFormat("That part of the song is empty. Pick another section.")
            m.stop()
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    /** Copies whole MP3 frames that overlap [startUs, endUs]. Handles CBR and VBR alike. */
    private suspend fun cutMp3(uri: Uri, startUs: Long, endUs: Long, out: OutputStream, onProgress: (Float) -> Unit) {
        val raw = context.contentResolver.openInputStream(uri)
            ?: throw UnsupportedClipFormat("Ampli couldn't open this song.")
        val buffered = BufferedInputStream(raw, 64 * 1024)
        buffered.use {
            skipId3v2(buffered)
            val input = PushbackInputStream(buffered, 8192)
            val head = ByteArray(4)
            if (!input.readFully(head, 0, 4)) throw UnsupportedClipFormat("This MP3 file looks damaged.")
            var locked: Mp3Frame? = null
            var timeUs = 0L
            var wrote = 0
            var frames = 0
            while (true) {
                coroutineContext.ensureActive()
                val h = Mp3Frame.parse(head)
                if (h == null || (locked != null && !h.sameStream(locked))) {
                    // Lost sync (junk, tags): slide one byte and look again.
                    head.copyInto(head, 0, 1, 4)
                    val b = input.read()
                    if (b < 0) break
                    head[3] = b.toByte()
                    continue
                }
                val frame = ByteArray(h.length)
                head.copyInto(frame)
                if (!input.readFully(frame, 4, h.length - 4)) break
                val hasNext = input.readFully(head, 0, 4)
                if (locked == null) {
                    // Confirm the first frame by checking the next one starts right after it.
                    val next = if (hasNext) Mp3Frame.parse(head) else null
                    if (hasNext && (next == null || !next.sameStream(h))) {
                        // False sync inside junk: rewind and resume scanning one byte later.
                        input.unread(head)
                        input.unread(frame, 1, frame.size - 1)
                        if (!input.readFully(head, 0, 4)) break
                        continue
                    }
                    locked = h
                }
                frames++
                // The first frame is often a Xing/Info header with no sound; its counts would be wrong for the clip.
                val isInfoFrame = frames == 1 && frame.containsTag()
                if (!isInfoFrame) {
                    val frameUs = h.samples * 1_000_000L / h.sampleRate
                    if (timeUs + frameUs > startUs && timeUs < endUs) {
                        out.write(frame)
                        wrote++
                    }
                    timeUs += frameUs
                    if (timeUs >= startUs) onProgress(((timeUs - startUs).toFloat() / (endUs - startUs)).coerceIn(0f, 0.99f))
                    if (timeUs >= endUs) break
                }
                if (!hasNext) break
            }
            if (wrote == 0) throw UnsupportedClipFormat("That part of the song is empty. Pick another section.")
        }
    }

    private fun skipId3v2(input: BufferedInputStream) {
        input.mark(10)
        val h = ByteArray(10)
        if (!input.readFully(h, 0, 10) || h[0] != 'I'.code.toByte() || h[1] != 'D'.code.toByte() || h[2] != '3'.code.toByte()) {
            input.reset()
            return
        }
        var size = ((h[6].toInt() and 0x7F) shl 21) or ((h[7].toInt() and 0x7F) shl 14) or
            ((h[8].toInt() and 0x7F) shl 7) or (h[9].toInt() and 0x7F)
        if (h[5].toInt() and 0x10 != 0) size += 10 // footer
        var left = size.toLong()
        while (left > 0) {
            val s = input.skip(left)
            if (s <= 0) { if (input.read() < 0) return; left-- } else left -= s
        }
        skipId3v2(input) // some files carry more than one tag
    }

    /** Minimal ID3v2.3 tag so the clip shows its name in ringtone pickers. */
    private fun id3Tag(title: String, artist: String): ByteArray {
        fun frame(id: String, text: String): ByteArray {
            val body = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
            val n = body.size
            return id.toByteArray(Charsets.US_ASCII) +
                byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte(), 0, 0) + body
        }
        val frames = frame("TIT2", title) + if (artist.isNotBlank()) frame("TPE1", artist) else ByteArray(0)
        val s = frames.size
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
            ((s shr 21) and 0x7F).toByte(), ((s shr 14) and 0x7F).toByte(), ((s shr 7) and 0x7F).toByte(), (s and 0x7F).toByte()) + frames
    }

    private fun save(file: File, displayName: String, mime: String, title: String, artist: String, targets: Set<RingtoneTarget>): Uri {
        // "Save only" still marks it as a ringtone so it shows up in the phone's sound pickers.
        val flags = targets.ifEmpty { setOf(RingtoneTarget.Call) }
        val folder = flags.minBy { it.ordinal }.folder
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.AudioColumns.IS_MUSIC, 0)
            RingtoneTarget.entries.forEach { put(it.column, if (it in flags) 1 else 0) }
        }
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, "$folder/")
            values.put(MediaStore.Audio.Media.IS_PENDING, 1)
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: error("Couldn't save the ringtone. Is the storage full?")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("Couldn't save the ringtone. Is the storage full?")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        }
        // Android 9 and older: write the file ourselves (needs storage permission), then index it.
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(folder).apply { mkdirs() }
        var dest = File(dir, displayName)
        var n = 1
        while (dest.exists()) dest = File(dir, displayName.substringBeforeLast('.') + " ($n)." + displayName.substringAfterLast('.')).also { n++ }
        try {
            file.copyTo(dest)
        } catch (e: Exception) {
            throw IllegalStateException("Couldn't save to $folder. Allow storage access for Ampli and try again.")
        }
        values.put(MediaStore.Audio.Media.DATA, dest.absolutePath)
        values.put(MediaStore.Audio.Media.SIZE, dest.length())
        return resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't save the ringtone. Is the storage full?")
    }

    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
}

/** One MPEG audio frame header (MP3 is MPEG-1/2/2.5 Layer III; Layers I and II are handled too). */
private class Mp3Frame(val version: Int, val layer: Int, val sampleRate: Int, val length: Int, val samples: Int) {
    fun sameStream(o: Mp3Frame) = version == o.version && layer == o.layer && sampleRate == o.sampleRate

    companion object {
        private val RATES = intArrayOf(44100, 48000, 32000)
        private val V1L1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
        private val V1L2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
        private val V1L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
        private val V2L1 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
        private val V2L23 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

        fun parse(h: ByteArray): Mp3Frame? {
            val b0 = h[0].toInt() and 0xFF
            val b1 = h[1].toInt() and 0xFF
            val b2 = h[2].toInt() and 0xFF
            if (b0 != 0xFF || b1 and 0xE0 != 0xE0) return null
            val version = (b1 shr 3) and 3 // 0 = 2.5, 2 = 2, 3 = 1
            val layerBits = (b1 shr 1) and 3 // 1 = III, 2 = II, 3 = I
            val brIndex = (b2 shr 4) and 0xF
            val srIndex = (b2 shr 2) and 3
            if (version == 1 || layerBits == 0 || brIndex == 0 || brIndex == 15 || srIndex == 3) return null
            val layer = 4 - layerBits
            val v1 = version == 3
            val table = when {
                v1 && layer == 1 -> V1L1
                v1 && layer == 2 -> V1L2
                v1 -> V1L3
                layer == 1 -> V2L1
                else -> V2L23
            }
            val kbps = table[brIndex]
            val sampleRate = RATES[srIndex] / when (version) { 3 -> 1; 2 -> 2; else -> 4 }
            val padding = (b2 shr 1) and 1
            val bps = kbps * 1000
            val length = when {
                layer == 1 -> (12 * bps / sampleRate + padding) * 4
                layer == 3 && !v1 -> 72 * bps / sampleRate + padding
                else -> 144 * bps / sampleRate + padding
            }
            val samples = when {
                layer == 1 -> 384
                layer == 3 && !v1 -> 576
                else -> 1152
            }
            return if (length < 8) null else Mp3Frame(version, layer, sampleRate, length, samples)
        }
    }
}

private fun ByteArray.containsTag(): Boolean {
    val end = minOf(size - 4, 60)
    for (i in 4..end) {
        val s = String(this, i, 4, Charsets.US_ASCII)
        if (s == "Xing" || s == "Info" || s == "VBRI") return true
    }
    return false
}

/** Reads exactly [len] bytes; false if the stream ended first. */
private fun InputStream.readFully(b: ByteArray, off: Int, len: Int): Boolean {
    var done = 0
    while (done < len) {
        val n = read(b, off + done, len - done)
        if (n < 0) return false
        done += n
    }
    return true
}
