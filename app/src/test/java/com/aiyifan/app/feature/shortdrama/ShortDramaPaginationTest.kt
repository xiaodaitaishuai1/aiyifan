package com.aiyifan.app.feature.shortdrama

import com.aiyifan.app.core.data.HomeVideoPage
import com.aiyifan.app.core.model.VideoSummary
import org.junit.Assert.*
import org.junit.Test

class ShortDramaPaginationTest {
    private fun page(vararg ids: String, more: Boolean = true) = HomeVideoPage(
        ids.map { VideoSummary(it, title = it, coverUrl = "", videoType = 0) }, more,
    )

    @Test fun `failure retries same page and append deduplicates`() {
        val state = ShortDramaPagination()
        state.complete(state.begin(refresh = true)!!, page("a"))
        val failed = state.begin()!!
        assertNull(state.begin())
        state.fail(failed)
        val retry = state.begin()!!
        assertEquals(2, retry.page)
        state.complete(retry, page("a", "b", more = false))
        assertEquals(listOf("a", "b"), state.videos.map { it.mediaKey })
        assertNull(state.begin())
    }

    @Test fun `refresh rejects stale completion and failure`() {
        val state = ShortDramaPagination()
        state.complete(state.begin(true)!!, page("old"))
        val old = state.begin()!!
        val fresh = state.begin(true)!!
        assertFalse(state.complete(old, page("stale")))
        assertFalse(state.fail(old))
        assertTrue(state.loading)
        state.complete(fresh, page("new"))
        assertEquals(listOf("new"), state.videos.map { it.mediaKey })
        assertEquals(2, state.begin()!!.page)
    }

    @Test fun `failed refresh keeps visible data but retries first page`() {
        val state = ShortDramaPagination()
        state.complete(state.begin(true)!!, page("old"))
        state.fail(state.begin(true)!!)
        assertEquals(listOf("old"), state.videos.map { it.mediaKey })
        assertEquals(1, state.begin()!!.page)
    }
}
