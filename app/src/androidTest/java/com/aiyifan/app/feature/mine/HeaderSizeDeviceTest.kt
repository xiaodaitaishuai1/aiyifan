package com.aiyifan.app.feature.mine

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderSizeDeviceTest {
    @Test fun fixedExpansionSurvivesWindowResizeAndCollapsesInShortWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val header = HeaderGestureLayout(instrumentation.targetContext)
            header.layoutParams = FrameLayout.LayoutParams(1080, 800)
            val collapsed = header.resources.getDimensionPixelSize(com.aiyifan.app.R.dimen.mine_header_height) + 100
            val windowHeight = collapsed * 4
            header.configureSize(windowHeight, 100)
            header.restoreExpanded(true)
            assertTrue(header.expanded)
            assertEquals(windowHeight * 4 / 5, header.layoutParams.height)
            header.configureSize(collapsed, 100)
            assertFalse(header.expanded)
            assertEquals(collapsed, header.layoutParams.height)
            header.configureSize(windowHeight, 100)
            assertEquals(collapsed, header.layoutParams.height)
            header.restoreExpanded(true)
            assertEquals(windowHeight * 4 / 5, header.layoutParams.height)
            header.configureSize(windowHeight + 400, 100)
            assertEquals((windowHeight + 400) * 4 / 5, header.layoutParams.height)
        }
    }
}
