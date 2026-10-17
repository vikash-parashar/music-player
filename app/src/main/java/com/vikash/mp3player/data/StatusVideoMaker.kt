package com.vikash.mp3player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * Makes a 9:16 "status" video (WhatsApp / Instagram) of up to 30 seconds of a song: a spinning
 * record with the song's title, the synced lyric line of the moment (when the user has lyrics),
 * and a small "Made with Ampli" mark on free exports.
 *
 * 1. The audio is cut and re-encoded to AAC with [ClipEncoder] (short fades).
 * 2. The picture is drawn with android.graphics on the input Surface of an H.264 encoder.
 * 3. Both go into one MP4 with MediaMuxer, in cacheDir/status/ (older videos there are deleted).
 */
internal object StatusVideoMaker {

    const val MAX_CLIP_MS = 30_000L
    const val MIN_CLIP_MS = 3_000L
    private const val FPS = 30
    private const val TIMEOUT_US = 10_000L
    private const val FADE_IN_MS = 300L
    private const val FADE_OUT_MS = 700L

    enum class Quality(val width: Int, val height: Int, val bitRate: Int) {
        HD720(720, 1280, 4_000_000),
        HD1080(1080, 1920, 8_000_000),
    }

    /** What goes on screen. [watermark] is the "Made with Ampli" text, or null for none. */
    class Look(
        val title: String,
        val artist: String,
        val lyrics: List<LyricLine>?,
        val watermark: String?,
    )

    fun statusDir(context: Context) = File(context.cacheDir, "status")

    /**
     * Builds the video and returns the MP4 file. [onProgress] goes from 0 to 1. If the phone's
     * encoder refuses 1080p, it is made at 720p instead.
     */
    suspend fun make(
        context: Context,
        song: Song,
        startMs: Long,
        endMs: Long,
        quality: Quality,
        look: Look,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.Default) {
        val dir = statusDir(context).apply { mkdirs() }
        // One video at a time is plenty; earlier ones were already shared (or abandoned).
        dir.listFiles()?.forEach { it.delete() }
        val stamp = System.currentTimeMillis()
        val audio = File(dir, "audio-$stamp.m4a")
        val out = File(dir, "ampli-status-$stamp.mp4")
        val end = endMs.coerceAtMost(startMs + MAX_CLIP_MS)
        try {
            withContext(Dispatchers.IO) {
                ClipEncoder.encode(
                    context, song.uri, startMs * 1000, end * 1000, FADE_IN_MS, FADE_OUT_MS, audio,
                ) { onProgress(it * 0.25f) }
            }
            try {
                encodeVideo(out, audio, startMs, end, quality, look) { onProgress(0.25f + it * 0.75f) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quality == Quality.HD720) throw e
                // Some encoders can't do 1080x1920: fall back to 720p.
                out.delete()
                encodeVideo(out, audio, startMs, end, Quality.HD720, look) { onProgress(0.25f + it * 0.75f) }
            }
            onProgress(1f)
            out
        } catch (e: Throwable) {
            out.delete()
            throw e
        } finally {
            audio.delete()
        }
    }

