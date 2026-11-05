package com.aiyifan.app.feature.video

object EpisodeListScrollPolicy {
    fun targetPosition(currentIndex: Int, itemCount: Int): Int? =
        currentIndex.takeIf { it in 0 until itemCount }
}
