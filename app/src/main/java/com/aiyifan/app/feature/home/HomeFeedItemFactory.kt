package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.VideoSummary

sealed interface HomeFeedItem {
    data class Banner(val videos: List<VideoSummary>) : HomeFeedItem

    data class Card(val video: VideoSummary) : HomeFeedItem

    data object Loading : HomeFeedItem
}

object HomeFeedItemFactory {
    const val BANNER_ITEM_LIMIT = 5

    fun create(videos: List<VideoSummary>, isLoadingMore: Boolean = false): List<HomeFeedItem> =
        buildList {
            if (videos.isNotEmpty()) {
                add(HomeFeedItem.Banner(videos.take(BANNER_ITEM_LIMIT)))
                addAll(videos.drop(BANNER_ITEM_LIMIT).map(HomeFeedItem::Card))
            }
            if (videos.isNotEmpty() && isLoadingMore) add(HomeFeedItem.Loading)
        }
}
