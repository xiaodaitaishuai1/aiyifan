package com.aiyifan.app.feature.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenInsetsPolicyTest {

    @Test
    fun `page insets are disabled in full screen`() {
        assertFalse(FullScreenInsetsPolicy.shouldApplyPageInsets(isFullScreen = true))
        assertTrue(FullScreenInsetsPolicy.shouldApplyPageInsets(isFullScreen = false))
    }
}
