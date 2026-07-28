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
    fun `first video becomes banner and remaining videos become cards`() {
        val videos = listOf(video("banner"), video("card-one"), video("card-two"))

        assertEquals(
            listOf(
                HomeFeedItem.Banner(videos[0]),
                HomeFeedItem.Card(videos[1]),
                HomeFeedItem.Card(videos[2]),
            ),
            HomeFeedItemFactory.create(videos),
        )
    }

    @Test
    fun `loading state appends a footer after the current home videos`() {
        val videos = listOf(video("banner"), video("card-one"))

        assertEquals(
            listOf(
                HomeFeedItem.Banner(videos[0]),
                HomeFeedItem.Card(videos[1]),
                HomeFeedItem.Loading,
            ),
            HomeFeedItemFactory.create(videos, isLoadingMore = true),
        )
    }

    private fun video(mediaKey: String) = VideoSummary(
        mediaKey = mediaKey,
        title = mediaKey,
        coverUrl = "",
        videoType = 0,
    )
}
