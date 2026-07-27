package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.data.CatalogRepository
import com.aiyifan.app.core.data.FakeCatalogRepository
import com.aiyifan.app.core.model.Category
import com.aiyifan.app.core.model.Comment
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.FavoriteVideo
import com.aiyifan.app.core.model.PlaybackLanguage
import com.aiyifan.app.core.model.PlaybackQuality
import com.aiyifan.app.core.model.SearchSuggestion
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.core.model.WatchHistory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class RemoteCatalogRepository(
    private val fallback: FakeCatalogRepository = FakeCatalogRepository(),
    private val configResolver: RemoteConfigResolver = RemoteConfigResolver(UrlConnectionHttpFetcher()),
    private val fetcher: HttpFetcher = UrlConnectionHttpFetcher(),
) : CatalogRepository {
    private val cacheLock = Mutex()
    private val detailLock = Mutex()

    @Volatile
    private var cachedSections: List<TripDataHomeSection>? = null

    private val detailCache = linkedMapOf<String, VideoDetail>()

    override suspend fun getCategories(): List<Category> =
        try {
            ensureSections().mapIndexed { index, section ->
                Category(
                    id = section.name,
                    name = section.name,
                    type = index,
                    styleType = 0,
                )
            }
        } catch (_: Throwable) {
            fallback.getCategories()
        }

    override suspend fun getHomeVideos(categoryId: String): List<VideoSummary> =
        try {
            val sections = ensureSections()
            sections.firstOrNull { it.name == categoryId }?.videos
                ?: sections.firstOrNull()?.videos
                ?: emptyList()
        } catch (_: Throwable) {
            fallback.getHomeVideos(categoryId)
        }

    override suspend fun refreshHome() {
        cacheLock.withLock {
            cachedSections = fetchHomeSections()
        }
    }

    override suspend fun getHotVideos(): List<VideoSummary> =
        try {
            ensureSections()
                .flatMap { it.videos }
                .distinctBy { it.mediaKey }
                .sortedByDescending { it.playCount }
        } catch (_: Throwable) {
            fallback.getHotVideos()
        }

    override suspend fun searchVideos(keyword: String): List<VideoSummary> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()
        return runCatching { searchRemoteVideos(query) }
            .getOrDefault(emptyList())
            .ifEmpty { searchCachedVideos(query) }
            .ifEmpty { fallback.searchVideos(query) }
    }

    override suspend fun searchSuggestions(keyword: String): List<SearchSuggestion> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()
        return try {
            val baseUrl = configResolver.resolveBaseUrl()
            val response = fetcher.get(buildUrl(baseUrl, "api/Home/GetKeyWord", mapOf("keyword" to query)))
            if (response.code !in 200..299) throw IllegalStateException("Suggest failed: ${response.code}")
            parseSearchSuggestions(response.body, query)
        } catch (_: Throwable) {
            localSuggestions(query)
        }
    }

    override suspend fun getVideoDetail(mediaKey: String): VideoDetail {
        detailCache[mediaKey]?.let { return it }
        return detailLock.withLock {
            detailCache[mediaKey]?.let { return@withLock it }
            val detail = try {
                val baseUrl = configResolver.resolveBaseUrl()
                val response = fetcher.get(
                    buildUrl(
                        baseUrl,
                        "api/Video/VideoDetails",
                        mapOf("mediaKey" to mediaKey),
                    ),
                )
                if (response.code !in 200..299) {
                    throw IllegalStateException("VideoDetails failed: ${response.code}")
                }
                parseVideoDetail(response.body)
            } catch (_: Throwable) {
                fallback.getVideoDetail(mediaKey)
            }
            detailCache[mediaKey] = detail
            detail
        }
    }

    override suspend fun resolvePlayback(
        detail: VideoDetail,
        episode: Episode,
        forceRefresh: Boolean,
    ): Episode {
        if (!forceRefresh && !episode.mediaUrl.isNullOrBlank()) {
            return episode
        }
        return try {
            val baseUrl = configResolver.resolveBaseUrl()
            val response = fetcher.get(
                buildUrl(
                    baseUrl,
                    "api/Video/getPlayData",
                    linkedMapOf(
                        "mediaKey" to detail.mediaKey,
                        "videoId" to episode.uniqueId.toString(),
                        "resolution" to episode.resolution.orEmpty(),
                        "liveLine" to "",
                        "subtitlePrefer" to "",
                        "videoType" to detail.videoType.toString(),
                    ),
                ),
            )
            if (response.code !in 200..299) {
                throw IllegalStateException("getPlayData failed: ${response.code}")
            }
            parsePlayableEpisode(response.body, episode, episode.resolution)
        } catch (_: Throwable) {
            if (forceRefresh) episode.copy(mediaUrl = null) else episode
        }
    }

    override fun getComments(mediaKey: String): List<Comment> = fallback.getComments(mediaKey)

    override fun saveHistory(detail: VideoDetail, episode: Episode, progressMs: Long, durationMs: Long) {
        fallback.saveHistory(detail, episode, progressMs, durationMs)
    }

    override fun getHistory(): List<WatchHistory> = fallback.getHistory()

    override fun clearHistory() {
        fallback.clearHistory()
    }

    override fun toggleFavorite(detail: VideoDetail): Boolean = fallback.toggleFavorite(detail)

    override fun isFavorite(mediaKey: String): Boolean = fallback.isFavorite(mediaKey)

    override fun getFavorites(): List<FavoriteVideo> = fallback.getFavorites()

    private suspend fun ensureSections(): List<TripDataHomeSection> {
        cachedSections?.let { return it }
        return cacheLock.withLock {
            cachedSections?.let { return@withLock it }
            fetchHomeSections().also { parsed ->
                cachedSections = parsed
            }
        }
    }

    private suspend fun fetchHomeSections(): List<TripDataHomeSection> {
        val baseUrl = configResolver.resolveBaseUrl()
        val response = fetcher.get("${baseUrl}api/List/Index")
        if (response.code !in 200..299) {
            throw IllegalStateException("Home request failed: ${response.code}")
        }
        return TripDataHomeParser.parse(response.body).also { sections ->
            check(sections.isNotEmpty()) { "Home response contains no sections" }
        }
    }

    private fun parseVideoDetail(payload: String): VideoDetail {
        val detailInfo = JSONObject(payload)
            .optJSONObject("data")
            ?.optJSONObject("detailInfo")
            ?: throw IllegalStateException("Missing detailInfo")
        val episodes = parseEpisodes(detailInfo.optJSONArray("episodes"))
        val related = cachedSections
            ?.flatMap { it.videos }
            ?.filterNot { it.mediaKey == detailInfo.optionalRemoteText("mediaKey") }
            ?.distinctBy { it.mediaKey }
            ?.take(12)
            .orEmpty()
        val qualities = episodes
            .mapNotNull { it.resolution }
            .distinct()
            .map { resolution ->
                PlaybackQuality(
                    resolution = resolution,
                    description = "${resolution}P",
                    mediaUrl = "",
                    isDefault = resolution == detailInfo.optionalRemoteText("resolution"),
                )
            }
        val languages = buildList {
            val languageList = JSONObject(payload)
                .optJSONObject("data")
                ?.optJSONArray("languageList")
                ?: JSONArray()
            for (index in 0 until languageList.length()) {
                val item = languageList.optJSONObject(index) ?: continue
                val name = item.optionalRemoteText("name")
                val mediaKey = item.optionalRemoteText("mediaKey")
                if (name != null && mediaKey != null) {
                    add(PlaybackLanguage(mediaKey = mediaKey, name = name))
                }
            }
        }
        return VideoDetail(
            mediaKey = detailInfo.optionalRemoteText("mediaKey").orEmpty(),
            title = detailInfo.optionalRemoteText("title").orEmpty(),
            coverUrl = detailInfo.optionalRemoteText("coverImgUrl").orEmpty(),
            videoType = detailInfo.optInt("videoType"),
            typeName = detailInfo.optionalRemoteText("typeName"),
            director = detailInfo.optionalRemoteText("director"),
            actor = detailInfo.optionalRemoteText("actor"),
            introduce = detailInfo.optionalRemoteText("introduce"),
            playCount = detailInfo.optInt("playCount"),
            comments = detailInfo.optInt("comments"),
            shareCount = detailInfo.optInt("shareCount"),
            publishTime = detailInfo.optionalRemoteText("publishTime"),
            updateMsg = detailInfo.optionalRemoteText("updateStatus"),
            commentEnabled = detailInfo.optInt("commentStatus", 0) == 0,
            episodes = episodes,
            qualities = qualities,
            languages = languages,
            related = related,
        )
    }

    private fun parseEpisodes(items: JSONArray?): List<Episode> =
        buildList {
            if (items == null) return@buildList
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val episodeKey = item.optionalRemoteText("episodeKey").orEmpty()
                if (episodeKey.isBlank()) continue
                add(
                    Episode(
                        episodeKey = episodeKey,
                        episodeTitle = item.optionalRemoteText("episodeTitle") ?: "${index + 1}",
                        uniqueId = item.optInt("episodeId"),
                        mediaUrl = item.optionalRemoteText("mediaUrl"),
                        resolution = item.optionalRemoteText("resolution"),
                        lang = item.optionalRemoteText("lang"),
                        duration = null,
                    ),
                )
            }
        }

    private fun parsePlayableEpisode(
        payload: String,
        fallbackEpisode: Episode,
        requestedResolution: String?,
    ): Episode {
        val items = JSONObject(payload)
            .optJSONObject("data")
            ?.optJSONArray("list")
            ?: JSONArray()
        val playable = mutableListOf<JSONObject>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (item.optionalRemoteText("mediaUrl") == null) continue
            playable += item
        }
        val chosen = playable.firstOrNull {
            it.optionalRemoteText("resolution") == requestedResolution
        } ?: playable.firstOrNull { it.optBoolean("isDefault") }
            ?: playable.firstOrNull()
            ?: return fallbackEpisode
        return fallbackEpisode.copy(
            episodeKey = chosen.optionalRemoteText("episodeKey") ?: fallbackEpisode.episodeKey,
            uniqueId = chosen.optInt("episodeId", fallbackEpisode.uniqueId),
            mediaUrl = chosen.optionalRemoteText("mediaUrl") ?: fallbackEpisode.mediaUrl,
            resolution = chosen.optionalRemoteText("resolution") ?: fallbackEpisode.resolution,
            lang = chosen.optionalRemoteText("lang") ?: fallbackEpisode.lang,
        )
    }

    private suspend fun searchRemoteVideos(query: String): List<VideoSummary> {
        val baseUrl = configResolver.resolveBaseUrl()
        val response = fetcher.postForm(
            "${baseUrl}api/List/GetTitleGetData",
            mapOf("SearchCriteria" to query),
        )
        if (response.code !in 200..299) throw IllegalStateException("Search failed: ${response.code}")
        val payload = JSONObject(response.body)
        if (payload.optInt("ret", 200) != 200) throw IllegalStateException("Search service returned an error")
        return parseSearchVideos(payload)
    }

    private suspend fun searchCachedVideos(query: String): List<VideoSummary> =
        try {
            ensureSections()
                .flatMap { it.videos }
                .distinctBy { it.mediaKey }
                .filter { video -> video.matchesSearch(query) }
        } catch (_: Throwable) {
            emptyList()
        }

    private fun parseSearchVideos(payload: JSONObject): List<VideoSummary> {
        val data = payload.opt("data")
        val items = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("list")
                ?: data.optJSONArray("videoList")
                ?: data.optJSONArray("items")
                ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val mediaKey = item.optionalRemoteText("mediaKey").orEmpty().ifBlank {
                    item.optionalRemoteText("videoKey").orEmpty()
                }
                if (mediaKey.isBlank()) continue
                val publishTime = item.optionalRemoteText("publishTime").orEmpty()
                add(
                    VideoSummary(
                        mediaKey = mediaKey,
                        episodeKey = item.optionalRemoteText("episodeKey"),
                        title = item.optionalRemoteText("title").orEmpty(),
                        coverUrl = item.optionalRemoteText("coverImgUrl").orEmpty(),
                        videoType = item.optInt("videoType", item.optInt("type", 0)),
                        contentType = item.optionalRemoteText("contentType") ?: item.optionalRemoteText("typeName"),
                        mediaType = item.optionalRemoteText("mediaType"),
                        score = item.optionalRemoteText("score"),
                        playCount = item.optInt("playCount"),
                        updateStatus = item.optionalRemoteText("updateStatus"),
                        year = publishTime.takeIf { it.length >= 4 }?.take(4),
                        area = item.optionalRemoteText("regional"),
                        actor = item.optionalRemoteText("actor"),
                        director = item.optionalRemoteText("director"),
                        episodePreviews = parseEpisodePreviews(item.optJSONArray("episodes")),
                    ),
                )
            }
        }
    }

    private fun parseEpisodePreviews(items: JSONArray?): List<Episode> =
        parseEpisodes(items).take(6)

    private fun JSONObject.optionalRemoteText(key: String): String? =
        RemoteTextNormalizer.optional(optString(key))

    private suspend fun localSuggestions(query: String): List<SearchSuggestion> =
        try {
            ensureSections()
                .flatMap { it.videos }
                .flatMap { video -> listOf(video.title, video.actor.orEmpty(), video.director.orEmpty()) }
                .filter { value -> value.contains(query, ignoreCase = true) }
                .distinct()
                .take(8)
                .map(::SearchSuggestion)
        } catch (_: Throwable) {
            fallback.searchSuggestions(query)
        }

    private fun parseSearchSuggestions(payload: String, query: String): List<SearchSuggestion> {
        val data = JSONObject(payload).opt("data")
        val items = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("list") ?: data.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.opt(index)
                val value = when (item) {
                    is String -> item
                    is JSONObject -> item.optString("keyword")
                        .ifBlank { item.optString("name") }
                        .ifBlank { item.optString("title") }
                    else -> ""
                }.trim()
                if (value.isNotBlank()) add(SearchSuggestion(value))
            }
        }.distinctBy { it.keyword }.take(8).ifEmpty { localSuggestionFallback(query) }
    }

    private fun localSuggestionFallback(query: String): List<SearchSuggestion> =
        cachedSections.orEmpty()
            .flatMap { it.videos }
            .flatMap { video -> listOf(video.title, video.actor.orEmpty(), video.director.orEmpty()) }
            .filter { value -> value.contains(query, ignoreCase = true) }
            .distinct()
            .take(8)
            .map(::SearchSuggestion)

    private fun VideoSummary.matchesSearch(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            contentType.orEmpty().contains(query, ignoreCase = true) ||
            mediaType.orEmpty().contains(query, ignoreCase = true) ||
            area.orEmpty().contains(query, ignoreCase = true) ||
            year.orEmpty().contains(query, ignoreCase = true) ||
            actor.orEmpty().contains(query, ignoreCase = true) ||
            director.orEmpty().contains(query, ignoreCase = true)

    private fun buildUrl(baseUrl: String, path: String, params: Map<String, String>): String {
        if (params.isEmpty()) return "$baseUrl$path"
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$baseUrl$path?$query"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val DEFAULT_REGION = "cn"
    }
}
