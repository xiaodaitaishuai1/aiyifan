package com.aiyifan.app.feature.home

import com.aiyifan.app.core.data.HomeVideoPage
import com.aiyifan.app.core.model.VideoSummary

class HomeFeedPagination {
    private var shownItems = emptyList<VideoSummary>()
    private var nextPage = 2
    private var loadingPage: Int? = null
    private var serverHasMore = false

    val hasMore: Boolean
        get() = serverHasMore

    fun reset(response: HomeVideoPage): List<VideoSummary> {
        shownItems = response.videos.distinctBy(VideoSummary::mediaKey)
        nextPage = 2
        loadingPage = null
        serverHasMore = response.hasMore
        return shownItems
    }

    fun beginNextPage(): Int? {
        if (!serverHasMore || loadingPage != null) return null
        return nextPage.also { requestedPage ->
            loadingPage = requestedPage
        }
    }

    fun append(requestedPage: Int, response: HomeVideoPage): List<VideoSummary> {
        if (loadingPage != requestedPage) return shownItems
        shownItems = (shownItems + response.videos).distinctBy(VideoSummary::mediaKey)
        nextPage = requestedPage + 1
        loadingPage = null
        serverHasMore = response.hasMore
        return shownItems
    }

    fun fail(requestedPage: Int) {
        if (loadingPage == requestedPage) {
            loadingPage = null
        }
    }

    fun clear() {
        reset(HomeVideoPage(videos = emptyList(), hasMore = false))
    }

    fun cancelPending() {
        loadingPage = null
    }

    fun reset(selected: List<VideoSummary>): List<VideoSummary> =
        reset(HomeVideoPage(videos = selected, hasMore = false))

    fun next(): List<VideoSummary> = shownItems
}
