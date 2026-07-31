package com.aiyifan.app.feature.video

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

class FloatingPlayerLayoutTest {

    @Test
    fun `floating player renders an opaque black surface`() {
        val layout = floatingPlayerLayout()
        val playerView = viewWithId(layout, "floatingPlayerView")!!

        assertEquals("@android:color/black", playerView.getAttribute("android:background"))
        assertEquals("@android:color/black", playerView.getAttribute("app:shutter_background_color"))
        assertEquals("#FF000000", floatingPlayerBackground().trim())
    }

    @Test
    fun `floating player has a hidden edge restore handle`() {
        val restoreHandle = viewWithId(floatingPlayerLayout(), "floatingRestoreHandle")

        assertNotNull(restoreHandle)
        assertEquals("gone", restoreHandle!!.getAttribute("android:visibility"))
    }

    private fun floatingPlayerLayout() = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(layoutFile())

    private fun layoutFile(): File = sequenceOf(
        File("src/main/res/layout/view_floating_player.xml"),
        File("app/src/main/res/layout/view_floating_player.xml"),
    ).first(File::isFile)

    private fun floatingPlayerBackground(): String = sequenceOf(
        File("src/main/res/drawable/bg_floating_player.xml"),
        File("app/src/main/res/drawable/bg_floating_player.xml"),
    ).first(File::isFile).readText().substringAfter("android:color=\"").substringBefore("\"")

    private fun viewWithId(document: org.w3c.dom.Document, id: String): Element? =
        (0 until document.getElementsByTagName("*").length).asSequence()
            .map(document.getElementsByTagName("*")::item)
            .map { it as Element }
            .firstOrNull { element ->
                element.getAttribute("android:id") == "@+id/$id"
            }
}
