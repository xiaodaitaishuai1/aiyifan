package com.aiyifan.app.feature.localmedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalPlayerPositionPolicyTest {

    @Test
    fun `parses a valid local video intent uri`() {
        assertEquals(
            "content://media/external/video/media/7",
            LocalPlayerPositionPolicy.contentUriFrom("content://media/external/video/media/7"),
        )
    }

    @Test
    fun `rejects blank or invalid local video intent uri`() {
        assertEquals("", LocalPlayerPositionPolicy.contentUriFrom(""))
        assertEquals("", LocalPlayerPositionPolicy.contentUriFrom("not-a-uri"))
    }

    @Test
    fun `clamps saved position and resets completed position to zero`() {
        assertEquals(45_000L, LocalPlayerPositionPolicy.clampPosition(45_000L, 60_000L))
        assertEquals(0L, LocalPlayerPositionPolicy.clampPosition(60_000L, 60_000L))
        assertEquals(0L, LocalPlayerPositionPolicy.clampPosition(-1L, 60_000L))
        assertEquals(0L, LocalPlayerPositionPolicy.clampPosition(45_000L, 0L))
    }

    @Test
    fun `supports the local player speed options`() {
        val speeds = LocalPlayerPositionPolicy.speedOptions

        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), speeds)
        assertFalse(speeds.isEmpty())
    }

    @Test
    fun `builds a resume record from a local video`() {
        val record = LocalPlayerPositionPolicy.recordFor(
            mediaStoreId = 1L,
            contentUri = "content://media/external/video/media/1",
            displayName = "Trip.mp4",
            durationMs = 60_000L,
            positionMs = 12_000L,
            updatedAt = 99L,
        )

        assertEquals(1L, record.mediaStoreId)
        assertEquals("Trip.mp4", record.displayName)
        assertEquals(12_000L, record.positionMs)
        assertEquals(99L, record.updatedAt)
    }

}