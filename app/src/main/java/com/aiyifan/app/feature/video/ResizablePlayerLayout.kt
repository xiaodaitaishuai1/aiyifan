package com.aiyifan.app.feature.video

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** Owns resize gestures before child buttons or the video surface can consume them. */
class ResizablePlayerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var resizeWidth: ((Int) -> Int)? = null
    private var resizeStarted: () -> Unit = {}
    private var resizeFinished: () -> Unit = {}
    private var pinchGesture = false
    private var handleGesture = false
    private var requestedWidth = 0
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (resizeWidth == null) return false
            pinchGesture = true
            requestedWidth = width
            parent?.requestDisallowInterceptTouchEvent(true)
            resizeStarted()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            requestedWidth = resizeWidth?.invoke((requestedWidth * detector.scaleFactor).roundToInt()) ?: width
            return true
        }
    }).apply {
        isQuickScaleEnabled = false
        isStylusScaleEnabled = false
    }

    fun configureResize(
        handle: View,
        resizeWidth: (Int) -> Int,
        onStart: () -> Unit = {},
        onFinish: () -> Unit = {},
    ) {
        this.resizeWidth = resizeWidth
        // Keep receiving the second pointer when the first lands on a controller-free video surface.
        isClickable = true
        resizeStarted = onStart
        resizeFinished = onFinish
        var downRawX = 0f
        var initialWidth = 0
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handleGesture = true
                    downRawX = event.rawX
                    initialWidth = width
                    parent?.requestDisallowInterceptTouchEvent(true)
                    resizeStarted()
                }
                MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
                    resizeWidth(initialWidth + (event.rawX - downRawX).roundToInt())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handleGesture = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    resizeFinished()
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val wasPinching = pinchGesture
        if (!handleGesture) scaleDetector.onTouchEvent(event)
        if (!pinchGesture) return super.dispatchTouchEvent(event)
        if (!wasPinching) {
            // Cancel the previous child/drag target once, then consume the entire pinch sequence.
            MotionEvent.obtain(event).also { cancel ->
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            pinchGesture = false
            parent?.requestDisallowInterceptTouchEvent(false)
            resizeFinished()
        }
        return true
    }
}
