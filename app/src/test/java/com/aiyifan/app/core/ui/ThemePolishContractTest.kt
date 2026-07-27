package com.aiyifan.app.core.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ThemePolishContractTest {

    @Test
    fun `day and night palettes define semantic foreground colors`() {
        listOf("values", "values-night").forEach { directory ->
            assertTrue(colorNames(directory).containsAll(setOf("text_primary", "text_secondary", "text_on_accent")))
        }
    }

    @Test
    fun `primary and outline buttons have eight dp rounded corners`() {
        assertEquals("8dp", cornerRadius("bg_button_primary"))
        assertEquals("8dp", cornerRadius("bg_button_outline"))
    }

    @Test
    fun `mine no longer contains guest login affordances`() {
        val mine = layout("fragment_mine").readText()
        assertFalse(mine.contains("profileCard"))
        assertFalse(mine.contains("loginButton"))
    }

    @Test
    fun `simple list overlays a centered illustrated empty state`() {
        val root = root(layout("activity_simple_list"))
        assertEquals("FrameLayout", root.tagName)
        assertEquals("center", view(root, "emptyState").getAttribute("android:gravity"))
        assertEquals("ImageView", view(root, "emptyArtwork").tagName)
    }

    @Test
    fun `critical text buttons reserve forty four dp height`() {
        assertEquals("44dp", view(root(layout("item_video_card")), "playButton").getAttribute("android:layout_height"))
        val player = root(layout("activity_video_player"))
        assertEquals("44dp", view(player, "favoriteButton").getAttribute("android:layout_height"))
        assertEquals("44dp", view(player, "shareButton").getAttribute("android:layout_height"))
    }

    @Test
    fun `main status inset is backed by the semantic surface`() {
        assertEquals("@color/surface", view(root(layout("activity_main")), "fragmentContainer").getAttribute("android:background"))
    }

    private fun colorNames(directory: String): Set<String> = root(resource("$directory/colors.xml"))
        .getElementsByTagName("color")
        .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element }.map { it.getAttribute("name") }.toSet() }

    private fun cornerRadius(name: String): String = root(resource("drawable/$name.xml"))
        .getElementsByTagName("corners")
        .item(0)
        .let { it as Element }
        .getAttribute("android:radius")

    private fun layout(name: String): File = resource("layout/$name.xml")

    private fun resource(path: String): File = sequenceOf(
        File("src/main/res/$path"),
        File("app/src/main/res/$path"),
    ).first(File::isFile)

    private fun root(file: File): Element = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

    private fun view(root: Element, id: String): Element =
        (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element }
            .first { it.getAttribute("android:id").substringAfter("@+id/") == id }
}
