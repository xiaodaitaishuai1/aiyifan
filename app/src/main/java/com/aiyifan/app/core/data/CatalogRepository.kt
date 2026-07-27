package com.aiyifan.app.core.data

import com.aiyifan.app.core.model.Category
import com.aiyifan.app.core.model.Comment
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.FavoriteVideo
import com.aiyifan.app.core.model.SearchSuggestion
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.core.model.WatchHistory

interface CatalogRepository {
    suspend fun getCategories(): List<Category>

    suspend fun getHomeVideos(categoryId: String): List<VideoSummary>

    suspend fun refreshHome()

    suspend fun getHotVideos(): List<VideoSummary>

    suspend fun searchVideos(keyword: String): List<VideoSummary>

    suspend fun searchSuggestions(keyword: String): List<SearchSuggestion>

    suspend fun getVideoDetail(mediaKey: String): VideoDetail

    suspend fun resolvePlayback(
        detail: VideoDetail,
        episode: Episode,
        forceRefresh: Boolean = false,
    ): Episode

    fun getComments(mediaKey: String): List<Comment>

    fun saveHistory(detail: VideoDetail, episode: Episode, progressMs: Long, durationMs: Long)

    fun getHistory(): List<WatchHistory>

    fun clearHistory()

    fun toggleFavorite(detail: VideoDetail): Boolean

    fun isFavorite(mediaKey: String): Boolean

    fun getFavorites(): List<FavoriteVideo>
}
