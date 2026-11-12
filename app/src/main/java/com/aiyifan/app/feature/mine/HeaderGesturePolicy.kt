package com.aiyifan.app.feature.mine

import kotlin.math.abs

internal object HeaderGesturePolicy {
    enum class Axis { UNDECIDED, HORIZONTAL, VERTICAL, SCROLL }

    fun axis(dx: Float, dy: Float, slop: Float): Axis {
        val x = abs(dx)
        val y = abs(dy)
        if (maxOf(x, y) <= slop) return Axis.UNDECIDED
        if (x > y * 1.2f) return Axis.HORIZONTAL
        if (y > x * 1.2f) return Axis.VERTICAL
        return if (maxOf(x, y) > slop * 2) {
            if (x > y) Axis.HORIZONTAL else Axis.VERTICAL
        } else Axis.UNDECIDED
    }

    fun shouldSwitch(dx: Float, width: Float, velocity: Float, minVelocity: Float, slop: Float): Boolean =
        abs(dx) > width * 0.2f ||
            (abs(dx) >= slop && abs(velocity) >= minVelocity && dx * velocity > 0)

    fun canResize(dy: Float, expanded: Boolean, atTop: Boolean): Boolean =
        (dy > 0 && !expanded && atTop) || (dy < 0 && expanded)

    fun expandedAfter(initial: Boolean, dy: Float, threshold: Float): Boolean = when {
        dy >= threshold -> true
        dy <= -threshold -> false
        else -> initial
    }
}
