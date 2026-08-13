package com.aiyifan.app.feature.home

data class HomeImageSize(
    val width: Int,
    val height: Int,
)

object HomePosterSizePolicy {
    fun pxFromDp(dp: Int, density: Float): Int = (dp * density).toInt().coerceAtLeast(1)

    fun banner(width: Int): HomeImageSize = sizeForRatio(width, 16, 8)

    fun card(width: Int): HomeImageSize = sizeForRatio(width, 2, 3)

    private fun sizeForRatio(width: Int, ratioWidth: Int, ratioHeight: Int): HomeImageSize {
        val safeWidth = width.coerceAtLeast(1)
        return HomeImageSize(
            width = safeWidth,
            height = (safeWidth * ratioHeight / ratioWidth).coerceAtLeast(1),
        )
    }
}
