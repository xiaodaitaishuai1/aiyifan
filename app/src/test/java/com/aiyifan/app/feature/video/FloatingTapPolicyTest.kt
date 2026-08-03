package com.aiyifan.app.feature.video

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingTapPolicyTest {

    @Test
    fun `drag and scale never become tap actions`() {
        assertEquals(FloatingTapAction.None, FloatingTapPolicy.action(2, dragged = true, scaled = false))
        assertEquals(FloatingTapAction.None, FloatingTapPolicy.action(2, dragged = false, scaled = true))
    }

    @Test
    fun `single video tap toggles controls`() {
        assertEquals(FloatingTapAction.ToggleControls, FloatingTapPolicy.action(1, dragged = false, scaled = false))
    }

    @Test
    fun `double video tap restores the app`() {
        assertEquals(FloatingTapAction.RestoreActivity, FloatingTapPolicy.action(2, dragged = false, scaled = false))
    }
}
