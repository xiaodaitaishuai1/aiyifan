package com.aiyifan.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBannerCarouselPolicyTest {

    @Test
    fun `next page wraps to first page after the final page`() {
        assertEquals(0, HomeBannerCarouselPolicy.nextPage(currentPage = 4, pageCount = 5))
    }

    @Test
    fun `next page advances while another page exists`() {
        assertEquals(3, HomeBannerCarouselPolicy.nextPage(currentPage = 2, pageCount = 5))
    }

    @Test
    fun `automatic scrolling only runs for multiple visible pages`() {
        assertFalse(HomeBannerCarouselPolicy.canAutoScroll(pageCount = 1, isVisible = true))
        assertFalse(HomeBannerCarouselPolicy.canAutoScroll(pageCount = 2, isVisible = false))
        assertTrue(HomeBannerCarouselPolicy.canAutoScroll(pageCount = 2, isVisible = true))
    }
}
