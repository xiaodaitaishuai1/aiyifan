package com.aiyifan.app.feature.hot

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HotFragmentRefreshContractTest {

    @Test
    fun `hot page refreshes through a shared loader after a proxy connection`() {
        val source = hotFragmentSource()

        assertTrue(source.contains("override fun onStart()"))
        assertTrue(source.contains("override fun onStop()"))
        assertTrue(source.contains("private fun loadHot()"))
        assertTrue(source.contains("val requestVersion = ++hotRequestVersion"))
        assertTrue(source.contains("requestVersion == hotRequestVersion"))
        assertTrue(source.contains("proxyManager.addConnectionObserver"))
        assertTrue(source.contains("proxyManager.removeConnectionObserver"))
        assertTrue(source.contains("viewLifecycleOwner.lifecycleScope.launch { loadHot() }"))
        assertTrue(source.contains("loadHot()"))
    }

    @Test
    fun `hot request propagates cancellation before rendering failure`() {
        val source = hotFragmentSource()

        assertTrue(source.contains(".onFailure { error ->"))
        assertTrue(source.contains("if (error is CancellationException) throw error"))
    }

    private fun hotFragmentSource(): String = sequenceOf(
        File("src/main/java/com/aiyifan/app/feature/hot/HotFragment.kt"),
        File("app/src/main/java/com/aiyifan/app/feature/hot/HotFragment.kt"),
    ).first(File::isFile).readText()
}
