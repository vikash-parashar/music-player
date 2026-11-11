package com.vikash.mp3player.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.vikash.mp3player.R

/**
 * The 14 Glass Cassette looks (Pro), approved by Vikash on 9 Nov 2026 from the preview at
 * https://claude.ai/artifact/61AEKysWe8P1U22HJSUkRa, plus 3 solid looks (Chrome Silver, Cream Vintage,
 * Matte White) he picked on 11 Nov 2026 from https://claude.ai/artifact/UUKrC8R5F7kn4ShhWDM498.
 *
 * Each look is drawn in two layers, 640x402: widget_glass_back_N (the glass under the tape) and
 * widget_glass_front_N (reel flanges, window, printing, screws, gloss and the blank white label).
 * The tape packs go between them, sized from how far the song has played: the left reel is full when
 * a song starts and winds onto the right reel until it ends (asked for by Vikash, 11 Nov 2026).
 * The hubs and the song name, written in the Kalam hand font in blue, green or red pen, go on top.
 * The same drawing is used by the widget, the player's Cassette style and Car mode.
 */
object GlassCassette {

    class Look(
        val name: String,
        val back: Int,
        val front: Int,
        /** Tint of the frame around the cassette and the keys, ARGB. */
        val shell: Long,
        val keyTint: Long,
        val hub: Hub,
        /** Index into [PENS] used when the person keeps the look's own pen. */
        val pen: Int,
        /**
         * For a solid shell, the tape window in picture units: the reels are only seen through it, so
         * a full reel must not peek out under the label.
         */
        val window: android.graphics.RectF? = null,
    )

    /** The still hub picture, and the spinning ProgressBars for it in widget_glass_cassette.xml. */
    enum class Hub(val still: Int, val spinLeft: Int, val spinRight: Int) {
        WHITE(R.drawable.widget_glass_hub_white, R.id.glass_hub_l_white, R.id.glass_hub_r_white),
        BLUE(R.drawable.widget_glass_hub_blue, R.id.glass_hub_l_blue, R.id.glass_hub_r_blue),
        GOLD(R.drawable.widget_glass_hub_gold, R.id.glass_hub_l_gold, R.id.glass_hub_r_gold),
    }

    /** Blue, green and red ball-pen ink. */
    val PENS = longArrayOf(0xFF1F4FBF, 0xFF16794A, 0xFFC4232F)
    private const val BLUE = 0
    private const val GREEN = 1
    private const val RED = 2

    private val SOLID_WINDOW get() = android.graphics.RectF(132f, 156f, 508f, 276f)

