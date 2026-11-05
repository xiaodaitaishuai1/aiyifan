package com.aiyifan.app.feature.video

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeListScrollPolicyTest {
    @Test
    fun `current episode index is the target scroll position`() {
        assertEquals(7, EpisodeListScrollPolicy.targetPosition(currentIndex = 7, itemCount = 12))
    }

    @Test
    fun `missing or out of range current episode does not request scrolling`() {
        assertEquals(null, EpisodeListScrollPolicy.targetPosition(currentIndex = -1, itemCount = 12))
        assertEquals(null, EpisodeListScrollPolicy.targetPosition(currentIndex = 12, itemCount = 12))
        assertEquals(null, EpisodeListScrollPolicy.targetPosition(currentIndex = 0, itemCount = 0))
    }
}
