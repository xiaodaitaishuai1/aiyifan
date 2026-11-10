package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.data.HomeVideoPage
import com.aiyifan.app.core.model.VideoSummary
import org.json.JSONObject

internal object ShortDramaPageParser {
    fun parse(payload: String, page: Int): HomeVideoPage {
        val root = JSONObject(payload)
        check(root.optInt("ret") == 200) { "Short drama service rejected request" }
        val sections = root.getJSONObject("data").getJSONArray("list")
        val videos = mutableListOf<VideoSummary>()
        var pageCount = 0
        for (index in 0 until sections.length()) {
            val section = sections.getJSONObject(index)
            if (section.optInt("type", -1) != 8) continue
            pageCount = maxOf(pageCount, section.getInt("pageCount"))
            videos += TripDataHomeParser.parseVideos(section.getJSONArray("list"))
        }
        val unique = videos.distinctBy(VideoSummary::mediaKey)
        return HomeVideoPage(unique, unique.isNotEmpty() && page < pageCount)
    }
}
