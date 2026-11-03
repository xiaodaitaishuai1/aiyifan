package com.aiyifan.app.feature.video

import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerEpisodePresentationTest {
    private val first = Episode("first", "100", 7, null)
    private val second = Episode("second", "特别篇", 8, null)
    private val detail = VideoDetail("video", "剧名", "", 2, episodes = listOf(first, second))

    @Test
    fun `title uses list position rather than episode label or identifier`() {
        assertEquals("剧名 · 第 2 集", PlayerEpisodePresentation.title(detail, second, "%s · 第 %d 集"))
    }

    @Test
    fun `missing or unknown selection falls back to video title`() {
        assertEquals("剧名", PlayerEpisodePresentation.title(detail, null, "%s · 第 %d 集"))
        assertEquals("剧名", PlayerEpisodePresentation.title(detail, first.copy(episodeKey = "missing"), "%s · 第 %d 集"))
    }
}
