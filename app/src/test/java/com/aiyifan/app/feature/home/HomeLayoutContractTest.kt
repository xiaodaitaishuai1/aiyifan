package com.aiyifan.app.feature.home

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class HomeLayoutContractTest {

    @Test
    fun `home banner reserves a sixteen by seven frame and overlays its title`() {
        val root = root(layout("item_home_banner"))

        assertEquals("16", view(root, "bannerFrame").getAttribute("app:ratioWidth"))
        assertEquals("7", view(root, "bannerFrame").getAttribute("app:ratioHeight"))
        assertEquals("@+id/bannerTitle", view(root, "bannerTitle").getAttribute("android:id"))
    }

    @Test
    fun `home card reserves a sixteen by nine frame and limits title lines`() {
        val root = root(layout("item_home_video"))

        assertEquals("16", view(root, "cardFrame").getAttribute("app:ratioWidth"))
        assertEquals("9", view(root, "cardFrame").getAttribute("app:ratioHeight"))
        assertEquals("2", view(root, "cardTitle").getAttribute("android:maxLines"))
    }

    @Test
    fun `home fragment configures a two column grid and leaves hot list untouched`() {
        val home = source("feature/home/HomeFragment.kt").readText()

        assertTrue(home.contains("GridLayoutManager(requireContext(), 2)"))
        assertTrue(home.contains("HomeVideoAdapter"))
        assertFalse(home.contains("LinearLayoutManager(requireContext())"))
        assertTrue(source("feature/hot/HotFragment.kt").readText().contains("LinearLayoutManager(requireContext())"))
    }

    private fun layout(name: String): File = sequenceOf(
        File("src/main/res/layout/$name.xml"),
        File("app/src/main/res/layout/$name.xml"),
    ).first(File::isFile)

    private fun root(file: File): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)
        .documentElement

    private fun source(path: String): File = sequenceOf(
        File("src/main/java/com/aiyifan/app/$path"),
        File("app/src/main/java/com/aiyifan/app/$path"),
    ).first(File::isFile)

    private fun view(root: Element, id: String): Element =
        (sequenceOf(root) + (0 until root.getElementsByTagName("*").length).asSequence()
            .map(root.getElementsByTagName("*")::item)
            .map { it as Element })
            .first { it.getAttribute("android:id").substringAfter("@+id/") == id }
}
