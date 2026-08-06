package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedItemFactoryTest {

    @Test
    fun `empty videos create no home feed items`() {
        assertTrue(HomeFeedItemFactory.create(emptyList()).isEmpty())
    }

    @Test
    fun `a single video becomes a banner carousel without cards`() {
        val videos = listOf(video("banner"))

        assertEquals(
            listOf(
                HomeFeedItem.Banner(videos),
            ),
            HomeFeedItemFactory.create(videos),
        )
    }

    @Test
    fun `first five videos become one banner carousel and remaining videos become cards`() {
        val videos = (1..7).map { video("video-$it") }

        assertEquals(
            listOf(
                HomeFeedItem.Banner(videos.take(5)),
                HomeFeedItem.Card(videos[5]),
                HomeFeedItem.Card(videos[6]),
            ),
            HomeFeedItemFactory.create(videos),
        )
    }

    @Test
    fun `six videos keep the first five in the banner and append a loading footer`() {
        val videos = (1..6).map { video("video-$it") }

        assertEquals(
            listOf(
                HomeFeedItem.Banner(videos.take(5)),
                HomeFeedItem.Card(videos[5]),
                HomeFeedItem.Loading,
            ),
            HomeFeedItemFactory.create(videos, isLoadingMore = true),
        )
    }

    @Test
    fun `loading more does not create a footer for an empty video list`() {
        assertTrue(HomeFeedItemFactory.create(emptyList(), isLoadingMore = true).isEmpty())
    }

    private fun video(mediaKey: String) = VideoSummary(
        mediaKey = mediaKey,
        title = mediaKey,
        coverUrl = "",
        videoType = 0,
    )
}
