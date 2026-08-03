package com.aiyifan.app.feature.tv

import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class TvFlavorContractTest {

    @Test
    fun `tv manifest declares a leanback launcher without mobile only components`() {
        val manifestFile = tvManifest()
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(manifestFile)
        val application = document.elements("application").single()
        val tvActivity = document.elements("activity")
            .single { it.getAttribute("android:name") == ".feature.tv.TvMainActivity" }

        assertEquals("@drawable/tv_launcher_banner", application.getAttribute("android:banner"))
        assertEquals("true", document.feature("android.software.leanback").getAttribute("android:required"))
        assertEquals("false", document.feature("android.hardware.touchscreen").getAttribute("android:required"))
        assertEquals("true", tvActivity.getAttribute("android:exported"))
        assertEquals("landscape", tvActivity.getAttribute("android:screenOrientation"))
        assertTrue(tvActivity.elements("action").any { it.getAttribute("android:name") == "android.intent.action.MAIN" })
        assertTrue(
            tvActivity.elements("category")
                .any { it.getAttribute("android:name") == "android.intent.category.LEANBACK_LAUNCHER" },
        )
        assertFalse(document.elements("activity").any { it.getAttribute("android:name").endsWith("LoginActivity") })
        assertFalse(document.hasPermission("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(document.hasPermission("android.permission.WRITE_SETTINGS"))
        assertFalse(document.hasPermission("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        assertFalse(document.elements("service").any { it.getAttribute("android:name").endsWith("FloatingPlayerService") })
        assertFalse(
            document.elements("service")
                .any { it.getAttribute("android:foregroundServiceType").split("|").contains("mediaPlayback") },
        )
        assertFalse(document.elements("activity").any { it.hasAttribute("android:supportsPictureInPicture") })
        assertEquals("爱壹帆 TV", tvStringsDocument().stringValue("app_name"))

        val banner = tvLauncherBanner()
        assertTrue(banner.isFile)
        val bannerBytes = Files.readAllBytes(banner.toPath())
        assertTrue(bannerBytes.copyOfRange(0, pngSignature.size).contentEquals(pngSignature))
        assertTrue(bannerBytes.copyOfRange(12, 16).contentEquals(ihdrChunkType))
        assertEquals(320, pngDimension(bannerBytes, 16))
        assertEquals(180, pngDimension(bannerBytes, 20))
        assertFalse(legacyTvLauncherVector().isFile)
    }

    private fun tvManifest(): File = sequenceOf(
        File("src/tv/AndroidManifest.xml"),
        File("app/src/tv/AndroidManifest.xml"),
    ).firstOrNull(File::isFile) ?: File("src/tv/AndroidManifest.xml")

    private fun tvStrings(): File = sequenceOf(
        File("src/tv/res/values/strings.xml"),
        File("app/src/tv/res/values/strings.xml"),
    ).first(File::isFile)

    private fun tvStringsDocument(): Document = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(tvStrings())

    private fun tvLauncherBanner(): File = sequenceOf(
        File("src/tv/res/drawable-nodpi/tv_launcher_banner.png"),
        File("app/src/tv/res/drawable-nodpi/tv_launcher_banner.png"),
    ).firstOrNull(File::isFile) ?: File("src/tv/res/drawable-nodpi/tv_launcher_banner.png")

    private fun legacyTvLauncherVector(): File = sequenceOf(
        File("src/tv/res/drawable/tv_launcher_banner.xml"),
        File("app/src/tv/res/drawable/tv_launcher_banner.xml"),
    ).firstOrNull(File::isFile) ?: File("src/tv/res/drawable/tv_launcher_banner.xml")

    private fun Document.feature(name: String): Element = elements("uses-feature")
        .single { it.getAttribute("android:name") == name }

    private fun Document.stringValue(name: String): String = elements("string")
        .single { it.getAttribute("name") == name }
        .textContent

    private fun Document.hasPermission(name: String): Boolean = elements("uses-permission")
        .any { it.getAttribute("android:name") == name }

    private fun Document.elements(tagName: String): List<Element> =
        (0 until getElementsByTagName(tagName).length).map { index ->
            getElementsByTagName(tagName).item(index) as Element
        }

    private fun Element.elements(tagName: String): List<Element> =
        (0 until getElementsByTagName(tagName).length).map { index ->
            getElementsByTagName(tagName).item(index) as Element
        }

    private fun pngDimension(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private companion object {
        val pngSignature = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4e,
            0x47,
            0x0d,
            0x0a,
            0x1a,
            0x0a,
        )
        val ihdrChunkType = byteArrayOf(0x49, 0x48, 0x44, 0x52)
    }
}
