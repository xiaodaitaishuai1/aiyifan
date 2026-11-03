package com.aiyifan.app.feature.video

import kotlin.math.roundToInt

data class FloatingWindowSize(
    val width: Int,
    val height: Int,
)

object FloatingWindowSizePolicy {
    fun resize(requestedWidth: Int, minWidth: Int, maxWidth: Int): FloatingWindowSize {
        val upperBound = maxWidth.coerceAtLeast(1)
        val width = requestedWidth.coerceIn(minWidth.coerceIn(1, upperBound), upperBound)
        return FloatingWindowSize(
            width = width,
            height = (width * HEIGHT_RATIO / WIDTH_RATIO).roundToInt(),
        )
    }

    fun fit(
        requestedWidth: Int,
        minWidth: Int,
        maxWidth: Int,
        availableWidth: Int,
        availableHeight: Int,
    ): FloatingWindowSize = resize(
        requestedWidth = requestedWidth,
        minWidth = minWidth,
        maxWidth = minOf(maxWidth, availableWidth, (availableHeight * WIDTH_RATIO / HEIGHT_RATIO).toInt()),
    )

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
