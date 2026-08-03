package com.aiyifan.app.feature.tv

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFlavorContractTest {

    @Test
    fun `tv manifest declares a leanback launcher without mobile only components`() {
        val manifest = Files.newBufferedReader(tvManifest().toPath()).use { it.readText() }

        assertTrue(manifest.contains("android.software.leanback"))
        assertTrue(manifest.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue(manifest.contains(".feature.tv.TvMainActivity"))
        assertFalse(manifest.contains("LoginActivity"))
    }

    private fun tvManifest(): File = sequenceOf(
        File("src/tv/AndroidManifest.xml"),
        File("app/src/tv/AndroidManifest.xml"),
    ).firstOrNull(File::isFile) ?: File("src/tv/AndroidManifest.xml")
}
