package com.aiyifan.app.feature.video

import com.aiyifan.app.core.model.PlaybackQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQualitySelectorTest {

    @Test
    fun `session quality is retained when available`() {
        assertEquals("720P", PlaybackQualitySelector.select(qualities(), "720P", "1080P")?.resolution)
    }

    @Test
    fun `default quality is used when session quality is unavailable`() {
        assertEquals("1080P", PlaybackQualitySelector.select(qualities(), "4K", "720P")?.resolution)
    }

    @Test
    fun `episode quality is used when no default exists`() {
        assertEquals(
            "480P",
            PlaybackQualitySelector.select(listOf(quality("720P"), quality("480P")), null, "480P")?.resolution,
        )
    }

    @Test
    fun `first usable quality is used as final fallback`() {
        assertEquals(
            "720P",
            PlaybackQualitySelector.select(listOf(quality("720P"), quality("480P")), null, "4K")?.resolution,
        )
    }

    private fun qualities() = listOf(quality("720P"), quality("1080P", isDefault = true))

    private fun quality(resolution: String, isDefault: Boolean = false) =
        PlaybackQuality(resolution, resolution, "https://example.com/$resolution.m3u8", isDefault)
}
