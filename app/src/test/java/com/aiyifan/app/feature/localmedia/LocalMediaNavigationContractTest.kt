package com.aiyifan.app.feature.localmedia

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalMediaNavigationContractTest {

    @Test
    fun `main activity maps the second tab to the local media fragment`() {
        val source = sourceFile("feature/main/MainActivity.kt").readText()

        assertTrue(source.contains("1 -> LocalMediaFragment()"))
        assertTrue(source.contains("import com.aiyifan.app.feature.localmedia.LocalMediaFragment"))
    }

    @Test
    fun `main layout labels the second tab as local media`() {
        val root = parseLayout(layoutFile("activity_main.xml"))
        val tabItems = elements(root).filter { it.tagName == "com.google.android.material.tabs.TabItem" }.toList()

        assertEquals(3, tabItems.size)
        assertEquals("@string/local_media_tab", tabItems[1].getAttribute("android:text"))
    }

    @Test
    fun `mine fragment opens hot activity from a hot row below collections`() {
        val source = sourceFile("feature/mine/MineFragment.kt").readText()
        val layout = parseLayout(layoutFile("fragment_mine.xml"))

        assertTrue(source.contains("binding.hotButton"))
        assertTrue(source.contains("HotActivity::class.java"))

        val contentGroup = viewWithId(layout, "contentGroup")
        val hotRow = viewWithId(layout, "hotButton")
        val collectionRow = viewWithId(layout, "collectionButton")

        assertEquals("@string/tab_hot", childText(hotRow))
        assertTrue(indexOf(layout, hotRow) > indexOf(layout, collectionRow))
        assertEquals("contentGroup", (hotRow.parentNode as Element).getAttribute("android:id").substringAfter("@+id/"))
        assertEquals("contentGroup", (collectionRow.parentNode as Element).getAttribute("android:id").substringAfter("@+id/"))
    }

    @Test
    fun `manifest declares scoped video read permissions`() {
        val manifest = manifestFile().readText()

        assertTrue(manifest.contains("android.permission.READ_MEDIA_VIDEO"))
        assertTrue(manifest.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertTrue(manifest.contains("android:maxSdkVersion=\"32\""))
    }

    private fun childText(row: Element): String {
        return (0 until row.getElementsByTagName("*").length)
            .asSequence()
            .map(row.getElementsByTagName("*")::item)
            .map { it as Element }
            .firstOrNull { it.tagName == "TextView" }
            ?.getAttribute("android:text")
            .orEmpty()
    }

    private fun indexOf(root: Element, target: Element): Int =
        elements(root).indexOf(target)

    private fun elements(root: Element): Sequence<Element> =
        (0 until root.getElementsByTagName("*").length)
            .asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element }

    private fun viewWithId(root: Element, id: String): Element =
        elements(root).first { it.getAttribute("android:id").substringAfter("@+id/") == id }



    private fun manifestFile(): File = sequenceOf(
        File("src/main/AndroidManifest.xml"),
        File("app/src/main/AndroidManifest.xml"),
    ).first(File::isFile)

    private fun layoutFile(name: String): File = sequenceOf(
        File("src/main/res/layout/$name"),
        File("app/src/main/res/layout/$name"),
    ).first(File::isFile)

    private fun parseLayout(file: File): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)
        .documentElement

    private fun sourceFile(path: String): File = sequenceOf(
        File("src/main/java/com/aiyifan/app/$path"),
        File("app/src/main/java/com/aiyifan/app/$path"),
    ).first(File::isFile)
}