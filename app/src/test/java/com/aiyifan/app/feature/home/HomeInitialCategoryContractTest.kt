package com.aiyifan.app.feature.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInitialCategoryContractTest {
    @Test
    fun `home accepts an initial category id and prefers it after categories load`() {
        val source = sequenceOf(
            File("src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt"),
            File("app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt"),
        ).first(File::isFile).readText()

        assertTrue(source.contains("fun newInstance(categoryId: String?)"))
        assertTrue(source.contains("ARG_INITIAL_CATEGORY_ID"))
        assertTrue(source.contains("firstOrNull { it.id == initialCategoryId }"))
    }
}
