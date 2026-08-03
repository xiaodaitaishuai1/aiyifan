package com.aiyifan.app.feature.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingControlsVisibilityPolicyTest {

    @Test
    fun `video tap toggles hidden controls to visible`() {
        assertTrue(FloatingControlsVisibilityPolicy.togglesToVisible(currentlyVisible = false))
    }

    @Test
    fun `video tap toggles visible controls to hidden`() {
        assertFalse(FloatingControlsVisibilityPolicy.togglesToVisible(currentlyVisible = true))
    }

    @Test
    fun `visible controls hide after the idle delay`() {
        assertTrue(
            FloatingControlsVisibilityPolicy.shouldAutoHide(
                controlsVisible = true,
                shownAtMs = 1_000L,
                nowMs = 3_000L,
                delayMs = 2_000L,
            ),
        )
    }

    @Test
    fun `hidden controls do not schedule another hide`() {
        assertFalse(
            FloatingControlsVisibilityPolicy.shouldAutoHide(
                controlsVisible = false,
                shownAtMs = 1_000L,
                nowMs = 3_000L,
                delayMs = 2_000L,
            ),
        )
    }

    @Test
    fun `previous is disabled at first episode`() {
        assertFalse(FloatingControlsVisibilityPolicy.isEpisodeButtonEnabled(index = 0, count = 3, offset = -1))
    }

    @Test
    fun `next is disabled at final episode`() {
        assertFalse(FloatingControlsVisibilityPolicy.isEpisodeButtonEnabled(index = 2, count = 3, offset = 1))
    }

    @Test
    fun `next is enabled from middle episode`() {
        assertTrue(FloatingControlsVisibilityPolicy.isEpisodeButtonEnabled(index = 1, count = 3, offset = 1))
    }
}
