package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.model.VideoSummary
import org.json.JSONObject

data class NavigationTag(val id: String, val name: String, val classType: Int, val index: Int, val subId: String)
data class NavigationChannel(val id: String, val name: String, val tags: List<NavigationTag>)

class ChannelNavigationRepository(
    private val fetcher: HttpFetcher = UrlConnectionHttpFetcher(),
    private val resolver: RemoteConfigResolver = RemoteConfigResolver(fetcher),
) {
    private suspend fun request(path: String, body: JSONObject): JSONObject {
        val response = fetcher.postJson("${resolver.resolveBaseUrl()}$path", body.toString())
        check(response.code in 200..299) { "Navigation HTTP ${response.code}" }
        val root = JSONObject(response.body)
        check(root.optInt("ret", 200) == 200) { "Navigation service error" }
        return root.getJSONObject("data")
    }

    suspend fun loadChannels(): List<NavigationChannel> {
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
