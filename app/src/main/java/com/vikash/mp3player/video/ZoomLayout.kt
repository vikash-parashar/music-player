package com.vikash.mp3player.video

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps the video player: two-finger pinch zooms the picture (1x to 4x), one finger pans it
 * while zoomed. Taps still reach the player, so the controls show and hide as before. Only
 * the picture ([target]) is scaled; the play/seek controls stay put.
 */
class ZoomLayout(context: Context) : FrameLayout(context) {
    /** The view that draws the video (the player's content frame). */
    var target: View? = null
    /** Called with the zoom in percent whenever it changes, for the "150%" hint. */
    var onZoom: ((Int) -> Unit)? = null

    var scale = 1f
        private set
    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var panning = false
    // Once a gesture turns into a zoom or pan, the player must not also see it as a tap.
    private var stolen = false
    private var lastScaleEnd = 0L
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val t = target ?: return false
            val old = scale
            scale = (scale * d.scaleFactor).coerceIn(MIN, MAX)
            // Keep the point between the fingers still while zooming.
            val fx = d.focusX - t.width / 2f
            val fy = d.focusY - t.height / 2f
            val ratio = scale / old
            panX = fx - (fx - panX) * ratio
            panY = fy - (fy - panY) * ratio
            apply()
            return true
        }

        override fun onScaleEnd(d: ScaleGestureDetector) {
            // Close to 100%: snap back so the video sits exactly in its frame again.
            if (scale < 1.08f) { reset(); onZoom?.invoke(100) }
            lastScaleEnd = SystemClock.uptimeMillis()
        }
    }).apply { isQuickScaleEnabled = false }

    fun reset() {
        scale = 1f; panX = 0f; panY = 0f
        apply(notify = false)
    }

    private fun apply(notify: Boolean = true) {
        val t = target ?: return
        // Never pan the picture past its own edges.
        val maxX = (t.width * (scale - 1f)) / 2f
        val maxY = (t.height * (scale - 1f)) / 2f
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
        t.scaleX = scale
        t.scaleY = scale
        t.translationX = panX
        t.translationY = panY
        if (notify) onZoom?.invoke((scale * 100).toInt())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stolen = false; panning = false
                downX = ev.x; downY = ev.y; lastX = ev.x; lastY = ev.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> steal(ev)
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1 || detector.isInProgress) {
                    steal(ev)
                } else if (scale > 1f && SystemClock.uptimeMillis() - lastScaleEnd > 150) {
                    if (!panning && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) {
                        panning = true
                        steal(ev)
                    }
                    if (panning) {
                        panX += ev.x - lastX
                        panY += ev.y - lastY
                        apply()
                    }
                }
                lastX = ev.x; lastY = ev.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Continue panning from the finger that stays down, without a jump.
                val stay = if (ev.actionIndex == 0) 1 else 0
                lastX = ev.getX(stay); lastY = ev.getY(stay)
            }
        }
        if (stolen) return true
        return super.dispatchTouchEvent(ev)
    }

    /** Tells the player the touch is over (so no tap fires), then keeps the rest of the gesture. */
    private fun steal(ev: MotionEvent) {
        if (stolen) return
        stolen = true
        val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    private companion object {
        const val MIN = 1f
        const val MAX = 4f
    }
}
