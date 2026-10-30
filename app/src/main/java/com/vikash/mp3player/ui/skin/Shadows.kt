package com.vikash.mp3player.ui.skin

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * CSS-style box shadows for Compose (outer and inset), used by the Pro looks. Blurred shadows need
 * Android 9+ (hardware-drawn blur); older phones get a crisp shadow when blur is 0 and none otherwise.
 */

private val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun DrawScope.shapePath(shape: Shape, inset: Float = 0f): Path {
    val w = (size.width - inset * 2).coerceAtLeast(0f)
    val h = (size.height - inset * 2).coerceAtLeast(0f)
    val path = Path()
    path.addOutline(shape.createOutline(androidx.compose.ui.geometry.Size(w, h), layoutDirection, this))
    if (inset != 0f) path.translate(Offset(inset, inset))
    return path
}

private fun shadowPaint(color: Color, blurPx: Float): Paint = Paint().also { p ->
    val fp = p.asFrameworkPaint()
    fp.isAntiAlias = true
    fp.color = color.toArgb()
    if (blurPx > 0f) fp.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
}

/** Like CSS `box-shadow: x y blur spread color`. Put it before the background in the chain. */
fun Modifier.dropShadow(
    shape: Shape,
    color: Color,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    blur: Dp = 0.dp,
    spread: Dp = 0.dp,
): Modifier = drawBehind {
    if (color.alpha == 0f) return@drawBehind
    val blurPx = blur.toPx()
    if (blurPx > 0f && !blurOk) return@drawBehind
    val path = shapePath(shape, -spread.toPx())
    path.translate(Offset(offsetX.toPx(), offsetY.toPx()))
    drawIntoCanvas { it.drawPath(path, shadowPaint(color, blurPx)) }
}

/** Like CSS `box-shadow: inset x y blur spread color`. Put it after the background in the chain. */
fun Modifier.innerShadow(
    shape: Shape,
    color: Color,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    blur: Dp = 0.dp,
    spread: Dp = 0.dp,
): Modifier = drawBehind {
    if (color.alpha == 0f) return@drawBehind
    val blurPx = blur.toPx()
    if (blurPx > 0f && !blurOk) return@drawBehind
    val clip = shapePath(shape)
    val hole = shapePath(shape, spread.toPx())
    hole.translate(Offset(offsetX.toPx(), offsetY.toPx()))
    val pad = blurPx * 3f + 4f + maxOf(kotlin.math.abs(offsetX.toPx()), kotlin.math.abs(offsetY.toPx()))
    val outer = Path().apply { addRect(Rect(-pad, -pad, size.width + pad, size.height + pad)) }
    val ring = Path().apply { op(outer, hole, PathOperation.Difference) }
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawPath(ring, shadowPaint(color, blurPx))
        canvas.restore()
    }
}
