package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedPaginationTest {
    @Test
    fun `initial page keeps selected category first and fills to seven uniquely`() {
        val pager = HomeFeedPagination()
        val page = pager.reset(
            selected = listOf("a", "b", "c", "d", "e").map(::video),
            supplements = listOf(listOf("c", "f", "g", "h").map(::video)),
        )

        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g"), page.map { it.mediaKey })
    }

    @Test
    fun `next page appends six items and stops at the end`() {
        val pager = HomeFeedPagination()
        pager.reset((1..15).map { video("v$it") }, emptyList())

        assertEquals((1..13).map { "v$it" }, pager.next().map { it.mediaKey })
        assertTrue(pager.hasMore)
        assertEquals((1..15).map { "v$it" }, pager.next().map { it.mediaKey })
        assertFalse(pager.hasMore)
    }

    private fun video(key: String) = VideoSummary(key, title = key, coverUrl = "", videoType = 0)
}
