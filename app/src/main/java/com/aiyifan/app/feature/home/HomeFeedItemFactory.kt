package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.VideoSummary

sealed interface HomeFeedItem {
    val video: VideoSummary

    data class Banner(override val video: VideoSummary) : HomeFeedItem

    data class Card(override val video: VideoSummary) : HomeFeedItem
}

object HomeFeedItemFactory {
    fun create(videos: List<VideoSummary>): List<HomeFeedItem> =
        videos.mapIndexed { index, video ->
            if (index == 0) HomeFeedItem.Banner(video) else HomeFeedItem.Card(video)
        }
}
