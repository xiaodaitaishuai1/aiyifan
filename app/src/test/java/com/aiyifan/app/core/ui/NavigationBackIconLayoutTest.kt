package com.aiyifan.app.core.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class NavigationBackIconLayoutTest {

    @Test
    fun `every navigation back control uses the shared back icon`() {
        layoutNames.forEach { layoutName ->
            val backButton = viewWithId(parseLayout(layoutName), "backButton")

            assertEquals("ImageButton", backButton.tagName)
            assertEquals("@drawable/ic_back", backButton.getAttribute("android:src"))
            assertEquals("", backButton.getAttribute("android:text"))
        }
    }

    private fun parseLayout(layoutName: String): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(layoutFile(layoutName))
        .documentElement

    private fun layoutFile(layoutName: String): File = sequenceOf(
        File("src/main/res/layout/$layoutName.xml"),
        File("app/src/main/res/layout/$layoutName.xml"),
    ).first(File::isFile)

    private fun viewWithId(root: Element, id: String): Element =
        (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element }
            .first { it.getAttribute("android:id").substringAfter("@+id/") == id }

    private companion object {
        val layoutNames = listOf(
            "activity_login",
            "activity_search",
            "activity_simple_list",
            "activity_video_player",
        )
    }
}
