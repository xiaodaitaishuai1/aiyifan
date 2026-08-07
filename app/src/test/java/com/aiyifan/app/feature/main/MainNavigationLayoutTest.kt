package com.aiyifan.app.feature.main

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class MainNavigationLayoutTest {

    @Test
    fun `main navigation uses three fixed text tabs`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layoutFile())
        val tabLayout = viewWithId(document.documentElement, "bottomTabs")

        assertNotNull(tabLayout)
        assertEquals("fixed", tabLayout!!.getAttribute("app:tabMode"))
        assertEquals("fill", tabLayout.getAttribute("app:tabGravity"))
        assertEquals(3, tabLayout.getElementsByTagName("com.google.android.material.tabs.TabItem").length)
    }

    @Test
    fun `theme recreation restores the selected bottom tab`() {
        val activity = sourceFile("feature/main/MainActivity.kt").readText()

        assertTrue(activity.contains("KEY_SELECTED_TAB"))
        assertTrue(activity.contains("outState.putInt(KEY_SELECTED_TAB, binding.bottomTabs.selectedTabPosition)"))
        assertTrue(activity.contains("savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: 0"))
    }

    private fun layoutFile(): File = sequenceOf(
        File("src/main/res/layout/activity_main.xml"),
        File("app/src/main/res/layout/activity_main.xml"),
    ).first(File::isFile)

    private fun sourceFile(path: String): File = sequenceOf(
        File("src/main/java/com/aiyifan/app/$path"),
        File("app/src/main/java/com/aiyifan/app/$path"),
    ).first(File::isFile)

    private fun viewWithId(root: Element, id: String): Element? =
        (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element }
            .firstOrNull {
                it.getAttribute("android:id").substringAfter("@+id/", "") == id
            }
}
