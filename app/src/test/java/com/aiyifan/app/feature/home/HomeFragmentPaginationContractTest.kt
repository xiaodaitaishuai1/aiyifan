package com.aiyifan.app.feature.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFragmentPaginationContractTest {

    @Test
    fun `home scroll requests the next page for the selected category`() {
        val source = homeFragmentSource()

        assertTrue(source.contains("pagination.beginNextPage()"))
        assertTrue(source.contains("repository.getHomeVideoPage(category, page)"))
        assertTrue(source.contains("val requestVersion = homeRequestVersion"))
        assertTrue(source.contains("isCurrentRequest(requestVersion) && selectedCategory?.id == categoryId"))
        assertTrue(source.contains("homeRefresh.setColorSchemeResources(R.color.accent)"))
        assertTrue(source.contains("adapter.setLoadMoreLoading(true)"))
        assertTrue(source.contains("recyclerView.post {"))
        assertTrue(source.contains("loadNextPage(category, page, requestVersion)"))
        assertFalse(source.contains("pagination.next()"))
    }

    private fun homeFragmentSource(): String = sequenceOf(
        File("src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt"),
        File("app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt"),
    ).first(File::isFile).readText()
}
