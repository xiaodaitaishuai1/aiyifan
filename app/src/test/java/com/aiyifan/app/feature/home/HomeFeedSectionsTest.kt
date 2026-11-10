package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedSectionsTest {
    @Test
    fun `empty feed has no banner and no cards`() {
        assertEquals(HomeFeedSections(emptyList(), emptyList()), HomeFeedSections.from(emptyList()))
    }

    @Test
    fun `first five videos are banner and remaining videos are cards`() {
        val videos = (1..7).map { video("video-$it") }

        assertEquals(
            HomeFeedSections(videos.take(5), videos.drop(5)),
            HomeFeedSections.from(videos),
        )
    }

    @Test
    fun `loading state does not duplicate banner videos`() {
        val videos = (1..6).map { video("video-$it") }

        assertEquals(videos.take(5), HomeFeedSections.from(videos).banner)
        assertEquals(listOf(videos[5]), HomeFeedSections.from(videos).cards)
    }

    private fun video(mediaKey: String) = VideoSummary(
        mediaKey = mediaKey,
        title = mediaKey,
        coverUrl = "",
        videoType = 0,
    )
}