    private suspend fun encodeVideo(
        out: File,
        audio: File,
        startMs: Long,
        endMs: Long,
        quality: Quality,
        look: Look,
        onProgress: (Float) -> Unit,
    ) {
        val w = quality.width
        val h = quality.height
        val frames = ((endMs - startMs) * FPS / 1000).toInt().coerceAtLeast(1)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, quality.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val extractor = MediaExtractor()
        var encoder: MediaCodec? = null
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val painter = FramePainter(w, h, look)
        try {
            extractor.setDataSource(audio.absolutePath)
            val audioTrack = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(audioTrack)
            val audioFormat = extractor.getTrackFormat(audioTrack)

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder = codec
            val caps = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
            require(caps == null || caps.isSizeSupported(w, h)) { "Encoder can't do ${w}x$h" }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = codec.createInputSurface()
            surface = input
            codec.start()
            val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux

            var videoTrack = -1
            var muxAudioTrack = -1
            val info = MediaCodec.BufferInfo()
            // The Surface stamps each frame with the time it was posted; we rewrite those to
            // frame index / FPS. postUs[i] is taken just before frame i is posted, so the frame
            // with encoder time t is the last i with postUs[i] <= t (works with B-frames too).
            val postUs = LongArray(frames)
            var posted = 0
            var written = 0

            fun frameFor(ptsUs: Long): Int {
                // Clock mismatch (never expected): fall back to output order.
                if (posted == 0 || ptsUs < postUs[0] || ptsUs > postUs[posted - 1] + 5_000_000L) return written
                var lo = 0
                var hi = posted - 1
                var found = 0
                while (lo <= hi) {
                    val mid = (lo + hi) ushr 1
                    if (postUs[mid] <= ptsUs) { found = mid; lo = mid + 1 } else hi = mid - 1
                }
                return found
            }

            /** Moves finished frames to the muxer. Returns true at end of stream. */
            fun drain(waitUs: Long): Boolean {
                while (true) {
                    val o = codec.dequeueOutputBuffer(info, waitUs)
                    when {
                        o == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "Format changed twice" }
                            videoTrack = mux.addTrack(codec.outputFormat)
                            muxAudioTrack = mux.addTrack(audioFormat)
                            mux.start()
                            muxerStarted = true
                        }
                        o >= 0 -> {
                            val buf = codec.getOutputBuffer(o)
                            val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (buf != null && !isConfig && info.size > 0 && muxerStarted) {
                                info.presentationTimeUs = frameFor(info.presentationTimeUs) * 1_000_000L / FPS
                                written++
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                mux.writeSampleData(videoTrack, buf, info)
                            }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                        }
                    }
                }
            }

            // Frames are posted at up to twice real time: encoders spread the bit rate by the
            // Surface's timestamps, so posting far faster than that would starve each frame.
            val pace = 1000L / FPS / 2
            val began = System.nanoTime()
            for (i in 0 until frames) {
                coroutineContext.ensureActive()
                val ahead = began / 1_000_000 + i * pace - System.nanoTime() / 1_000_000
                if (ahead > 0) delay(ahead)
                val canvas = lockCanvas(input)
                try {
                    painter.draw(canvas, i, startMs + i * 1000L / FPS)
                } finally {
                    postUs[i] = System.nanoTime() / 1000
                    posted = i + 1
                    input.unlockCanvasAndPost(canvas)
                }
                drain(0)
                onProgress(0.9f * (i + 1) / frames)
            }
            codec.signalEndOfInputStream()
            var done = false
            var waits = 0
            while (!done) {
                coroutineContext.ensureActive()
                done = drain(TIMEOUT_US)
                if (++waits > 3000) error("Video encoder stalled")
            }
            check(muxerStarted) { "No video was produced" }

            // Audio after video; MediaMuxer accepts tracks written one after the other.
            val maxSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val sample = ByteBuffer.allocateDirect(maxSize.coerceAtLeast(256 * 1024))
            val audioInfo = MediaCodec.BufferInfo()
            val videoEndUs = frames * 1_000_000L / FPS
            while (true) {
                coroutineContext.ensureActive()
                sample.clear()
                val n = extractor.readSampleData(sample, 0)
                if (n < 0) break
                val t = extractor.sampleTime
                if (t > videoEndUs) break
                val key = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                audioInfo.set(0, n, t, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(muxAudioTrack, sample, audioInfo)
                extractor.advance()
            }
            onProgress(1f)
            mux.stop()
            muxerStarted = false
        } finally {
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { surface?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            painter.recycle()
        }
    }

    /** Hardware canvas is much faster; some devices refuse it on an encoder surface. */
    private fun lockCanvas(surface: Surface): Canvas =
        runCatching { surface.lockHardwareCanvas() }.getOrNull() ?: surface.lockCanvas(null)

