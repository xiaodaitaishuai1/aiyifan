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

        assertTrue(manifest.contains("android:banner=\"@drawable/tv_launcher_banner\""))
        assertTrue(manifest.contains("android.software.leanback"))
        assertTrue(manifest.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue(manifest.contains(".feature.tv.TvMainActivity"))
        assertTrue(
            manifest.contains(
                "android:name=\"android.hardware.touchscreen\"\n        android:required=\"false\"",
            ),
        )
        assertTrue(
            manifest.contains(
                "android:name=\".feature.tv.TvMainActivity\"\n            android:exported=\"true\"\n            android:screenOrientation=\"landscape\"",
            ),
        )
        assertFalse(manifest.contains("LoginActivity"))
        assertFalse(manifest.contains("SYSTEM_ALERT_WINDOW"))
        assertFalse(manifest.contains("WRITE_SETTINGS"))
        assertFalse(manifest.contains("FloatingPlayerService"))
        assertFalse(manifest.contains("android:foregroundServiceType=\"mediaPlayback\""))
        assertFalse(manifest.contains("supportsPictureInPicture"))
        assertTrue(tvStrings().readText().contains("<string name=\"app_name\">爱壹帆 TV</string>"))
    }

    private fun tvManifest(): File = sequenceOf(
        File("src/tv/AndroidManifest.xml"),
        File("app/src/tv/AndroidManifest.xml"),
    ).firstOrNull(File::isFile) ?: File("src/tv/AndroidManifest.xml")

    private fun tvStrings(): File = sequenceOf(
        File("src/tv/res/values/strings.xml"),
        File("app/src/tv/res/values/strings.xml"),
    ).first(File::isFile)
}
