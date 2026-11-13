package com.aiyifan.app.core.data

import com.aiyifan.app.core.model.ShortDramaCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortDramaCategoryContractTest {
    @Test
    fun `category preserves navigation fields`() {
        val category = ShortDramaCategory("19", "喜剧", 1, 2, "")

        assertEquals("19", category.id)
        assertEquals("喜剧", category.name)
        assertEquals(1, category.classType)
        assertEquals(2, category.index)
    }

    @Test
    fun `fake repository exposes no remote short drama categories`() = runBlocking {
        assertTrue(FakeCatalogRepository().getShortDramaCategories().isEmpty())
    }

    @Test
    fun `category page defaults to existing short drama page`() = runBlocking {
        val category = ShortDramaCategory("19", "喜剧", 1, 2)
        val page = FakeCatalogRepository().getShortDramaPage(category, 1)

        assertTrue(page.videos.isEmpty())
        assertTrue(!page.hasMore)
    }
}
