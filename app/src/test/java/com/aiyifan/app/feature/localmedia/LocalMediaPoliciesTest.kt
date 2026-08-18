package com.aiyifan.app.feature.localmedia

import com.aiyifan.app.feature.localmedia.model.LocalPlaybackRecord
import com.aiyifan.app.feature.localmedia.model.LocalVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaPoliciesTest {

    @Test
    fun `android thirteen requests video media permission`() {
        assertEquals("android.permission.READ_MEDIA_VIDEO", LocalMediaPermissionPolicy.permissionFor(33))
    }

    @Test
    fun `android twelve requests external storage permission`() {
        assertEquals("android.permission.READ_EXTERNAL_STORAGE", LocalMediaPermissionPolicy.permissionFor(32))
    }

    @Test
    fun `library excludes zero duration videos and searches file names`() {
        val videos = listOf(
            video(id = 1, name = "Trip.mp4", durationMs = 30_000),
            video(id = 2, name = "Broken.mp4", durationMs = 0),
            video(id = 3, name = "Ocean.mkv", durationMs = 60_000),
        )

        assertEquals(listOf(1L, 3L), LocalMediaLibraryPolicy.validVideos(videos).map(LocalVideo::id))
        assertEquals(listOf(3L), LocalMediaLibraryPolicy.search(videos, "ocean").map(LocalVideo::id))
    }

    @Test
    fun `recent playback replaces a video record and retains one hundred newest`() {
        val records = (1L..100L).map { record(it, updatedAt = it) }
        val merged = LocalPlaybackStorePolicy.merge(records, record(50, updatedAt = 101))
        val withNew = LocalPlaybackStorePolicy.merge(merged, record(101, updatedAt = 102))

        assertEquals(100, withNew.size)
        assertEquals(101L, withNew.first().mediaStoreId)
        assertEquals(101L, withNew.first { it.mediaStoreId == 50L }.updatedAt)
        assertFalse(withNew.any { it.mediaStoreId == 1L })
    }

    @Test
    fun `recent playback prunes unavailable media and clamps resume position`() {
        val records = listOf(record(1, positionMs = 12_000), record(2, positionMs = 45_000))

        assertEquals(listOf(2L), LocalPlaybackStorePolicy.prune(records, setOf(2L)).map(LocalPlaybackRecord::mediaStoreId))
        assertEquals(45_000, LocalPlaybackStorePolicy.resumePosition(45_000, 60_000))
        assertEquals(0, LocalPlaybackStorePolicy.resumePosition(60_000, 60_000))
        assertEquals(0, LocalPlaybackStorePolicy.resumePosition(-1, 60_000))
    }

    private fun video(id: Long, name: String, durationMs: Long) = LocalVideo(
        id = id,
        contentUri = "content://media/external/video/media/$id",
        displayName = name,
        durationMs = durationMs,
        sizeBytes = 1,
        dateAddedMs = id,
        bucketName = null,
    )

    private fun record(id: Long, positionMs: Long = 0, updatedAt: Long = 0) = LocalPlaybackRecord(
        mediaStoreId = id,
        contentUri = "content://media/external/video/media/$id",
        displayName = "Video $id",
        durationMs = 60_000,
        positionMs = positionMs,
        updatedAt = updatedAt,
    )
}
