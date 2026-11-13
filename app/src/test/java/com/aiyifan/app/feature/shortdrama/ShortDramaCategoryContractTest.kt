package com.aiyifan.app.feature.shortdrama

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShortDramaCategoryContractTest {
    @Test
    fun `short drama layout exposes horizontal category container`() {
        val source = File("src/main/res/layout/fragment_short_drama.xml").readText()
        assertTrue(source.contains("@+id/dramaCategoryContainer"))
        assertTrue(source.contains("HorizontalScrollView"))
    }

    @Test
    fun `short drama fragment loads categories and guards current request`() {
        val source = File("src/main/java/com/aiyifan/app/feature/shortdrama/ShortDramaFragment.kt").readText()
        assertTrue(source.contains("getShortDramaCategories()"))
        assertTrue(source.contains("getShortDramaPage(category"))
        assertTrue(source.contains("requestVersion"))
        assertTrue(source.contains("selectedCategory?.id == categoryId"))
    }
}
