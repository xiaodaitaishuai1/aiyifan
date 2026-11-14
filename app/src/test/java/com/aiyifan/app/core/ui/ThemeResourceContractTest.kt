package com.aiyifan.app.core.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ThemeResourceContractTest {

    @Test
    fun `day and night palettes define the same semantic colors`() {
        val dayNames = colorNames("values")
        val nightNames = colorNames("values-night")

        assertEquals(dayNames, nightNames)
        assertTrue(requiredNames.all(dayNames::contains))
    }

    @Test
    fun `night palette uses layered dark surfaces`() {
        val nightColors = colorValues("values-night")

        assertEquals("#15181D", nightColors.getValue("primary"))
        assertEquals("#0B0D10", nightColors.getValue("page_bg"))
        assertEquals("#15181D", nightColors.getValue("surface"))
    }

    @Test
    fun `night palette uses a warm accent for selected controls`() {
        assertEquals("#FF8A3D", colorValues("values-night").getValue("accent"))
    }

    @Test
    fun `night palette uses high contrast primary and secondary text`() {
        val nightColors = colorValues("values-night")

        assertEquals("#F2F4F7", nightColors.getValue("text_primary"))
        assertEquals("#A0A7B2", nightColors.getValue("text_secondary"))
    }

    private fun colorNames(directory: String): Set<String> =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(resourceFile(directory))
            .getElementsByTagName("color")
            .let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) as Element }
                    .map { it.getAttribute("name") }
                    .toSet()
            }

    private fun colorValues(directory: String): Map<String, String> =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(resourceFile(directory))
            .getElementsByTagName("color")
            .let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) as Element }
                    .associate { it.getAttribute("name") to it.textContent.trim() }
            }

    private fun resourceFile(directory: String): File = sequenceOf(
        File("src/main/res/$directory/colors.xml"),
        File("app/src/main/res/$directory/colors.xml"),
    ).first(File::isFile)

    private companion object {
        val requiredNames = setOf(
            "primary",
            "page_bg",
            "surface",
            "text_primary",
            "text_secondary",
            "accent",
            "white",
            "outline",
            "field_bg",
            "chip_bg",
            "poster_placeholder",
        )
    }
}