    /** Draws one frame. Static parts are painted once into bitmaps. */
    private class FramePainter(private val w: Int, private val h: Int, private val look: Look) {
        private val s = w / 720f
        private val radius = w * 0.36f
        private val cx = w / 2f
        private val cy = h * 0.40f
        private val background: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        private val record: Bitmap = Bitmap.createBitmap((radius * 2).toInt(), (radius * 2).toInt(), Bitmap.Config.ARGB_8888)
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f * s
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(8f * s, 0f, 2f * s, 0x99000000.toInt())
        }
        private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 26f * s
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROSE }
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                intArrayOf(0x00FFFFFF, 0x22FFFFFF, 0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        }
        private val timed = look.lyrics?.filter { it.timeMs != null && it.text.isNotBlank() }.orEmpty()
        private var lyricText: String? = null
        private var lyricLayout: StaticLayout? = null

        init {
            paintBackground(Canvas(background))
            paintRecord(Canvas(record))
        }

        private fun paintBackground(c: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF3B0A1E.toInt(), 0xFF1C1820.toInt(), NIGHT), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = RadialGradient(cx, cy, radius * 1.6f, intArrayOf(0x66E11D48, 0x00E11D48), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, radius * 1.6f, p)

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 46f * s
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BLUSH
                textSize = 30f * s
                textAlign = Paint.Align.CENTER
            }
            val maxW = w - 96f * s
            val titleY = cy + radius + 100f * s
            c.drawText(TextUtils.ellipsize(look.title, titlePaint, maxW, TextUtils.TruncateAt.END).toString(), cx, titleY, titlePaint)
            if (look.artist.isNotBlank()) {
                c.drawText(TextUtils.ellipsize(look.artist, artistPaint, maxW, TextUtils.TruncateAt.END).toString(), cx, titleY + 48f * s, artistPaint)
            }
        }

        private fun paintRecord(c: Canvas) {
            val r = radius
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = 0xFF0B0B0E.toInt()
            c.drawCircle(r, r, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f * s
            var g = r * 0.97f
            var k = 0
            while (g > r * 0.38f) {
                p.color = if (k % 3 == 0) 0x30FFFFFF else 0x16FFFFFF
                c.drawCircle(r, r, g, p)
                g -= 5f * s
                k++
            }
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, r * 0.66f, 0f, r * 1.34f, ROSE, ROSE_DEEP, Shader.TileMode.CLAMP)
            c.drawCircle(r, r, r * 0.34f, p)
            p.shader = null
            // Label text, so the turning is easy to see.
            val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 22f * s
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            c.drawText(TextUtils.ellipsize(look.title, label, r * 0.5f, TextUtils.TruncateAt.END).toString(), r, r - r * 0.12f, label)
            p.color = NIGHT
            c.drawCircle(r, r, r * 0.05f, p)
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(r, r + r * 0.2f, r * 0.025f, p)
        }

        private fun lineAt(ms: Long): String? {
            if (timed.isEmpty()) return null
            var line: String? = null
            for (l in timed) {
                val t = l.timeMs ?: continue
                if (t <= ms) line = l.text else break
            }
            return line
        }

        fun draw(c: Canvas, frame: Int, songMs: Long) {
            c.drawBitmap(background, 0f, 0f, null)
            val angle = (frame * 120f / FPS) % 360f
            c.save()
            c.rotate(angle, cx, cy)
            c.drawBitmap(record, cx - radius, cy - radius, bitmapPaint)
            c.restore()
            c.drawCircle(cx, cy, radius, sheenPaint)

            val text = lineAt(songMs)
            if (text != lyricText) {
                lyricText = text
                lyricLayout = text?.let {
                    StaticLayout.Builder.obtain(it, 0, it.length, lyricPaint, (w - 128f * s).toInt())
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setMaxLines(3)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()
                }
            }
            lyricLayout?.let { layout ->
                c.save()
                c.translate(64f * s, h * 0.75f)
                layout.draw(c)
                c.restore()
            }

            look.watermark?.let { mark ->
                val x = w - 32f * s
                val y = h - 40f * s
                c.drawText(mark, x, y, markPaint)
                c.drawCircle(x - markPaint.measureText(mark) - 14f * s, y - 9f * s, 6f * s, dotPaint)
            }
        }

        fun recycle() {
            background.recycle()
            record.recycle()
        }
    }

    private val ROSE = 0xFFE11D48.toInt()
    private val ROSE_DEEP = 0xFFBE123C.toInt()
    private val NIGHT = 0xFF0F0D14.toInt()
    private val BLUSH = 0xFFFDA4AF.toInt()
}
