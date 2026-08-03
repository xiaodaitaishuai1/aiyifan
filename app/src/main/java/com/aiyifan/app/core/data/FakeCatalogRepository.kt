package com.aiyifan.app.core.data

import com.aiyifan.app.core.model.Category
import com.aiyifan.app.core.model.Comment
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.FavoriteVideo
import com.aiyifan.app.core.model.PlaybackLanguage
import com.aiyifan.app.core.model.SearchSuggestion
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.core.model.WatchHistory

class FakeCatalogRepository(
    private val clock: () -> Long = System::currentTimeMillis,
) : CatalogRepository {
    var resolvePlaybackFailure: Throwable? = null
    private val favorites = linkedMapOf<String, FavoriteVideo>()
    private val history = linkedMapOf<String, WatchHistory>()

    private val categories = listOf(
        Category(id = "recommend", name = "推荐", type = 0, styleType = 0),
        Category(id = "movie", name = "电影", type = 1, styleType = 0),
        Category(id = "tv", name = "电视剧", type = 2, styleType = 0),
        Category(id = "anime", name = "动漫", type = 3, styleType = 0),
        Category(id = "variety", name = "综艺", type = 4, styleType = 0),
        Category(id = "documentary", name = "纪录片", type = 5, styleType = 0),
    )

    private val summaries = listOf(
        VideoSummary(
            mediaKey = "movie-mc",
            title = "迷城风云",
            coverUrl = "",
            videoType = 1,
            contentType = "电影",
            score = "8.8",
            playCount = 358210,
            updateStatus = "高清",
            duration = "01:48:32",
            year = "2026",
            area = "中国大陆",
            actor = "林川 / 赵晴",
            director = "陈一",
        ),
        VideoSummary(
            mediaKey = "tv-river",
            title = "长河旧事",
            coverUrl = "",
            videoType = 2,
            contentType = "电视剧",
            score = "9.1",
            playCount = 912334,
            updateStatus = "更新至 18 集",
            duration = "45:00",
            year = "2025",
            area = "中国大陆",
            actor = "沈南 / 何洛",
            director = "苏闻",
        ),
        VideoSummary(
            mediaKey = "anime-sky",
            title = "星桥少年",
            coverUrl = "",
            videoType = 3,
            contentType = "动漫",
            score = "8.6",
            playCount = 438221,
            updateStatus = "更新至 12 集",
            duration = "24:00",
            year = "2026",
            area = "日本",
            actor = "配音剧团",
            director = "青木",
        ),
        VideoSummary(
            mediaKey = "doc-food",
            title = "烟火地图",
            coverUrl = "",
            videoType = 5,
            contentType = "纪录片",
            score = "9.4",
            playCount = 184902,
            updateStatus = "全 6 集",
            duration = "38:00",
            year = "2024",
            area = "中国大陆",
            actor = "旁白：周然",
            director = "叶青",
        ),
    )

    override suspend fun getCategories(): List<Category> = categories

    override suspend fun getHomeVideos(categoryId: String): List<VideoSummary> =
        when (categoryId) {
            "recommend" -> summaries
            else -> summaries.filter { it.contentType == categories.firstOrNull { category -> category.id == categoryId }?.name }
        }

    override suspend fun getHomeVideoPage(
        category: Category,
        page: Int,
        size: Int,
    ): HomeVideoPage =
        if (page == 1) {
            HomeVideoPage(videos = getHomeVideos(category.id).take(size), hasMore = false)
        } else {
            HomeVideoPage(videos = emptyList(), hasMore = false)
        }

    override suspend fun refreshHome() = Unit

    override suspend fun getHotVideos(): List<VideoSummary> =
        summaries.sortedByDescending { it.playCount }

    override suspend fun searchVideos(keyword: String): List<VideoSummary> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()
        return summaries.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.contentType.orEmpty().contains(query, ignoreCase = true) ||
                it.area.orEmpty().contains(query, ignoreCase = true) ||
                it.year.orEmpty().contains(query, ignoreCase = true) ||
                it.actor.orEmpty().contains(query, ignoreCase = true) ||
                it.director.orEmpty().contains(query, ignoreCase = true)
        }
    }

    override suspend fun searchSuggestions(keyword: String): List<SearchSuggestion> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()
        return summaries
            .flatMap { listOf(it.title, it.actor.orEmpty(), it.director.orEmpty()) }
            .filter { it.contains(query, ignoreCase = true) }
            .distinct()
            .take(8)
            .map(::SearchSuggestion)
    }

    override suspend fun getVideoDetail(mediaKey: String): VideoDetail {
        val summary = summaries.firstOrNull { it.mediaKey == mediaKey } ?: summaries.first()
        val episodes = when (summary.videoType) {
            1 -> listOf(episode(summary, 1, "第 1 集"))
            else -> (1..6).map { episode(summary, it, "第 $it 集") }
        }
        val mediaUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        return VideoDetail(
            mediaKey = summary.mediaKey,
            title = summary.title,
            coverUrl = summary.coverUrl,
            videoType = summary.videoType,
            typeName = summary.contentType,
            director = summary.director,
            actor = summary.actor,
            introduce = "一段围绕信念、选择与重逢展开的故事。这里使用 mock 数据打通 v1 主链路，后续可替换为真实接口。",
            playCount = summary.playCount,
            comments = 32,
            shareCount = 108,
            publishTime = summary.year,
            updateMsg = summary.updateStatus,
            commentEnabled = true,
            episodes = episodes,
            languages = listOf(PlaybackLanguage(summary.mediaKey, "国语")),
            related = summaries.filterNot { it.mediaKey == summary.mediaKey },
        )
    }

    override suspend fun resolvePlayback(
        detail: VideoDetail,
        episode: Episode,
        forceRefresh: Boolean,
    ): Episode {
        resolvePlaybackFailure?.let { throw it }
        return episode
    }

    override fun getComments(mediaKey: String): List<Comment> =
        listOf(
            Comment(1, "这个开场节奏很像原版视频站的浏览体验。", "清风", postTime = "刚刚", likeCount = 12),
            Comment(2, "先把主链路跑通很重要，收藏和历史也顺手了。", "南星", postTime = "10 分钟前", likeCount = 8),
        )

    override fun saveHistory(detail: VideoDetail, episode: Episode, progressMs: Long, durationMs: Long) {
        history[detail.mediaKey] = WatchHistory(
            mediaKey = detail.mediaKey,
            episodeKey = episode.episodeKey,
            title = detail.title,
            coverUrl = detail.coverUrl,
            videoType = detail.videoType,
            progressMs = progressMs,
            durationMs = durationMs,
            updatedAt = clock(),
        )
    }

    override fun getHistory(): List<WatchHistory> =
        history.values.sortedByDescending { it.updatedAt }

    override fun clearHistory() {
        history.clear()
    }

    override fun toggleFavorite(detail: VideoDetail): Boolean {
        if (favorites.containsKey(detail.mediaKey)) {
            favorites.remove(detail.mediaKey)
            return false
        }
        favorites[detail.mediaKey] = FavoriteVideo(
            mediaKey = detail.mediaKey,
            title = detail.title,
            coverUrl = detail.coverUrl,
            videoType = detail.videoType,
            createdAt = clock(),
        )
        return true
    }

    override fun isFavorite(mediaKey: String): Boolean = favorites.containsKey(mediaKey)

    override fun getFavorites(): List<FavoriteVideo> =
        favorites.values.sortedByDescending { it.createdAt }

    private fun episode(summary: VideoSummary, index: Int, title: String): Episode =
        Episode(
            episodeKey = "${summary.mediaKey}-ep-$index",
            episodeTitle = title,
            uniqueId = index,
            mediaUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            resolution = "1080P",
            lang = "zh",
            duration = summary.duration,
        )
}
