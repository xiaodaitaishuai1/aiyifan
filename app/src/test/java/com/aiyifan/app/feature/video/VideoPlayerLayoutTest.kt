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
    fun `detail top bar is light and full screen title bar overlays the player`() {
        val document = videoPlayerLayout()
        val topBar = viewWithId(document, "playerTopBar")!!
        val playerContainer = viewWithId(document, "playerContainer")!!

        assertEquals("@color/surface", topBar.getAttribute("android:background"))
        assertEquals("@drawable/ic_back", viewWithId(topBar, "backButton")!!.getAttribute("android:src"))
        assertEquals("fullScreenTitleBar", viewWithId(playerContainer, "fullScreenTitleBar")!!.idName())
    }

    @Test
    fun `normal and full screen title bars show a single ellipsized video title`() {
        val document = videoPlayerLayout()
        val normalTopBar = viewWithId(document, "playerTopBar")!!
        val fullScreenTopBar = viewWithId(document, "fullScreenTitleBar")!!

        assertEquals("1", viewWithId(normalTopBar, "videoTitle")!!.getAttribute("android:maxLines"))
        assertEquals("end", viewWithId(normalTopBar, "videoTitle")!!.getAttribute("android:ellipsize"))
        assertEquals("fullScreenBackButton", viewWithId(fullScreenTopBar, "fullScreenBackButton")!!.idName())
        assertEquals("1", viewWithId(fullScreenTopBar, "fullScreenVideoTitle")!!.getAttribute("android:maxLines"))
        assertEquals("end", viewWithId(fullScreenTopBar, "fullScreenVideoTitle")!!.getAttribute("android:ellipsize"))
    }

    @Test
    fun `player has no quality selection button`() {
        assertEquals(null, viewWithId(videoPlayerLayout(), "fullScreenQualityButton"))
    }

    @Test
    fun `player offers an auto skip intro and outro switch`() {
        val autoSkipSwitch = viewWithId(videoPlayerLayout(), "autoSkipIntroOutroSwitch")

        assertNotNull(autoSkipSwitch)
        assertEquals("@string/auto_skip_intro_outro", autoSkipSwitch!!.getAttribute("android:text"))
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
