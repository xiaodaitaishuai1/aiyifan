package com.aiyifan.app.feature.video

object FloatingControlsVisibilityPolicy {
    fun togglesToVisible(currentlyVisible: Boolean): Boolean = !currentlyVisible

    fun shouldAutoHide(
        controlsVisible: Boolean,
        shownAtMs: Long,
        nowMs: Long,
        delayMs: Long,
    ): Boolean = controlsVisible && nowMs - shownAtMs >= delayMs

    fun isEpisodeButtonEnabled(index: Int, count: Int, offset: Int): Boolean =
        index >= 0 && index + offset in 0 until count
}
