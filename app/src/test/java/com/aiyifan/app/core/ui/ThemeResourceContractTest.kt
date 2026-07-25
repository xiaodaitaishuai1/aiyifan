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
            "black",
            "outline",
            "field_bg",
            "chip_bg",
            "poster_placeholder",
        )
    }
}
