package com.aiyifan.app.feature.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFragmentLifecycleSafetyTest {

    @Test
    fun `home load failure does not require an attached fragment context`() {
        assertFalse(homeFragmentSource().contains("Toast.makeText(requireContext(), \"首页刷新失败\""))
    }

    @Test
    fun `home refreshes when a successful proxy connection is observed`() {
        val source = homeFragmentSource()

        assertTrue(source.contains("override fun onStart()"))
        assertTrue(source.contains("override fun onStop()"))
        assertTrue(source.contains("proxyManager.addConnectionObserver"))
        assertTrue(source.contains("proxyManager.removeConnectionObserver"))
        assertTrue(source.contains("viewLifecycleOwner.lifecycleScope.launch { loadHome() }"))
    }

    private fun homeFragmentSource(): String = sequenceOf(
        File("src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt"),
        File("app/src/main/java/com/aiyifan/app/feature/home/HomeFragment.kt"),
    ).first(File::isFile).readText()
}
