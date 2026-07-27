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

    @Test
    fun `quality button is visible for visible full screen controls with a quality`() {
        assertTrue(FullScreenControlVisibility.shouldShowQualityButton(true, View.VISIBLE, 2))
        assertFalse(FullScreenControlVisibility.shouldShowQualityButton(true, View.GONE, 2))
        assertFalse(FullScreenControlVisibility.shouldShowQualityButton(false, View.VISIBLE, 2))
        assertTrue(FullScreenControlVisibility.shouldShowQualityButton(true, View.VISIBLE, 1))
        assertFalse(FullScreenControlVisibility.shouldShowQualityButton(true, View.VISIBLE, 0))
    }
}
