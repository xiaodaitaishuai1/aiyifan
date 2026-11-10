package com.aiyifan.app.feature.history

import org.junit.Assert.*
import org.junit.Test

class WatchProgressPresentationTest {
    @Test fun `progress formats hours and unknown duration`() {
        assertEquals("23:15 / 45:00", WatchProgressPresentation.time(1_395_000, 2_700_000))
        assertEquals("1:02:03 / 2:00:00", WatchProgressPresentation.time(3_723_000, 7_200_000))
        assertEquals("00:30", WatchProgressPresentation.time(30_000, 0))
        assertEquals("00:00", WatchProgressPresentation.time(-1, 0))
    }
    @Test fun `percentage is bounded and unknown duration is not completed`() {
        assertEquals(100, WatchProgressPresentation.percent(Long.MAX_VALUE, 100))
        assertEquals(0, WatchProgressPresentation.percent(10, 0))
        assertFalse(WatchProgressPresentation.completed(10, 0))
        assertTrue(WatchProgressPresentation.completed(100, 100))
    }
    @Test fun `compact time rounds durations to whole minutes for the home strip`() {
        assertEquals("0:00 / 1:29", WatchProgressPresentation.compactTime(1_000, 5_337_000))
        assertEquals("1:02 / 2:00", WatchProgressPresentation.compactTime(3_723_000, 7_200_000))
    }
}
