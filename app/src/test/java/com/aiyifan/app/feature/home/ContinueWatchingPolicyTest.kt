package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.WatchHistory
import org.junit.Assert.*
import org.junit.Test

class ContinueWatchingPolicyTest {
    private fun record(key: String, time: Long) = WatchHistory(key, "ep", key, "", 1, 100, 1000, time)

    @Test fun `most recent five visible records are independent of catalog availability`() {
        val records = (1..8).map { record("$it", it.toLong()) }
        assertEquals(listOf("8", "7", "6", "5", "4"), ContinueWatchingPolicy.visible(records, emptyMap()).map { it.mediaKey })
        assertTrue(ContinueWatchingPolicy.visible(emptyList(), emptyMap()).isEmpty())
    }

    @Test fun `hidden record reappears only after a newer save`() {
        assertTrue(ContinueWatchingPolicy.visible(listOf(record("a", 10)), mapOf("a" to 10L)).isEmpty())
        assertEquals(1, ContinueWatchingPolicy.visible(listOf(record("a", 11)), mapOf("a" to 10L)).size)
    }
}
