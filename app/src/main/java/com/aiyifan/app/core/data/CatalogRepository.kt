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

    suspend fun getHomeVideoPage(
        category: Category,
        page: Int,
        size: Int = 30,
    ): HomeVideoPage

    suspend fun refreshHome()

    suspend fun getHotVideos(): List<VideoSummary>

    suspend fun searchVideos(keyword: String): List<VideoSummary>

    suspend fun searchSuggestions(keyword: String): List<SearchSuggestion>

    suspend fun refreshVideoDetail(mediaKey: String): VideoDetail =
        throw UnsupportedOperationException("Strict refresh is unavailable")

    suspend fun checkFavoriteUpdates(mediaKey: String, force: Boolean = false) = Unit
    fun getFavoriteTracking(mediaKey: String): com.aiyifan.app.core.model.FavoriteTracking? = null
    fun markFavoriteEpisodesKnown(detail: VideoDetail) = Unit
    fun removeFavorite(mediaKey: String) = Unit
    fun removeHistory(mediaKeys: Set<String>) = Unit
    fun addHistoryListener(listener: () -> Unit): () -> Unit = {}

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
