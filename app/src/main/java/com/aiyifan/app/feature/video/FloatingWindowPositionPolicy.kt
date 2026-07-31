package com.aiyifan.app.feature.video

data class FloatingWindowPosition(
    val x: Int,
    val y: Int,
)

enum class FloatingWindowEdge {
    LEFT,
    RIGHT,
}

object FloatingWindowPositionPolicy {
    fun snapToNearestHorizontalEdge(
        x: Int,
        y: Int,
        windowWidth: Int,
        windowHeight: Int,
        displayWidth: Int,
        displayHeight: Int,
        hideThreshold: Int = DEFAULT_HIDE_THRESHOLD,
        hiddenHandleWidth: Int = DEFAULT_HIDDEN_HANDLE_WIDTH,
    ): FloatingWindowPosition {
        val clamped = clampToDisplay(
            x = x,
            y = y,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
        )
        val rightEdge = (displayWidth - windowWidth).coerceAtLeast(0)
        val handleWidth = hiddenHandleWidth.coerceIn(0, windowWidth)
        return when {
            clamped.x <= hideThreshold -> clamped.copy(x = -(windowWidth - handleWidth))
            rightEdge - clamped.x <= hideThreshold -> clamped.copy(x = displayWidth - handleWidth)
            clamped.x <= rightEdge / 2 -> clamped.copy(x = 0)
            else -> clamped.copy(x = rightEdge)
        }
    }

    fun hiddenEdgeForPosition(
        x: Int,
        windowWidth: Int,
        displayWidth: Int,
    ): FloatingWindowEdge? = when {
        x < 0 -> FloatingWindowEdge.LEFT
        x > (displayWidth - windowWidth).coerceAtLeast(0) -> FloatingWindowEdge.RIGHT
        else -> null
    }

    fun visibleXForEdge(
        edge: FloatingWindowEdge,
        windowWidth: Int,
        displayWidth: Int,
    ): Int = when (edge) {
        FloatingWindowEdge.LEFT -> 0
        FloatingWindowEdge.RIGHT -> (displayWidth - windowWidth).coerceAtLeast(0)
    }

    fun clampToDisplay(
        x: Int,
        y: Int,
        windowWidth: Int,
        windowHeight: Int,
        displayWidth: Int,
        displayHeight: Int,
    ): FloatingWindowPosition = FloatingWindowPosition(
        x = x.coerceIn(0, (displayWidth - windowWidth).coerceAtLeast(0)),
        y = y.coerceIn(0, (displayHeight - windowHeight).coerceAtLeast(0)),
    )

    private const val DEFAULT_HIDE_THRESHOLD = 32
    private const val DEFAULT_HIDDEN_HANDLE_WIDTH = 24
}
