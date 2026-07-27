package com.aiyifan.app.core.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class ThemeStyleContractTest {

    @Test
    fun `day and night app themes inherit the Material DayNight parent`() {
        assertEquals("Theme.MaterialComponents.DayNight.NoActionBar", appThemeParent("values"))
        assertEquals("Theme.MaterialComponents.DayNight.NoActionBar", appThemeParent("values-night"))
    }

    private fun appThemeParent(directory: String): String =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(styleFile(directory))
            .getElementsByTagName("style")
            .let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) as Element }
                    .first { it.getAttribute("name") == "AppTheme" }
                    .getAttribute("parent")
            }

    private fun styleFile(directory: String): File = sequenceOf(
        File("src/main/res/$directory/styles.xml"),
        File("app/src/main/res/$directory/styles.xml"),
    ).first(File::isFile)
}
