package com.aiyifan.app.core.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectNetworkingContractTest {

    @Test
    fun `catalog and playback are wired for direct networking`() {
        val graph = appFile("src/main/java/com/aiyifan/app/core/data/AppGraph.kt").readText()
        val resolver = appFile("src/main/java/com/aiyifan/app/core/data/remote/RemoteConfigResolver.kt").readText()
        val playback = appFile("src/main/java/com/aiyifan/app/feature/video/VideoPlaybackController.kt").readText()

        assertTrue(graph.contains("UrlConnectionHttpFetcher()"))
        assertFalse(graph.contains("proxy"))
        assertFalse(graph.contains("Proxy"))
        assertFalse(resolver.contains("Proxy"))
        assertFalse(resolver.contains("endpointProvider"))
        assertFalse(playback.contains("Proxy"))
        assertFalse(playback.contains("proxyEndpointProvider"))
    }

    @Test
    fun `application has no VPN implementation or entry points`() {
        assertFalse(appPathExists("src/main/java/com/aiyifan/app/feature/proxy"))
        assertFalse(appPathExists("src/main/java/com/aiyifan/app/feature/home/HomeVpnQuickConnectPresentation.kt"))
        assertFalse(appPathExists("src/main/res/layout/activity_proxy_settings.xml"))
        assertFalse(appPathExists("src/main/res/drawable/ic_network.xml"))

        val manifest = appFile("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("feature.proxy"))
        assertFalse(manifest.contains("FOREGROUND_SERVICE_SPECIAL_USE"))
        assertFalse(manifest.contains("CHANGE_NETWORK_STATE"))
        assertFalse(manifest.contains("ACCESS_NETWORK_STATE"))
    }

    @Test
    fun `layouts resources and build do not retain VPN configuration`() {
        val homeLayout = appFile("src/main/res/layout/fragment_home.xml").readText()
        val mineLayout = appFile("src/main/res/layout/fragment_mine.xml").readText()
        val strings = appFile("src/main/res/values/strings.xml").readText()
        val build = projectFile("app/build.gradle.kts").readText()
        val submodules = projectFileCandidates(".gitmodules").firstOrNull(File::isFile)?.readText().orEmpty()
        val gitignore = projectFile(".gitignore").readText()

        assertFalse(homeLayout.contains("vpnQuickConnectButton"))
        assertFalse(mineLayout.contains("proxySettingsButton"))
        assertFalse(strings.contains("home_vpn"))
        assertFalse(strings.contains("网络代理"))
        assertFalse(build.contains("libbox"))
        assertFalse(build.contains("sing-box"))
        assertFalse(appPathExists("libs/libbox.aar"))
        assertFalse(projectPathExists("tools/build-libbox.ps1"))
        assertFalse(projectPathExists("third_party/sing-box"))
        assertFalse(projectPathExists("docs/licenses/sing-box-GPL-3.0-or-later.txt"))
        assertFalse(projectPathExists("docs/licenses/README.md"))
        assertFalse(gitignore.contains("libbox.aar"))
        assertFalse(submodules.contains("sing-box"))
    }

    private fun appFile(path: String): File = appFileCandidates(path).first(File::isFile)

    private fun appPathExists(path: String): Boolean = appFileCandidates(path).any(File::exists)

    private fun appFileCandidates(path: String): List<File> = listOf(File("app/$path"), File(path))

    private fun projectFile(path: String): File = projectFileCandidates(path).first(File::isFile)

    private fun projectPathExists(path: String): Boolean = projectFileCandidates(path).any(File::exists)

    private fun projectFileCandidates(path: String): List<File> = listOf(File(path), File("../$path"))
}
