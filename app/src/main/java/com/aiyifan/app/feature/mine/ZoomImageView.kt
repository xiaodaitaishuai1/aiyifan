package com.aiyifan.app.feature.mine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/** Zooms a screen-sized bitmap; gestures never trigger full-resolution decoding. */
internal class ZoomImageView(context: Context) : AppCompatImageView(context) {
    private val transform = Matrix()
    private var zoom = 1f
    private val scaleGesture = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleTo((zoom * detector.scaleFactor).coerceIn(1f, 4f), detector.focusX, detector.focusY)
            return true
        }
    })
    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean = performClick()
        override fun onDoubleTap(e: MotionEvent): Boolean {
            scaleTo(if (zoom > 1.1f) 1f else 2.5f, e.x, e.y)
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleGesture.isInProgress && zoom > 1f) {
                transform.postTranslate(-distanceX, -distanceY)
                constrain()
            }
            return true
        }
    })

    init { scaleType = ScaleType.MATRIX; isClickable = true }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        // ImageView's constructor may dispatch here before this class is initialized.
        if (drawable != null && width > 0) resetTransform()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetTransform()
    }

    private fun resetTransform() {
        val image = drawable ?: return
        if (width == 0 || height == 0 || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
        val scale = minOf(width.toFloat() / image.intrinsicWidth, height.toFloat() / image.intrinsicHeight)
        transform.setScale(scale, scale)
        transform.postTranslate((width - image.intrinsicWidth * scale) / 2, (height - image.intrinsicHeight * scale) / 2)
        zoom = 1f
        imageMatrix = transform
    }

    private fun scaleTo(value: Float, x: Float, y: Float) {
        if (drawable == null) return
        transform.postScale(value / zoom, value / zoom, x, y)
        zoom = value
        constrain()
    }

    private fun constrain() {
        val image = drawable ?: return
        val rect = RectF(0f, 0f, image.intrinsicWidth.toFloat(), image.intrinsicHeight.toFloat())
        transform.mapRect(rect)
        fun offset(start: Float, end: Float, size: Int): Float = when {
            end - start <= size -> (size - start - end) / 2
            start > 0 -> -start
            end < size -> size - end
            else -> 0f
        }
        transform.postTranslate(offset(rect.left, rect.right, width), offset(rect.top, rect.bottom, height))
        imageMatrix = transform
    }

    // GestureDetector calls performClick from onSingleTapConfirmed above.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGesture.onTouchEvent(event)
        gesture.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
