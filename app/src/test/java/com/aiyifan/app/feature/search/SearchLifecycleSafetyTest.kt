package com.aiyifan.app.feature.search

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLifecycleSafetyTest {

    @Test
    fun `suggestion and search requests propagate cancellation`() {
        val source = searchSource()

        assertTrue(source.contains("searchSuggestions(keyword)"))
        assertTrue(source.contains("searchVideos(keyword)"))
        assertTrue(source.contains(".onFailure { error ->"))
        assertTrue(source.contains("if (error is CancellationException) throw error"))
    }

    private fun searchSource(): String = sequenceOf(
        File("src/main/java/com/aiyifan/app/feature/search/SearchActivity.kt"),
        File("app/src/main/java/com/aiyifan/app/feature/search/SearchActivity.kt"),
    ).first(File::isFile).readText()
}
