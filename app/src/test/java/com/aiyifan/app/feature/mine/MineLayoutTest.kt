package com.aiyifan.app.feature.mine

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

class MineLayoutTest {

    @Test
    fun `mine screen presents profile and grouped navigation`() {
        val root = parseLayout()

        assertEquals("@string/hardcoded_text_037", viewWithId(root, "mineTitle").getAttribute("android:text"))
        assertEquals("LinearLayout", viewWithId(root, "contentGroup").tagName)
        assertEquals("LinearLayout", viewWithId(root, "settingsGroup").tagName)
        assertEquals("LinearLayout", viewWithId(root, "themeSettingsButton").tagName)
        assertEquals("TextView", viewWithId(root, "themeSettingsSummary").tagName)
        assertFalse(hasViewWithId(root, "profileCard"))
        assertFalse(hasViewWithId(root, "loginButton"))
        listOf("historyButton", "collectionButton", "proxySettingsButton").forEach { id ->
            assertEquals("LinearLayout", viewWithId(root, id).tagName)
        }
    }

    private fun parseLayout(): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(layoutFile())
        .documentElement

    private fun layoutFile(): File = sequenceOf(
        File("src/main/res/layout/fragment_mine.xml"),
        File("app/src/main/res/layout/fragment_mine.xml"),
    ).first(File::isFile)

    private fun viewWithId(root: Element, id: String): Element =
        (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element }
            .first { it.getAttribute("android:id").substringAfter("@+id/") == id }

    private fun hasViewWithId(root: Element, id: String): Boolean =
        (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element }
            .any { it.getAttribute("android:id").substringAfter("@+id/") == id }
}
