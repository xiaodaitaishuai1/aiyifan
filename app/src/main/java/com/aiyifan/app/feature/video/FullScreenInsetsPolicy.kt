package com.aiyifan.app.feature.video

object FullScreenInsetsPolicy {
    fun shouldApplyPageInsets(isFullScreen: Boolean): Boolean = !isFullScreen
}
