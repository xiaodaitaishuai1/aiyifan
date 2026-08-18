package com.aiyifan.app.feature.localmedia

import com.aiyifan.app.feature.localmedia.data.LocalMediaProjection
import com.aiyifan.app.feature.localmedia.data.LocalMediaRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalMediaRepositoryProjectionTest {

    @Test
    fun `media store projection exposes the required columns`() {
        assertEquals(
            setOf("_id", "_display_name", "duration", "_size", "date_added", "bucket_display_name"),
            LocalMediaProjection.columns.toSet(),
        )
    }

    @Test
    fun `mapper drops zero duration rows and normalizes timestamps to milliseconds`() {
        assertNull(
            LocalMediaProjection.map(
                row(id = 7L, durationMs = 0L),
                contentUri = "content://media/external/video/media/7",
            ),
        )

        val mapped = LocalMediaProjection.map(
            row(id = 7L, durationMs = 30_000L, dateAddedSeconds = 1_000L),
            contentUri = "content://media/external/video/media/7",
        )

        assertEquals(7L, mapped!!.id)
        assertEquals(30_000L, mapped.durationMs)
        assertEquals(1_000_000L, mapped.dateAddedMs)
        assertEquals("content://media/external/video/media/7", mapped.contentUri)
    }

    private fun row(
        id: Long,
        durationMs: Long,
        dateAddedSeconds: Long = 0L,
    ) = LocalMediaRow(
        id = id,
        displayName = "Video $id.mp4",
        durationMs = durationMs,
        sizeBytes = 1234L,
        dateAddedSeconds = dateAddedSeconds,
        bucketName = "Movies",
    )
}