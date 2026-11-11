package com.vikash.mp3player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vikash.mp3player.widget.GlassCassette

/**
 * The Glass Cassette from the widget, drawn live: the tape winds from the left reel to the right one
 * as the song plays ([progress] 0..1), the hubs turn by [hubAngle] degrees, and the song is
 * handwritten on the label in the look's own pen. Used by the player's Cassette style and Car mode.
 */
@Composable
fun GlassCassetteView(
    look: GlassCassette.Look,
    title: String,
    artist: String,
    progress: () -> Float,
    hubAngle: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val back = ImageBitmap.imageResource(look.back)
    val front = ImageBitmap.imageResource(look.front)
    val hub = ImageBitmap.imageResource(look.hub.still)
    val pen = GlassCassette.penColor(look, -1)
    // The writing is drawn once per song into its own small picture, at a fixed size in picture units.
    val writing = remember(title, artist, pen) {
        val w = (GlassCassette.TEXT_W * 1.5f).toInt()
        val h = (GlassCassette.TEXT_H * 1.5f).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        GlassCassette.drawWriting(context, android.graphics.Canvas(bmp), title, artist, pen, w, h)
        bmp
    }
    Canvas(modifier.aspectRatio(GlassCassette.BODY_W / GlassCassette.BODY_H)) {
        val k = size.width / GlassCassette.BODY_W
        scale(k, k, Offset.Zero) {
            val full = IntSize(GlassCassette.BODY_W.toInt(), GlassCassette.BODY_H.toInt())
            drawImage(back, dstSize = full)
            drawIntoCanvas { GlassCassette.drawTape(it.nativeCanvas, progress(), look.window) }
            drawImage(front, dstSize = full)
            val a = hubAngle()
            val h = GlassCassette.HUB.toInt()
            for (c in listOf(GlassCassette.HUB_L, GlassCassette.HUB_R)) {
                rotate(a, Offset(c[0], c[1])) {
                    drawImage(hub, dstOffset = IntOffset((c[0] - h / 2).toInt(), (c[1] - h / 2).toInt()), dstSize = IntSize(h, h))
                }
            }
            drawIntoCanvas {
                val dst = android.graphics.RectF(
                    GlassCassette.TEXT_X, GlassCassette.TEXT_Y,
                    GlassCassette.TEXT_X + GlassCassette.TEXT_W, GlassCassette.TEXT_Y + GlassCassette.TEXT_H,
                )
                it.nativeCanvas.drawBitmap(writing, null, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            }
        }
    }
}
