package com.aiyifan.app.feature.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationContractTest {
    @Test
    fun `navigation activity loads categories and returns selected category to main`() {
        val source = sourceFile("src/main/java/com/aiyifan/app/feature/navigation/NavigationActivity.kt").readText()

        assertTrue(source.contains("repository.loadChannels()"))
        assertTrue(source.contains("NavigationResultsActivity.intent(this, channel, channel.tags[index])"))
        assertTrue(source.contains("finish()"))
    }

    @Test
    fun `navigation layout exposes category list and retry state`() {
        val layout = sourceFile("src/main/res/layout/activity_navigation.xml").readText()

        assertTrue(layout.contains("@+id/navigationRecycler"))
        assertTrue(layout.contains("@+id/navigationEmptyState"))
        assertTrue(layout.contains("@+id/navigationRetryButton"))
    }

    @Test
    fun `navigation handles empty categories and is registered as an internal activity`() {
        val source = sourceFile("src/main/java/com/aiyifan/app/feature/navigation/NavigationActivity.kt").readText()
        val manifest = sourceFile("src/main/AndroidManifest.xml").readText()

        assertTrue(source.contains("channels.isEmpty()"))
        assertTrue(source.contains("navigationRetryButton.setOnClickListener"))
        assertTrue(manifest.contains(".feature.navigation.NavigationActivity"))
    }

    private fun sourceFile(path: String): File = sequenceOf(File(path), File("app/$path")).first(File::isFile)
}
