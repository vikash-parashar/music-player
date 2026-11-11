package com.vikash.mp3player.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * The song's own photo as an old, worn sticker on the cassette's top-right corner (design A, picked by
 * Vikash on 12 Nov 2026 from https://claude.ai/artifact/XXugpxqduy99zfxy6CkwLX). Faded colours, rough
 * edges, rubbed-off patches, light scratches and a lifted bottom-right corner. The same song always
 * gets the same wear and tilt. Songs without a photo get no sticker.
 */
object CassetteSticker {
    /** Where the sticker sits, in the cassette picture's 640 x 402 units (top-left corner, side). */
    const val X = 452f
    const val Y = 4f
    const val SIDE = 140f

    /** How much of the writing strip the song name may use while a sticker covers its right end. */
    const val ROOM = 0.72f

    private const val C = 280 // sticker body in bitmap pixels
    private const val PAD = 10 // room for the shadow
    private const val FOLD = 34
    private val PAPER = 0xFFF4EFE2.toInt()

    fun seed(uri: String): Int = uri.hashCode()

    private fun angle(seed: Int) = 4f + abs(seed % 5)

    /** Draws [sticker] on [canvas], which is in picture units. */
    fun draw(canvas: Canvas, sticker: Bitmap, seed: Int) {
        val k = SIDE / C
        canvas.save()
        canvas.translate(X, Y)
        canvas.rotate(angle(seed))
        canvas.drawBitmap(sticker, null, RectF(-PAD * k, -PAD * k, (C + PAD) * k, (C + PAD) * k), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    private val cache = LruCache<String, Bitmap>(4)
    private val loading = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cached(uri: String): Bitmap? = if (uri.isEmpty()) null else cache.get(uri)

    /** Loads [uri]'s sticker; null when the song has no photo. */
    suspend fun load(context: Context, uri: String): Bitmap? {
        if (uri.isEmpty()) return null
        cache.get(uri)?.let { return it }
        return try {
            withTimeoutOrNull(5_000) {
                val request = ImageRequest.Builder(context).data(uri).size(400).allowHardware(false).build()
                val art = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                art?.let { withContext(Dispatchers.Default) { make(it, seed(uri)) } }
            }?.also { cache.put(uri, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** For the widget: starts loading [uri] in the background and calls [ready] once it is there. */
    fun request(context: Context, uri: String, ready: suspend () -> Unit) {
        if (uri.isEmpty() || cache.get(uri) != null) return
        synchronized(loading) { if (!loading.add(uri)) return }
        scope.launch {
            try {
                if (load(context.applicationContext, uri) != null) ready()
            } finally {
                synchronized(loading) { loading.remove(uri) }
            }
        }
    }

    private fun shape(rnd: java.util.Random): Path {
        val p = Path()
        val o = PAD.toFloat()
        fun j() = (rnd.nextFloat() - 0.5f) * 3f
        val pts = mutableListOf<Pair<Float, Float>>()
        var x = 0f
        while (x < C) { pts += x to j(); x += 10f }
        var y = 0f
        while (y < C - FOLD) { pts += C + j() to y; y += 10f }
        pts += C.toFloat() to (C - FOLD).toFloat()
        pts += (C - FOLD).toFloat() to C.toFloat()
        x = (C - FOLD).toFloat()
        while (x > 0f) { pts += x to C + j(); x -= 10f }
        y = C.toFloat()
        while (y > 0f) { pts += j() to y; y -= 10f }
        pts.forEachIndexed { i, (px, py) -> if (i == 0) p.moveTo(px + o, py + o) else p.lineTo(px + o, py + o) }
        p.close()
        return p
    }

    private fun make(art: Bitmap, seed: Int): Bitmap {
        val size = C + PAD * 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = java.util.Random(seed.toLong())
        val o = PAD.toFloat()
        val shape = shape(rnd)

        // Paper backing with a soft shadow.
        c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER; setShadowLayer(4f, 1.5f, 3f, 0x73000000) })

        c.save()
        c.clipPath(shape)
        // The photo, centre-cropped, sun-faded and less saturated.
        val side = minOf(art.width, art.height)
        val src = Rect((art.width - side) / 2, (art.height - side) / 2, (art.width + side) / 2, (art.height + side) / 2)
        val faded = ColorMatrix().apply { setSaturation(0.6f) }
        faded.postConcat(ColorMatrix(floatArrayOf(
            0.88f, 0f, 0f, 0f, 23f,
            0f, 0.86f, 0f, 0f, 20f,
            0f, 0f, 0.80f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f,
        )))
        val inset = 5f
        c.drawBitmap(art, src, RectF(o + inset, o + inset, o + C - inset, o + C - inset),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(faded) })

        // Patches where the print has rubbed off, mostly near the edges.
        val rub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
        repeat(5 + rnd.nextInt(3)) {
            val edge = rnd.nextInt(4)
            val t = rnd.nextFloat() * C
            val d = rnd.nextFloat() * 40f
            val (cx, cy) = when (edge) { 0 -> t to d; 1 -> C - d to t; 2 -> t to C - d; else -> d to t }
            val r = 6f + rnd.nextFloat() * 14f
            rub.alpha = 130 + rnd.nextInt(110)
            repeat(3) { c.drawCircle(o + cx + (rnd.nextFloat() - 0.5f) * r, o + cy + (rnd.nextFloat() - 0.5f) * r, r * (0.5f + rnd.nextFloat() * 0.5f), rub) }
        }
        // Paper grain.
        val dot = Paint()
        repeat(1400) {
            val light = rnd.nextBoolean()
            dot.color = if (light) 0xFFFFFFFF.toInt() else 0xFF3A2A1A.toInt()
            dot.alpha = if (light) 20 + rnd.nextInt(35) else 10 + rnd.nextInt(20)
            val px = o + rnd.nextFloat() * C
            val py = o + rnd.nextFloat() * C
            c.drawRect(px, py, px + 1.4f, py + 1.4f, dot)
        }
        // Warm sun-fade from the top left.
        c.drawRect(o, o, o + C, o + C, Paint().apply {
            shader = LinearGradient(o, o, o + C, o + C, 0x33FFF0D0, 0x00FFF0D0, Shader.TileMode.CLAMP)
        })
        // A few light scratches.
        val scratch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88FFFFFF.toInt(); strokeWidth = 0.9f; style = Paint.Style.STROKE }
        repeat(3) {
            val x0 = o + rnd.nextFloat() * C
            val y0 = o + rnd.nextFloat() * C
            c.drawLine(x0, y0, x0 + (rnd.nextFloat() - 0.5f) * C * 0.8f, y0 + (rnd.nextFloat() - 0.5f) * C * 0.5f, scratch)
        }
        c.restore()

        // Little bites out of the edge where the sticker has worn away.
        val bite = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        repeat(3) {
            val t = rnd.nextFloat() * (C - FOLD)
            val (bx, by) = if (rnd.nextBoolean()) t to 0f else 0f to t
            c.drawCircle(o + bx, o + by, 2.5f + rnd.nextFloat() * 3f, bite)
        }

        // The lifted corner, folded back.
        val fold = Path().apply {
            moveTo(o + C, o + C - FOLD)
            lineTo(o + C - FOLD, o + C)
            lineTo(o + C - FOLD * 0.78f, o + C - FOLD * 0.78f)
            close()
        }
        c.drawPath(fold, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8E0CC.toInt(); setShadowLayer(2f, -1f, -1f, 0x40000000) })
        c.drawPath(fold, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000; style = Paint.Style.STROKE; strokeWidth = 0.8f })
        return bmp
    }
}
