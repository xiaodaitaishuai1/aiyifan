package com.aiyifan.app.feature.shortdrama

import com.aiyifan.app.core.data.HomeVideoPage
import com.aiyifan.app.core.model.VideoSummary

class ShortDramaPagination {
    data class Request(val generation: Long, val page: Int)

    var videos: List<VideoSummary> = emptyList()
        private set
    var hasMore = true
        private set
    private var generation = 0L
    private var nextPage = 1
    private var pending: Request? = null
    val loading: Boolean get() = pending != null

    fun begin(refresh: Boolean = false): Request? {
        if (refresh) {
            generation++
            nextPage = 1
            hasMore = true
            pending = null
        }
        if (loading || !hasMore) return null
        return Request(++generation, nextPage).also { pending = it }
    }

    fun complete(request: Request, response: HomeVideoPage): Boolean {
        if (pending != request) return false
        videos = ((if (request.page == 1) emptyList() else videos) + response.videos)
            .distinctBy(VideoSummary::mediaKey)
        nextPage = request.page + 1
        hasMore = response.hasMore
        pending = null
        return true
    }

    fun fail(request: Request): Boolean {
        if (pending != request) return false
        pending = null
        return true
    }
}
