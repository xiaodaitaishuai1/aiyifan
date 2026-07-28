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

    private fun video(mediaKey: String) = VideoSummary(
        mediaKey = mediaKey,
        title = mediaKey,
        coverUrl = "",
        videoType = 0,
    )
}
