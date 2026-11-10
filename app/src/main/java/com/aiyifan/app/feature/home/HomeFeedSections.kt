package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.VideoSummary

data class HomeFeedSections(
    val banner: List<VideoSummary>,
    val cards: List<VideoSummary>,
) {
    companion object {
        const val BANNER_ITEM_LIMIT = 5

        fun from(videos: List<VideoSummary>): HomeFeedSections = HomeFeedSections(
            banner = videos.take(BANNER_ITEM_LIMIT),
            cards = videos.drop(BANNER_ITEM_LIMIT),
        )
    }
}
