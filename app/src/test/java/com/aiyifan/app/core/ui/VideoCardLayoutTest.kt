package com.aiyifan.app.core.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class VideoCardLayoutTest {

    @Test
    fun `every list poster overlays the shared bottom scrim`() {
        listOf("item_home_video", "item_video_card", "item_search_result").forEach { name ->
            assertEquals(true, layoutFile(name).readText().contains("@drawable/bg_poster_bottom_scrim"))
        }
        assertEquals(true, drawableFile("bg_poster_bottom_scrim").readText().contains("android:startColor=\"#E6000000\""))
    }

    @Test
    fun `play button preserves its orange drawable and semantic accent label`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layoutFile())
        val playButton = viewWithId(document.documentElement, "playButton")!!

        assertEquals("@color/text_on_accent", playButton.getAttribute("android:textColor"))
        assertEquals("@null", playButton.getAttribute("app:backgroundTint"))
    }

    private fun layoutFile(name: String = "item_video_card"): File = sequenceOf(
        File("src/main/res/layout/$name.xml"),
        File("app/src/main/res/layout/$name.xml"),
    ).first(File::isFile)

    private fun drawableFile(name: String): File = sequenceOf(
        File("src/main/res/drawable/$name.xml"),
        File("app/src/main/res/drawable/$name.xml"),
    ).first(File::isFile)

    private fun viewWithId(root: Element, id: String): Element? =
        (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element }
            .firstOrNull {
                it.getAttribute("android:id").substringAfter("@+id/", "") == id
            }
}
