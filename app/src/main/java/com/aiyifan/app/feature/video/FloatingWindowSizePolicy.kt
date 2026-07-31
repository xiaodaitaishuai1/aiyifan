package com.aiyifan.app.feature.video

import kotlin.math.roundToInt

data class FloatingWindowSize(
    val width: Int,
    val height: Int,
)

object FloatingWindowSizePolicy {
    fun resize(requestedWidth: Int, minWidth: Int, maxWidth: Int): FloatingWindowSize {
        val width = requestedWidth.coerceIn(minWidth, maxWidth)
        return FloatingWindowSize(
            width = width,
            height = (width * HEIGHT_RATIO / WIDTH_RATIO).roundToInt(),
        )
    }

    fun resizeByScale(
        currentWidth: Int,
        scaleFactor: Float,
        minWidth: Int,
        maxWidth: Int,
    ): FloatingWindowSize = resize(
        requestedWidth = (currentWidth * scaleFactor).roundToInt(),
        minWidth = minWidth,
        maxWidth = maxWidth,
    )

    private const val WIDTH_RATIO = 16f
    private const val HEIGHT_RATIO = 9f
}
