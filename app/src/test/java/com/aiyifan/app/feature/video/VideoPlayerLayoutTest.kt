package com.aiyifan.app.feature.video

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

class VideoPlayerLayoutTest {

    @Test
    fun `video navigation controls are outside the player surface`() {
        val document = videoPlayerLayout()
        val toolbar = viewWithId(document, "playerTopBar")

        assertNotNull(toolbar)
        assertEquals("backButton", viewWithId(toolbar!!, "backButton")?.idName())
        assertEquals("fullScreenButton", viewWithId(toolbar, "fullScreenButton")?.idName())
        assertEquals("floatingWindowButton", viewWithId(toolbar, "floatingWindowButton")?.idName())
        assertFalse(viewWithId(viewWithId(document, "playerContainer")!!, "backButton") != null)
    }

    @Test
    fun `favorite and share buttons use a readable label color`() {
        val document = videoPlayerLayout()

        assertEquals("@color/text_primary", viewWithId(document, "favoriteButton")!!.getAttribute("android:textColor"))
        assertEquals("@color/text_primary", viewWithId(document, "shareButton")!!.getAttribute("android:textColor"))
    }

    @Test
    fun `detail top bar is light and full screen exit button overlays the player`() {
        val document = videoPlayerLayout()
        val topBar = viewWithId(document, "playerTopBar")!!
        val playerContainer = viewWithId(document, "playerContainer")!!

        assertEquals("@color/surface", topBar.getAttribute("android:background"))
        assertEquals("@drawable/ic_back", viewWithId(topBar, "backButton")!!.getAttribute("android:src"))
        assertEquals("fullscreenExitButton", viewWithId(playerContainer, "fullscreenExitButton")!!.idName())
    }

    @Test
    fun `quality button overlays player and starts hidden`() {
        val button = viewWithId(viewWithId(videoPlayerLayout(), "playerContainer")!!, "fullScreenQualityButton")!!

        assertEquals("gone", button.getAttribute("android:visibility"))
        assertEquals("bottom|end", button.getAttribute("android:layout_gravity"))
    }

    private fun videoPlayerLayout() = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(layoutFile())

    private fun layoutFile(): File = sequenceOf(
        File("src/main/res/layout/activity_video_player.xml"),
        File("app/src/main/res/layout/activity_video_player.xml"),
    ).first(File::isFile)

    private fun viewWithId(root: Element, id: String): Element? =
        (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element }
            .firstOrNull { it.idName() == id }

    private fun viewWithId(document: org.w3c.dom.Document, id: String): Element? =
        viewWithId(document.documentElement, id)

    private fun Element.idName(): String? =
        getAttribute("android:id").substringAfter("@+id/", missingDelimiterValue = "")
            .ifBlank { null }
}
