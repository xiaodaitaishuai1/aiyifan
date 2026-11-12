package com.aiyifan.app.feature.mine

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import com.aiyifan.app.R
import com.aiyifan.app.feature.mine.HeaderGesturePolicy.Axis

/** Owns image gestures, leaving buttons and ordinary vertical page scrolling to their views. */
class HeaderGestureLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    interface Listener {
        fun onTouchStart()
        fun onHorizontalDrag(offset: Float)
        fun onTouchFinish(switchImage: Boolean)
        fun onExpandedChanged(expanded: Boolean)
    }

    var listener: Listener? = null
    var isPageAtTop: () -> Boolean = { true }
    /** Scroll toward the page top and return the distance actually consumed, in pixels. */
    internal var scrollPageTowardTop: ((Int) -> Int)? = null
    var expanded = false
        private set
    private val baseHeight = resources.getDimensionPixelSize(R.dimen.mine_header_height)
    private var largerHeight = baseHeight
    private val verticalThreshold = resources.getDimension(R.dimen.dp_32)
    private val configuration = ViewConfiguration.get(context)
    private val slop = configuration.scaledTouchSlop.toFloat()
    private var topInset = 0
    val expandedHeightPx get() = largerHeight + topInset
    val collapsedHeightPx get() = baseHeight + topInset
    private var heightAnimation: ValueAnimator? = null
    private var tracker: VelocityTracker? = null
    private var activeTouch = false
    private var axis = Axis.UNDECIDED
    private var downX = 0f
    private var downY = 0f
    private var initialExpanded = false
    private var initialHeight = 0
    private var pageScrollConsumed = 0f
    private val hitRect = Rect()

    fun restoreExpanded(value: Boolean) {
        expanded = value && largerHeight > baseHeight
        setHeight(stableHeight())
    }

    internal fun configureSize(windowHeight: Int, inset: Int): Boolean {
        val targetContentHeight = HeaderExpansionSize.heightPx(windowHeight, baseHeight + inset) - inset
        if (topInset == inset && largerHeight == targetContentHeight) return false
        cancelInteraction()
        topInset = inset
        largerHeight = targetContentHeight
        if (expanded && largerHeight == baseHeight) {
            expanded = false
            listener?.onExpandedChanged(false)
        }
        setHeight(stableHeight())
        return true
    }

    private fun stableHeight() = (if (expanded) largerHeight else baseHeight) + topInset

    private fun setHeight(value: Int) {
        if (layoutParams.height != value) updateLayoutParams { height = value }
    }

    private fun isButton(x: Float, y: Float): Boolean = listOf(R.id.headerPlayback, R.id.headerExpand).any { id ->
        val button = findViewById<View>(id) ?: return@any false
        button.getDrawingRect(hitRect)
        offsetDescendantRectToMyCoords(button, hitRect)
        hitRect.contains(x.toInt(), y.toInt())
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            heightAnimation?.end()
            return !isButton(event.x, event.y)
        }
        return activeTouch
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (isButton(event.x, event.y)) return false
            heightAnimation?.end()
            activeTouch = true
            axis = Axis.UNDECIDED
            // Local coordinates move when the parent scrolls during this same gesture.
            downX = event.rawX
            downY = event.rawY
            pageScrollConsumed = 0f
            initialHeight = height
            initialExpanded = expanded
            tracker?.recycle()
            tracker = VelocityTracker.obtain().also { it.addMovement(event) }
            parent?.requestDisallowInterceptTouchEvent(true)
            listener?.onTouchStart()
            return true
        }
        tracker?.addMovement(event)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            cancelInteraction()
            return true
        }
        val dx = event.rawX - downX
        val dy = event.rawY - downY
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (activeTouch) {
                if (axis == Axis.UNDECIDED) {
                    axis = HeaderGesturePolicy.axis(dx, dy, slop)
                    if (axis == Axis.VERTICAL && (largerHeight == baseHeight ||
                            !(HeaderGesturePolicy.canResize(dy, initialExpanded, isPageAtTop()) ||
                                (dy > 0 && !initialExpanded && scrollPageTowardTop != null)))) {
                        axis = Axis.SCROLL
                        finishTouch(false)
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                when (axis) {
                    Axis.HORIZONTAL -> listener?.onHorizontalDrag(dx)
                    Axis.VERTICAL -> setHeight((initialHeight + resizeDistance(dy).toInt())
                        .coerceIn(baseHeight + topInset, expandedHeightPx))
                    else -> Unit
                }
            }
            MotionEvent.ACTION_UP -> {
                if (activeTouch) {
                    when (axis) {
                        Axis.HORIZONTAL -> {
                            tracker?.computeCurrentVelocity(1000, configuration.scaledMaximumFlingVelocity.toFloat())
                            // Include the final position even if no MOVE event accompanied it.
                            listener?.onHorizontalDrag(dx)
                            finishTouch(HeaderGesturePolicy.shouldSwitch(dx, width.toFloat(),
                                tracker?.xVelocity ?: 0f, configuration.scaledMinimumFlingVelocity.toFloat(), slop))
                        }
                        Axis.VERTICAL -> settleHeight(HeaderGesturePolicy.expandedAfter(
                            initialExpanded, resizeDistance(dy), verticalThreshold))
                        else -> {
                            performClick()
                            finishTouch(false)
                        }
                    }
                }
                releaseTracker()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> cancelInteraction()
        }
        return true
    }

    private fun resizeDistance(dy: Float): Float {
        if (initialExpanded) return dy
        // Keep ownership while scrolling to the top so the remainder of this pull can
        // expand the header. Page scrolling must not count toward the expansion threshold.
        if (!isPageAtTop()) {
            val available = (dy - pageScrollConsumed).toInt().coerceAtLeast(0)
            pageScrollConsumed += scrollPageTowardTop?.invoke(available)?.coerceIn(0, available) ?: 0
        }
        return if (isPageAtTop()) dy - pageScrollConsumed else 0f
    }

    private fun settleHeight(target: Boolean) {
        expanded = target
        listener?.onExpandedChanged(target)
        val animator = ValueAnimator.ofInt(height, stableHeight()).apply {
            duration = 250L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { setHeight(it.animatedValue as Int) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    heightAnimation = null
                    finishTouch(false)
                }
            })
        }
        heightAnimation = animator
        animator.start()
    }

    private fun finishTouch(switchImage: Boolean) {
        if (!activeTouch) return
        activeTouch = false
        listener?.onTouchFinish(switchImage)
    }

    private fun releaseTracker() {
        tracker?.recycle()
        tracker = null
    }

    fun cancelInteraction() {
        heightAnimation?.removeAllListeners()
        heightAnimation?.cancel()
        heightAnimation = null
        setHeight(stableHeight())
        finishTouch(false)
        releaseTracker()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onDetachedFromWindow() {
        cancelInteraction()
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean = super.performClick()

}
