package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.VideoSummary

class HomeFeedPagination(
    private val initialSize: Int = 7,
    private val pageSize: Int = 6,
) {
    private var allItems = emptyList<VideoSummary>()
    private var shownCount = 0

    val hasMore: Boolean
        get() = shownCount < allItems.size

    fun reset(selected: List<VideoSummary>): List<VideoSummary> {
        allItems = selected.distinctBy(VideoSummary::mediaKey)
        shownCount = minOf(initialSize, allItems.size)
        return allItems.take(shownCount)
    }

    fun next(): List<VideoSummary> {
        shownCount = minOf(shownCount + pageSize, allItems.size)
        return allItems.take(shownCount)
    }
}
