package com.vikash.mp3player.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Re-encodes part of a song to AAC (.m4a), with optional fade in and fade out. Used by the
 * ringtone maker when the user wants fades, or when the song's format can't be cut losslessly
 * (FLAC, OGG, WAV...). Works in two passes through a temporary PCM file, so long clips don't
 * need much memory.
 */
internal object ClipEncoder {

    private const val TIMEOUT_US = 10_000L
    private const val BIT_RATE = 160_000

    suspend fun encode(
        context: Context,
        source: Uri,
        startUs: Long,
        endUs: Long,
        fadeInMs: Long,
        fadeOutMs: Long,
        out: File,
        onProgress: (Float) -> Unit,
    ) {
        val pcm = File(context.cacheDir, "clip-${System.currentTimeMillis()}.pcm")
        try {
            val (rate, channels) = decode(context, source, startUs, endUs, pcm) { onProgress(it * 0.5f) }
            encodeAac(pcm, rate, channels, fadeInMs, fadeOutMs, out) { onProgress(0.5f + it * 0.5f) }
        } finally {
            pcm.delete()
        }
    }

    /** Decodes [startUs, endUs] to 16-bit PCM. Returns sample rate and channel count. */
    private suspend fun decode(context: Context, source: Uri, startUs: Long, endUs: Long, pcm: File, onProgress: (Float) -> Unit): Pair<Int, Int> {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val span = (endUs - startUs).coerceAtLeast(1)
            BufferedOutputStream(pcm.outputStream()).use { sink ->
                var scratch = ByteArray(0)
                while (true) {
                    coroutineContext.ensureActive()
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0 || extractor.sampleTime > endUs) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                        o >= 0 -> {
                            if (info.size > 0) {
                                val buf = codec.getOutputBuffer(o)!!
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                var bytes = ByteArray(info.size).also { buf.get(it) }
                                if (floatPcm) bytes = floatToShort(bytes)
                                val frameBytes = 2 * channels
                                val frames = bytes.size / frameBytes
                                // Drop what falls outside the clip (decoding starts at the sync point before it).
                                val firstUs = info.presentationTimeUs
                                val skip = if (firstUs < startUs) ((startUs - firstUs) * rate / 1_000_000L).toInt().coerceIn(0, frames) else 0
                                val lastFrameUs = { f: Int -> firstUs + f * 1_000_000L / rate }
                                var keep = frames
                                while (keep > skip && lastFrameUs(keep - 1) >= endUs) keep--
                                if (keep > skip) {
                                    val len = (keep - skip) * frameBytes
                                    if (scratch.size < len) scratch = ByteArray(len)
                                    System.arraycopy(bytes, skip * frameBytes, scratch, 0, len)
                                    sink.write(scratch, 0, len)
                                }
                                onProgress(((firstUs - startUs).toFloat() / span).coerceIn(0f, 1f))
                            }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
            }
            return rate to channels
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    private fun floatToShort(bytes: ByteArray): ByteArray {
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val out = ByteBuffer.allocate(floats.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
        while (floats.hasRemaining()) out.putShort((floats.get().coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        return out.array()
    }

    private suspend fun encodeAac(pcm: File, rate: Int, channels: Int, fadeInMs: Long, fadeOutMs: Long, out: File, onProgress: (Float) -> Unit) {
        val frameBytes = 2 * channels
        val totalFrames = pcm.length() / frameBytes
        require(totalFrames > 0) { "Empty clip" }
        val fadeInFrames = fadeInMs * rate / 1000
        val fadeOutFrames = fadeOutMs * rate / 1000
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrack = -1
        var muxerStarted = false
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val info = MediaCodec.BufferInfo()
            var framesFed = 0L
            var inputDone = false
            BufferedInputStream(pcm.inputStream()).use { input ->
                var chunk = ByteArray(0)
                while (true) {
                    coroutineContext.ensureActive()
                    if (!inputDone) {
                        val i = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (i >= 0) {
                            val buf = encoder.getInputBuffer(i)!!
                            buf.clear()
                            val want = (buf.capacity() / frameBytes) * frameBytes
                            if (chunk.size < want) chunk = ByteArray(want)
                            var got = 0
                            while (got < want) {
                                val r = input.read(chunk, got, want - got)
                                if (r < 0) break
                                got += r
                            }
                            got -= got % frameBytes
                            if (got <= 0) {
                                encoder.queueInputBuffer(i, 0, 0, framesFed * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                applyFade(chunk, got, channels, framesFed, totalFrames, fadeInFrames, fadeOutFrames)
                                buf.put(chunk, 0, got)
                                encoder.queueInputBuffer(i, 0, got, framesFed * 1_000_000L / rate, 0)
                                framesFed += got / frameBytes
                                onProgress(framesFed.toFloat() / totalFrames)
                            }
                        }
                    }
                    val o = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            muxerTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        o >= 0 -> {
                            val buf = encoder.getOutputBuffer(o)!!
                            val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (!isConfig && info.size > 0 && muxerStarted) {
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                muxer.writeSampleData(muxerTrack, buf, info)
                            }
                            encoder.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
            }
            if (muxerStarted) muxer.stop()
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { muxer.release() }
        }
    }

    /** Scales little-endian 16-bit samples by a linear ramp at the start and end of the clip. */
    private fun applyFade(bytes: ByteArray, len: Int, channels: Int, firstFrame: Long, total: Long, fadeIn: Long, fadeOut: Long) {
        if (fadeIn <= 0 && fadeOut <= 0) return
        val frameBytes = 2 * channels
        val frames = len / frameBytes
        for (f in 0 until frames) {
            val pos = firstFrame + f
            var gain = 1f
            if (fadeIn > 0 && pos < fadeIn) gain = minOf(gain, pos.toFloat() / fadeIn)
            val left = total - pos
            if (fadeOut > 0 && left < fadeOut) gain = minOf(gain, left.toFloat() / fadeOut)
            if (gain >= 1f) continue
            for (c in 0 until channels) {
                val at = f * frameBytes + c * 2
                val sample = ((bytes[at + 1].toInt() shl 8) or (bytes[at].toInt() and 0xFF)).toShort()
                val scaled = (sample * gain).toInt()
                bytes[at] = (scaled and 0xFF).toByte()
                bytes[at + 1] = ((scaled shr 8) and 0xFF).toByte()
            }
        }
    }
}
