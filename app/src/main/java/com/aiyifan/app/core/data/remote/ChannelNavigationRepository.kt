package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.model.VideoSummary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class NavigationTag(val id: String, val name: String, val classType: Int, val index: Int, val subId: String)
data class NavigationChannel(val id: String, val name: String, val tags: List<NavigationTag>)

class ChannelNavigationRepository(
    private val fetcher: HttpFetcher = UrlConnectionHttpFetcher(),
    private val resolver: RemoteConfigResolver = RemoteConfigResolver(fetcher),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class ChannelCache(val channels: List<NavigationChannel>, val savedAt: Long)
    private var channelCache: ChannelCache? = null
    private val channelLock = Mutex()

    private suspend fun request(path: String, body: JSONObject): JSONObject {
        val response = fetcher.postJson("${resolver.resolveBaseUrl()}$path", body.toString())
        check(response.code in 200..299) { "Navigation HTTP ${response.code}" }
        val root = JSONObject(response.body)
        check(root.optInt("ret", 200) == 200) { "Navigation service error" }
        return root.getJSONObject("data")
    }

    /** Shared by navigation screens through AppGraph; successful data expires after five minutes. */
    suspend fun loadChannels(): List<NavigationChannel> = channelLock.withLock {
        channelCache?.let { cached ->
            if (clock() - cached.savedAt in 0 until 300_000L) return@withLock cached.channels
        }
        val channels = fetchChannels()
        currentCoroutineContext().ensureActive()
        // Empty results must remain retryable from the navigation empty state.
        channelCache = channels.takeIf { it.isNotEmpty() }?.let { ChannelCache(it, clock()) }
        channels
    }

    private suspend fun fetchChannels(): List<NavigationChannel> {
        val list = request("api/Home/GetChannelNavigation", JSONObject()).getJSONArray("list")
        return buildList {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val id = item.text("categoryId") ?: continue
                val name = item.text("name") ?: continue
                val children = item.optJSONArray("list") ?: continue
                val tags = buildList {
                    for (j in 0 until children.length()) {
                        val tag = children.optJSONObject(j) ?: continue
                        val tagId = tag.text("classifyId") ?: continue
                        val tagName = tag.text("classifyName") ?: continue
                        val type = tag.optInt("classType")
                        val index = tag.optInt("index")
                        if (type !in 1..2 || (type == 1 && index !in 0..5)) continue
                        add(NavigationTag(tagId, tagName, type, index, tag.text("subID").orEmpty()))
                    }
                }
                if (tags.isNotEmpty()) add(NavigationChannel(id, name, tags))
            }
        }
    }

    suspend fun loadVideos(channelId: String, tag: NavigationTag, page: Int): List<VideoSummary> {
        require(page > 0)
        val body = JSONObject().put("page", page.toString()).put("size", "30")
        val path = if (tag.classType == 2) {
            body.put("titleId", channelId).put("ids", "0").put("name", tag.subId)
            "api/List/GetSmallVideo?videoType=3"
        } else {
            require(tag.index in 0..5)
            val ids = MutableList(6) { "0" }.apply { this[tag.index] = tag.id }
            body.put("titleid", channelId).put("ids", ids.joinToString(","))
            "api/List/GetConditionFilterData"
        }
        val list = request(path, body).getJSONArray("list")
        return buildList {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val key = item.text("mediaKey") ?: continue
                add(VideoSummary(
                    mediaKey = key, title = item.text("title").orEmpty(),
                    coverUrl = item.text("coverImgUrl").orEmpty(), videoType = item.optInt("videoType"),
                    episodeKey = item.text("episodeKey"), score = item.text("score"),
                    playCount = item.optInt("playCount"), updateStatus = item.text("updateStatus"),
                    year = item.text("publishTime")?.take(4), area = item.text("regional"),
                ))
            }
        }.distinctBy { it.mediaKey }
    }

    private fun JSONObject.text(key: String): String? = RemoteTextNormalizer.optional(optString(key))
}
