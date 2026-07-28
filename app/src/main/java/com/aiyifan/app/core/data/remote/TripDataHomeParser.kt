package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.model.VideoSummary
import org.json.JSONArray
import org.json.JSONObject

data class TripDataHomeSection(
    val name: String,
    val videos: List<VideoSummary>,
)

object TripDataHomeParser {
    fun parse(payload: String): List<TripDataHomeSection> {
        val sections = JSONObject(payload)
            .optJSONObject("data")
            ?.optJSONArray("list")
            ?: JSONArray()
        return buildList {
            for (index in 0 until sections.length()) {
                val section = sections.optJSONObject(index) ?: continue
                val name = section.optionalRemoteText("name").orEmpty()
                val videos = parseVideos(section.optJSONArray("list"))
                if (name.isNotBlank() && videos.isNotEmpty()) {
                    add(TripDataHomeSection(name = name, videos = videos))
                }
            }
        }
    }

    fun parseSection(payload: String, name: String): List<VideoSummary> {
        val sections = JSONObject(payload)
            .optJSONObject("data")
            ?.optJSONArray("list")
            ?: JSONArray()
        for (index in 0 until sections.length()) {
            val section = sections.optJSONObject(index) ?: continue
            if (section.optionalRemoteText("name") == name) {
                return parseVideos(section.optJSONArray("list")).distinctBy(VideoSummary::mediaKey)
            }
        }
        return emptyList()
    }

    private fun parseVideos(items: JSONArray?): List<VideoSummary> =
        buildList {
            if (items == null) return@buildList
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val mediaKey = item.optionalRemoteText("mediaKey").orEmpty()
                if (mediaKey.isBlank()) continue
                val videoType = item.optInt("videoType", item.optInt("type", 0))
                if (videoType == 3) continue
                val publishTime = item.optionalRemoteText("publishTime").orEmpty()
                add(
                    VideoSummary(
                        mediaKey = mediaKey,
                        episodeKey = item.optionalRemoteText("episodeKey"),
                        title = item.optionalRemoteText("title").orEmpty(),
                        coverUrl = item.optionalRemoteText("coverImgUrl").orEmpty(),
                        videoType = videoType,
                        contentType = item.optionalRemoteText("contentType"),
                        mediaType = item.optionalRemoteText("mediaType"),
                        score = item.optionalRemoteText("score"),
                        playCount = item.optInt("playCount"),
                        updateStatus = item.optionalRemoteText("updateStatus"),
                        duration = item.optionalRemoteText("duration"),
                        year = publishTime.takeIf { it.length >= 4 }?.substring(0, 4),
                        area = item.optionalRemoteText("regional"),
                        actor = item.optionalRemoteText("actor"),
                        director = item.optionalRemoteText("director"),
                    ),
                )
            }
        }

    private fun JSONObject.optionalRemoteText(key: String): String? =
        RemoteTextNormalizer.optional(optString(key))
}