    val LOOKS = listOf(
        Look("Crystal Clear", R.drawable.widget_glass_back_1, R.drawable.widget_glass_front_1, 0x29FFFFFF, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Smoke Glass", R.drawable.widget_glass_back_2, R.drawable.widget_glass_front_2, 0x5914141A, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Ice Blue", R.drawable.widget_glass_back_3, R.drawable.widget_glass_front_3, 0x338FD3FF, 0xFFFFFFFF, Hub.WHITE, RED),
        Look("Aqua", R.drawable.widget_glass_back_4, R.drawable.widget_glass_front_4, 0x2E5EEAD4, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Mint", R.drawable.widget_glass_back_5, R.drawable.widget_glass_front_5, 0x3396E6B4, 0xFFFFFFFF, Hub.WHITE, RED),
        Look("Rose", R.drawable.widget_glass_back_6, R.drawable.widget_glass_front_6, 0x2EFF78A0, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Honey Amber", R.drawable.widget_glass_back_7, R.drawable.widget_glass_front_7, 0x33F6AA3C, 0xFFFFFFFF, Hub.WHITE, GREEN),
        Look("Violet", R.drawable.widget_glass_back_8, R.drawable.widget_glass_front_8, 0x33966EFF, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Ruby", R.drawable.widget_glass_back_9, R.drawable.widget_glass_front_9, 0x38C81E32, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Frosted", R.drawable.widget_glass_back_10, R.drawable.widget_glass_front_10, 0x55FFFFFF, 0xFFFFFFFF, Hub.WHITE, GREEN),
        Look("Night Glow", R.drawable.widget_glass_back_11, R.drawable.widget_glass_front_11, 0x73080A14, 0xFF8FD3FF, Hub.BLUE, BLUE),
        Look("Gold Edge", R.drawable.widget_glass_back_12, R.drawable.widget_glass_front_12, 0x2EE6B84F, 0xFFFFFFFF, Hub.GOLD, RED),
        Look("Rainbow", R.drawable.widget_glass_back_13, R.drawable.widget_glass_front_13, 0x26FFFFFF, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Sticker Mixtape", R.drawable.widget_glass_back_14, R.drawable.widget_glass_front_14, 0x29FFFFFF, 0xFFFFFFFF, Hub.WHITE, RED),
        Look("Chrome Silver", R.drawable.widget_glass_back_15, R.drawable.widget_glass_front_15, 0x7396A0AF, 0xFFFFFFFF, Hub.WHITE, RED, SOLID_WINDOW),
        Look("Cream Vintage", R.drawable.widget_glass_back_16, R.drawable.widget_glass_front_16, 0x8CEFE4CC, 0xFF4A2F1A, Hub.WHITE, BLUE, SOLID_WINDOW),
        Look("Matte White", R.drawable.widget_glass_back_17, R.drawable.widget_glass_front_17, 0x8CFFFFFF, 0xFF111111, Hub.WHITE, BLUE, SOLID_WINDOW),
    )

    // The pictures are 640x402. widget_glass_cassette.xml places the writing (380x74, from 150,42)
    // and the hubs (60x60, centred on 200,216 and 440,216) with layout weights in these units.
    const val BODY_W = 640f
    const val BODY_H = 402f
    const val TEXT_W = 380f
    const val TEXT_H = 74f

    const val TEXT_X = 150f
    const val TEXT_Y = 42f
    const val HUB = 60f
    val HUB_L = floatArrayOf(200f, 216f)
    val HUB_R = floatArrayOf(440f, 216f)

    // Tape packs: the bare hub is RMIN across; a full reel reaches RMAX, inside the flanges (92).
    // The radii follow the tape's area, so the two reels always hold the same amount between them.
    private const val RMIN = 34f
    private const val RMAX = 88f

    private fun packRadius(fill: Float): Float =
        kotlin.math.sqrt(RMIN * RMIN + (RMAX * RMAX - RMIN * RMIN) * fill.coerceIn(0f, 1f))

    private val packPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f; color = 0x10FFFFFF
    }
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4.4f; color = 0xFF2A1A0F.toInt()
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * The brown tape: both packs and the strip running round the bottom guides, for [progress]
     * 0..1 of the song. [canvas] is in the pictures' 640x402 units.
     */
    fun drawTape(canvas: Canvas, progress: Float, window: android.graphics.RectF? = null) {
        val p = progress.coerceIn(0f, 1f)
        val rl = packRadius(1f - p)
        val rr = packRadius(p)
        val path = android.graphics.Path().apply {
            moveTo(HUB_L[0] - rl + 8f, HUB_L[1] + 36f)
            lineTo(92f, 352f); lineTo(548f, 352f)
            lineTo(HUB_R[0] + rr - 4f, HUB_R[1] + 24f)
        }
        canvas.drawPath(path, tapePaint)
        val saved = canvas.save()
        window?.let { canvas.clipRect(it) }
        pack(canvas, HUB_L[0], HUB_L[1], rl)
        pack(canvas, HUB_R[0], HUB_R[1], rr)
        canvas.restoreToCount(saved)
    }

    private fun pack(canvas: Canvas, x: Float, y: Float, r: Float) {
        packPaint.shader = android.graphics.RadialGradient(
            x, y, r,
            intArrayOf(0xFF4A3020.toInt(), 0xFF2C1B10.toInt(), 0xFF3D2617.toInt(), 0xFF1C110A.toInt()),
            floatArrayOf(RMIN * 0.6f / r, 0.55f, 0.92f, 1f).map { it.coerceIn(0f, 1f) }.sortedBy { it }.toFloatArray(),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, r, packPaint)
        // Faint winding lines, like layers of tape.
        var k = RMIN + 6f
        while (k < r) { canvas.drawCircle(x, y, k, ringPaint); k += 7f }
    }

    private var bodyKey: String? = null
    private var bodyCache: Bitmap? = null

    /**
     * The widget's cassette picture: back, tape at [progress] and front, [widthPx] wide. The tape is
     * rounded to 1/60 of the song so the picture is only redrawn when the reels visibly change.
     */
    fun body(context: Context, look: Look, progress: Float, widthPx: Int): Bitmap {
        val w = widthPx.coerceIn(160, 640)
        val h = (w * BODY_H / BODY_W).toInt()
        val step = (progress.coerceIn(0f, 1f) * 60).toInt()
        val key = "${look.back}|$step|$w"
        bodyCache?.let { if (key == bodyKey && !it.isRecycled) return it }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(w / BODY_W, h / BODY_H)
        val dst = android.graphics.RectF(0f, 0f, BODY_W, BODY_H)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        android.graphics.BitmapFactory.decodeResource(context.resources, look.back)?.let { canvas.drawBitmap(it, null, dst, paint); it.recycle() }
        drawTape(canvas, step / 60f, look.window)
        android.graphics.BitmapFactory.decodeResource(context.resources, look.front)?.let { canvas.drawBitmap(it, null, dst, paint); it.recycle() }
        bodyKey = key
        bodyCache = bmp
        return bmp
    }

    private const val PREFS = "glass_cassette"

    /** The look used by the player's Cassette style and by Car mode (the widgets keep their own). */
    fun playerLook(context: Context): Int = prefs(context).getInt("player_look", 0).coerceIn(0, LOOKS.lastIndex)

    fun setPlayerLook(context: Context, look: Int) {
        prefs(context).edit().putInt("player_look", look.mod(LOOKS.size)).apply()
    }

    fun lookIndex(context: Context, widgetId: Int): Int =
        prefs(context).getInt("look_$widgetId", 0).coerceIn(0, LOOKS.lastIndex)

    /** -1 means "the look's own pen". */
    fun penChoice(context: Context, widgetId: Int): Int =
        prefs(context).getInt("pen_$widgetId", -1).coerceIn(-1, PENS.lastIndex)

    fun save(context: Context, widgetId: Int, look: Int, pen: Int) {
        prefs(context).edit().putInt("look_$widgetId", look).putInt("pen_$widgetId", pen).apply()
    }

    fun penColor(look: Look, choice: Int): Int = PENS[if (choice >= 0) choice else look.pen].toInt()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var cacheKey: String? = null
    private var cache: Bitmap? = null

    /**
     * The song name (and artist under it) as if written on the strip with a ball pen.
     * [widthPx] x [heightPx] is the strip's writing area on screen, so the bitmap stays small.
     */
    fun writing(context: Context, title: String, artist: String, pen: Int, widthPx: Int, heightPx: Int): Bitmap {
        val w = widthPx.coerceIn(60, 720)
        val h = heightPx.coerceIn(16, 160)
        val key = "$title|$artist|$pen|$w|$h"
        cache?.let { if (key == cacheKey && !it.isRecycled) return it }

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawWriting(context, Canvas(bmp), title, artist, pen, w, h)
        cacheKey = key
        cache = bmp
        return bmp
    }

    private var hand: android.graphics.Typeface? = null

    /** Writes the song on [canvas] in a [w] x [h] box from its top left, as [writing] does. */
    fun drawWriting(context: Context, canvas: Canvas, title: String, artist: String, pen: Int, w: Int, h: Int) {
        val hand = hand ?: runCatching { ResourcesCompat.getFont(context, R.font.kalam_regular) }.getOrNull().also { this.hand = it }
        val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pen
            typeface = hand
            textSize = h * 0.46f
        }
        val small = TextPaint(big).apply { textSize = h * 0.27f; alpha = 210 }
        canvas.save()
        // A real hand never writes quite straight.
        canvas.rotate(-1.2f, w / 2f, h / 2f)
        val room = w * 0.97f
        val line1 = TextUtils.ellipsize(title, big, room, TextUtils.TruncateAt.END).toString()
        canvas.drawText(line1, 0f, h * 0.56f, big)
        if (artist.isNotEmpty()) {
            val line2 = TextUtils.ellipsize(artist, small, room, TextUtils.TruncateAt.END).toString()
            canvas.drawText(line2, 2f, h * 0.92f, small)
        }
        canvas.restore()
    }
}
