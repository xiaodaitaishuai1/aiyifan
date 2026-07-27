package com.aiyifan.app.core.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class VideoCardLayoutTest {

    @Test
    fun `play button preserves its orange drawable and semantic accent label`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layoutFile())
        val playButton = viewWithId(document.documentElement, "playButton")!!

        assertEquals("@color/text_on_accent", playButton.getAttribute("android:textColor"))
        assertEquals("@null", playButton.getAttribute("app:backgroundTint"))
    }

    private fun layoutFile(): File = sequenceOf(
        File("src/main/res/layout/item_video_card.xml"),
        File("app/src/main/res/layout/item_video_card.xml"),
    ).first(File::isFile)

    private fun viewWithId(root: Element, id: String): Element? =
        (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element }
            .firstOrNull {
                it.getAttribute("android:id").substringAfter("@+id/", "") == id
            }
}
