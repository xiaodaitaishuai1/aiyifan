package com.aiyifan.app.feature.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGesturePolicyTest {

    @Test
    fun `left half starts a brightness gesture`() {
        assertEquals(PlayerGestureKind.BRIGHTNESS, PlayerGesturePolicy.kindFor(99f, 200))
    }

    @Test
    fun `right half starts a volume gesture`() {
        assertEquals(PlayerGestureKind.VOLUME, PlayerGesturePolicy.kindFor(100f, 200))
    }

    @Test
    fun `vertical adjustment clamps at maximum`() {
        assertEquals(255, PlayerGesturePolicy.adjustVertical(250, -1_000f, 500, 0, 255))
    }

    @Test
    fun `stationary touch becomes seek eligible only after two seconds`() {
        assertFalse(PlayerGesturePolicy.isSeekLongPress(1_999L, 2f, 8))
        assertTrue(PlayerGesturePolicy.isSeekLongPress(2_000L, 2f, 8))
    }

    @Test
    fun `seek preview is clamped to duration`() {
        assertEquals(60_000L, PlayerGesturePolicy.seekPreview(50_000L, 300f, 300, 60_000L))
    }
}
