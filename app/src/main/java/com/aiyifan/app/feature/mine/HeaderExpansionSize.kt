package com.aiyifan.app.feature.mine

internal object HeaderExpansionSize {
    /** Total header height includes the status bar exactly once. */
    fun heightPx(windowHeight: Int, collapsedHeight: Int): Int =
        maxOf(collapsedHeight, (windowHeight.toLong() * 4 / 5).toInt())
}
