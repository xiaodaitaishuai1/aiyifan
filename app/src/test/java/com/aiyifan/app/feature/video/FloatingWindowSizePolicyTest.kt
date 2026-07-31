package com.aiyifan.app.feature.video

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWindowSizePolicyTest {

    @Test
    fun `resize preserves sixteen by nine ratio at the maximum width`() {
        assertEquals(
            FloatingWindowSize(width = 480, height = 270),
            FloatingWindowSizePolicy.resize(
                requestedWidth = 500,
                minWidth = 240,
                maxWidth = 480,
            ),
        )
    }

    @Test
    fun `resize clamps width to its minimum`() {
        assertEquals(
            FloatingWindowSize(width = 240, height = 135),
            FloatingWindowSizePolicy.resize(
                requestedWidth = 120,
                minWidth = 240,
                maxWidth = 480,
            ),
        )
    }

    @Test
    fun `resize applies a pinch scale factor while preserving the aspect ratio`() {
        assertEquals(
            FloatingWindowSize(width = 400, height = 225),
            FloatingWindowSizePolicy.resizeByScale(
                currentWidth = 320,
                scaleFactor = 1.25f,
                minWidth = 240,
                maxWidth = 480,
            ),
        )
    }
}
