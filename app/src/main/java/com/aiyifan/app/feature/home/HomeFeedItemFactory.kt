package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.VideoSummary

sealed interface HomeFeedItem {
    data class Banner(val video: VideoSummary) : HomeFeedItem

    data class Card(val video: VideoSummary) : HomeFeedItem

    data object Loading : HomeFeedItem
}

object HomeFeedItemFactory {
    fun create(videos: List<VideoSummary>, isLoadingMore: Boolean = false): List<HomeFeedItem> =
        buildList {
            addAll(videos.mapIndexed { index, video ->
            if (index == 0) HomeFeedItem.Banner(video) else HomeFeedItem.Card(video)
            })
            if (videos.isNotEmpty() && isLoadingMore) add(HomeFeedItem.Loading)
        }
}
