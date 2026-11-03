package com.aiyifan.app.feature.video

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWindowSizePolicyTest {

    @Test
    fun `screen smaller than minimum still fits without throwing`() {
        assertEquals(FloatingWindowSize(120, 68), FloatingWindowSizePolicy.resize(320, 240, 120))
    }

    @Test
    fun `available height limits width even when screen is wide`() {
        assertEquals(FloatingWindowSize(160, 90), FloatingWindowSizePolicy.fit(480, 240, 480, 800, 90))
    }

    @Test
    fun `available width caps both pinch and corner resize results`() {
        assertEquals(FloatingWindowSize(200, 113), FloatingWindowSizePolicy.fit(640, 240, 480, 200, 600))
        assertEquals(FloatingWindowSize(240, 135), FloatingWindowSizePolicy.fit(100, 240, 480, 800, 600))
    }

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
