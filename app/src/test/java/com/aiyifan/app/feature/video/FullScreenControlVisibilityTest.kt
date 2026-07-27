package com.aiyifan.app.feature.video

import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenControlVisibilityTest {

    @Test
    fun `exit button is visible only while full screen controller is visible`() {
        assertTrue(FullScreenControlVisibility.shouldShowExitButton(isFullScreen = true, controllerVisibility = View.VISIBLE))
        assertFalse(FullScreenControlVisibility.shouldShowExitButton(isFullScreen = true, controllerVisibility = View.GONE))
        assertFalse(FullScreenControlVisibility.shouldShowExitButton(isFullScreen = false, controllerVisibility = View.VISIBLE))
    }
}
