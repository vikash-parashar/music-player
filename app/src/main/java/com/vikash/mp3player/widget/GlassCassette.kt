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
 * https://claude.ai/artifact/61AEKysWe8P1U22HJSUkRa.
 *
 * Each look is a pre-drawn cassette body (widget_glass_body_N, 640x402, see-through glass with a
 * blank white label strip). The widget lays the spinning hubs on top and a small bitmap with the
 * song name written in the Kalam hand font, in blue, green or red pen.
 */
object GlassCassette {

    class Look(
        val name: String,
        val body: Int,
        /** Tint of the frame around the cassette and the keys, ARGB. */
        val shell: Long,
        val keyTint: Long,
        val hub: Hub,
        /** Index into [PENS] used when the person keeps the look's own pen. */
        val pen: Int,
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

    val LOOKS = listOf(
        Look("Crystal Clear", R.drawable.widget_glass_body_1, 0x29FFFFFF, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Smoke Glass", R.drawable.widget_glass_body_2, 0x5914141A, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Ice Blue", R.drawable.widget_glass_body_3, 0x338FD3FF, 0xFFFFFFFF, Hub.WHITE, RED),
        Look("Aqua", R.drawable.widget_glass_body_4, 0x2E5EEAD4, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Mint", R.drawable.widget_glass_body_5, 0x3396E6B4, 0xFFFFFFFF, Hub.WHITE, RED),
        Look("Rose", R.drawable.widget_glass_body_6, 0x2EFF78A0, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Honey Amber", R.drawable.widget_glass_body_7, 0x33F6AA3C, 0xFFFFFFFF, Hub.WHITE, GREEN),
        Look("Violet", R.drawable.widget_glass_body_8, 0x33966EFF, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Ruby", R.drawable.widget_glass_body_9, 0x38C81E32, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Frosted", R.drawable.widget_glass_body_10, 0x55FFFFFF, 0xFFFFFFFF, Hub.WHITE, GREEN),
        Look("Night Glow", R.drawable.widget_glass_body_11, 0x73080A14, 0xFF8FD3FF, Hub.BLUE, BLUE),
        Look("Gold Edge", R.drawable.widget_glass_body_12, 0x2EE6B84F, 0xFFFFFFFF, Hub.GOLD, RED),
        Look("Rainbow", R.drawable.widget_glass_body_13, 0x26FFFFFF, 0xFFFFFFFF, Hub.WHITE, BLUE),
        Look("Sticker Mixtape", R.drawable.widget_glass_body_14, 0x29FFFFFF, 0xFFFFFFFF, Hub.WHITE, RED),
    )

    // The body picture is 640x402. widget_glass_cassette.xml places the writing (380x74, from 150,42)
    // and the hubs (60x60, centred on 200,216 and 440,216) with layout weights in these units.
    const val BODY_W = 640f
    const val BODY_H = 402f
    const val TEXT_W = 380f
    const val TEXT_H = 74f

    private const val PREFS = "glass_cassette"

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
        val canvas = Canvas(bmp)
        val hand = runCatching { ResourcesCompat.getFont(context, R.font.kalam_regular) }.getOrNull()
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
        cacheKey = key
        cache = bmp
        return bmp
    }
}
