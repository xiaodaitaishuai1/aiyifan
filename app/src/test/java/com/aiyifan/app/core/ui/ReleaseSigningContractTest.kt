package com.aiyifan.app.core.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningContractTest {

    @Test
    fun `release signing has no literal secrets and reads external values`() {
        val script = buildScript().readText()

        assertTrue(script.contains("providers.gradleProperty"))
        assertTrue(script.contains("System.getenv"))
        assertFalse(script.contains("keyPassword = \"aiyifan\""))
        assertFalse(script.contains("storePassword = \"aiyifan\""))
    }

    private fun buildScript(): File = sequenceOf(
        File("app/build.gradle.kts"),
        File("build.gradle.kts"),
    ).first { it.isFile && it.readText().contains("com.android.application") }
}
