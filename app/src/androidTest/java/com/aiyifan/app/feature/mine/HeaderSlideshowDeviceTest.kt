package com.aiyifan.app.feature.mine

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.ImageButton
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiyifan.app.R
import com.aiyifan.app.feature.main.MainActivity
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderSlideshowDeviceTest {
    private fun state(activity: MainActivity): HeaderSlideshow {
        val fragment = activity.supportFragmentManager.findFragmentByTag(MineFragment::class.java.name)!!
        return MineFragment::class.java.getDeclaredField("slideshow").apply { isAccessible = true }
            .get(fragment) as HeaderSlideshow
    }

    private fun awaitImage(scenario: ActivityScenario<MainActivity>) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < end) {
            var ready = false
            scenario.onActivity { ready = it.findViewById<ImageButton>(R.id.headerExpand).isEnabled }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("Header image did not load")
    }

    @Test fun pauseRecreateTabsAndViewerCloseKeepState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.findViewById<TabLayout>(R.id.bottomTabs).getTabAt(2)!!.select()
            }
            awaitImage(scenario)
            scenario.onActivity {
                assertFalse(it.findViewById<HeaderSlideView>(R.id.headerImage).performClick())
                assertFalse(it.findViewById<HeaderSlideView>(R.id.headerNextImage).performClick())
                val header = it.findViewById<android.view.View>(R.id.mineHeader)
                val topInset = androidx.core.view.ViewCompat.getRootWindowInsets(header)!!.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                        androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
                ).top
                assertEquals(it.resources.getDimensionPixelSize(R.dimen.mine_header_height) + topInset, header.height)
                it.findViewById<ImageButton>(R.id.headerPlayback).performClick()
            }
            var index = -1
            scenario.onActivity { index = state(it).index; assertTrue(state(it).paused) }
            SystemClock.sleep(5500)
            scenario.onActivity { assertEquals(index, state(it).index) }
            scenario.recreate()
            awaitImage(scenario)
            scenario.onActivity { assertEquals(index, state(it).index); assertTrue(state(it).paused) }
            repeat(5) {
                val monitor = instrumentation.addMonitor(HeaderImageActivity::class.java.name, null, false)
                scenario.onActivity { it.findViewById<ImageButton>(R.id.headerExpand).performClick() }
                val viewer = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
                assertNotNull("Viewer must open", viewer)
                instrumentation.runOnMainSync { viewer.finish() }
                instrumentation.removeMonitor(monitor)
                instrumentation.waitForIdleSync()
                awaitImage(scenario)
            }
            scenario.onActivity {
                assertTrue(state(it).paused)
                it.findViewById<TabLayout>(R.id.bottomTabs).getTabAt(0)!!.select()
                assertFalse(it.findViewById<HeaderSlideView>(R.id.headerImage).hasImage)
                assertFalse(it.findViewById<HeaderSlideView>(R.id.headerNextImage).hasImage)
            }
            SystemClock.sleep(5500)
            scenario.onActivity {
                assertEquals(index, state(it).index)
                it.findViewById<TabLayout>(R.id.bottomTabs).getTabAt(2)!!.select()
            }
            awaitImage(scenario)
            scenario.onActivity { it.findViewById<ImageButton>(R.id.headerPlayback).performClick() }
            val deadline = SystemClock.uptimeMillis() + 10_000
            var advanced = false
            while (!advanced && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50)
                scenario.onActivity { advanced = state(it).index != index }
            }
            scenario.onActivity {
                val current = state(it)
                val diagnostic = HeaderSlideshow::class.java.declaredFields.joinToString { field ->
                    field.isAccessible = true
                    "${field.name}=${field.get(current)}"
                }
                assertEquals("now=${SystemClock.uptimeMillis()} $diagnostic", (index + 1) % 188, current.index)
            }
        }
    }

    /** Opt-in long soak: 188 images x two rounds x (5s hold + 450ms transition), about 35 minutes. */
    @Test fun twoFullRoundsAtRealPlaybackSpeed() {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("headerSoak") == "true",
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.findViewById<TabLayout>(R.id.bottomTabs).getTabAt(2)!!.select()
            }
            awaitImage(scenario)
            var previous = 0
            var changes = 0
            var lastChange = SystemClock.uptimeMillis()
            while (changes < 376) {
                SystemClock.sleep(200)
                scenario.onActivity { activity ->
                    val current = state(activity)
                    assertFalse(current.exhausted)
                    if (current.index != previous) {
                        assertEquals((previous + 1) % 188, current.index)
                        previous = current.index
                        changes++
                        lastChange = SystemClock.uptimeMillis()
                        if (changes % 20 == 0 || changes == 376) {
                            val memory = Debug.MemoryInfo()
                            Debug.getMemoryInfo(memory)
                            Log.i("HeaderSoak", "changes=$changes pssKb=${memory.totalPss} nativeKb=${Debug.getNativeHeapAllocatedSize() / 1024}")
                        }
                    }
                    val bytes = listOf(R.id.headerImage, R.id.headerNextImage).sumOf {
                        activity.findViewById<HeaderSlideView>(it).imageAllocationBytes
                    }
                    val header = activity.findViewById<HeaderGestureLayout>(R.id.mineHeader)
                    assertTrue(bytes.toLong() <= 2L * header.width * header.expandedHeightPx * 4)
                }
                assertTrue("Slideshow stalled", SystemClock.uptimeMillis() - lastChange < 15000)
            }
        }
    }
}
