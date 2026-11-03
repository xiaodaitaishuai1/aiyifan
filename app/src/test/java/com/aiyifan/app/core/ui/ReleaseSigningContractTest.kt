package com.aiyifan.app.core.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningContractTest {

    @Test
    fun `release signing reads centralized app configuration without literal secrets`() {
        val script = buildScript().readText()

        assertTrue(script.contains("app-config.gradle"))
        assertTrue(script.contains("apply(from ="))
        assertTrue(script.contains("val STORE_PASSWORD: String by project.extra"))
        assertTrue(script.contains("val KEY_PASSWORD: String by project.extra"))
        assertFalse(script.contains("keyPassword = \"aiyifan\""))
        assertFalse(script.contains("storePassword = \"aiyifan\""))
    }

    @Test
    fun `app configuration example has empty passwords and local configuration is ignored`() {
        val root = requireNotNull(buildScript().canonicalFile.parentFile?.parentFile)
        val example = File(root, "app-config.gradle.example").readText()

        assertTrue(example.contains("STORE_PASSWORD = \"\""))
        assertTrue(example.contains("KEY_PASSWORD = \"\""))
        assertTrue(File(root, ".gitignore").readLines().contains("/app-config.gradle"))
    }

    private fun buildScript(): File = sequenceOf(
        File("app/build.gradle.kts"),
        File("build.gradle.kts"),
    ).first { it.isFile && it.readText().contains("com.android.application") }
}
