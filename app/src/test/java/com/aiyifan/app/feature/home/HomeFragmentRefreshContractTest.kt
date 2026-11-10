package com.aiyifan.app.feature.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFragmentRefreshContractTest {
    @Test
    fun `refresh keeps existing banner while request is in flight`() {
        val source = sourceFile().readText()
        val loadHome = source.substringAfter("private fun loadHome(").substringBefore("private fun refreshContinueWatching")

        assertFalse(loadHome.contains("bannerAdapter.submitList(emptyList())"))
    }

    @Test
    fun `category changes clear the previous banner before loading new content`() {
        val source = sourceFile().readText()
        val selectCategory = source.substringAfter("private fun selectCategory").substringBefore("private suspend fun submitHomeFeed")

        assertTrue(selectCategory.contains("bannerAdapter.submitList(emptyList())"))
    }

    private fun sourceFile(): File = sequenceOf(
        File("src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt"),
        File("app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt"),
    ).first(File::isFile)
}
