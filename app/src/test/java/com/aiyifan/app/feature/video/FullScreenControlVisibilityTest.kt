package com.aiyifan.app.feature.video

import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenControlVisibilityTest {

    @Test
    fun `title bar is visible only while full screen controller is visible`() {
        assertTrue(FullScreenControlVisibility.shouldShowTitleBar(isFullScreen = true, controllerVisibility = View.VISIBLE))
        assertFalse(FullScreenControlVisibility.shouldShowTitleBar(isFullScreen = true, controllerVisibility = View.GONE))
        assertFalse(FullScreenControlVisibility.shouldShowTitleBar(isFullScreen = false, controllerVisibility = View.VISIBLE))
    }
}
