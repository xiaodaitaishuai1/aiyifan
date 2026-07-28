package com.aiyifan.app.feature.home

import com.aiyifan.app.core.data.HomeVideoPage
import com.aiyifan.app.core.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HomeFeedPaginationTest {
    @Test
    fun `next request starts at page two and appends only unique current category videos`() {
        val pager = HomeFeedPagination()
        val initial = pager.reset(HomeVideoPage(listOf("a", "b", "c", "d", "e", "f", "g").map(::video), hasMore = true))

        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g"), initial.map { it.mediaKey })
        assertEquals(2, pager.beginNextPage())
        assertNull(pager.beginNextPage())

        val merged = pager.append(
            requestedPage = 2,
            response = HomeVideoPage(listOf("g", "h", "i").map(::video), hasMore = true),
        )

        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i"), merged.map { it.mediaKey })
        assertEquals(3, pager.beginNextPage())
    }

    @Test
    fun `empty current category response stops further paging`() {
        val pager = HomeFeedPagination()
        pager.reset(HomeVideoPage(listOf(video("movie-1")), hasMore = true))
        val requestedPage = pager.beginNextPage()!!

        pager.append(requestedPage, HomeVideoPage(emptyList(), hasMore = false))

        assertFalse(pager.hasMore)
        assertNull(pager.beginNextPage())
    }

    private fun video(key: String) = VideoSummary(key, title = key, coverUrl = "", videoType = 0)
}
