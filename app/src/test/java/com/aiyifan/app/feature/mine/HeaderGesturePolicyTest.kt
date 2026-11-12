package com.aiyifan.app.feature.mine

import org.junit.Assert.*
import org.junit.Test

class HeaderGesturePolicyTest {
    @Test fun `small and ambiguous movement does not lock an axis`() {
        assertEquals(HeaderGesturePolicy.Axis.UNDECIDED, HeaderGesturePolicy.axis(5f, 1f, 8f))
        assertEquals(HeaderGesturePolicy.Axis.UNDECIDED, HeaderGesturePolicy.axis(10f, 10f, 8f))
        assertEquals(HeaderGesturePolicy.Axis.HORIZONTAL, HeaderGesturePolicy.axis(-24f, 10f, 8f))
        assertEquals(HeaderGesturePolicy.Axis.VERTICAL, HeaderGesturePolicy.axis(8f, 24f, 8f))
    }

    @Test fun `swipe needs distance or velocity in the same direction`() {
        assertFalse(HeaderGesturePolicy.shouldSwitch(-19f, 100f, 0f, 50f, 8f))
        assertTrue(HeaderGesturePolicy.shouldSwitch(-21f, 100f, 0f, 50f, 8f))
        assertTrue(HeaderGesturePolicy.shouldSwitch(-10f, 100f, -100f, 50f, 8f))
        assertFalse(HeaderGesturePolicy.shouldSwitch(-10f, 100f, 100f, 50f, 8f))
        assertFalse(HeaderGesturePolicy.shouldSwitch(2f, 100f, 100f, 50f, 8f))
    }

    @Test fun `vertical gestures snap to two stable heights`() {
        assertFalse(HeaderGesturePolicy.expandedAfter(false, 31f, 32f))
        assertTrue(HeaderGesturePolicy.expandedAfter(false, 32f, 32f))
        assertTrue(HeaderGesturePolicy.expandedAfter(true, -31f, 32f))
        assertFalse(HeaderGesturePolicy.expandedAfter(true, -32f, 32f))
    }

    @Test fun `vertical interception leaves normal page scroll available`() {
        assertTrue(HeaderGesturePolicy.canResize(40f, false, true))
        assertFalse(HeaderGesturePolicy.canResize(40f, false, false))
        assertFalse(HeaderGesturePolicy.canResize(-40f, false, true))
        assertTrue(HeaderGesturePolicy.canResize(-40f, true, false))
        assertFalse(HeaderGesturePolicy.canResize(40f, true, true))
    }
}
