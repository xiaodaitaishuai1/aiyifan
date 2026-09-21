package com.aiyifan.app.feature.video

import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.core.model.WatchHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumePolicyTest {
    private val first = Episode("first", "1", 1, null)
    private val second = Episode("second", "2", 2, null)
    private val detail = VideoDetail("video", "Video", "", 2, episodes = listOf(first, second))
    private fun history(key: String = "second", position: Long = 42_000L, duration: Long = 100_000L) =
        WatchHistory("video", key, "Video", "", 2, position, duration, 1L)

    @Test fun `resume chooses the stored episode and position`() {
        assertEquals(PlaybackResumeTarget(second, 42_000L), PlaybackResumePolicy.target(detail, history()))
    }

    @Test fun `missing episode falls back without applying another episodes position`() {
        assertEquals(PlaybackResumeTarget(first, 0L), PlaybackResumePolicy.target(detail, history("deleted")))
    }

    @Test fun `completed and negative positions restart the same episode`() {
        for (position in listOf(-1L, 100_000L, Long.MAX_VALUE)) {
            assertEquals(PlaybackResumeTarget(second, 0L), PlaybackResumePolicy.target(detail, history(position = position)))
        }
    }

    @Test fun `unknown duration preserves a positive resume position`() {
        assertEquals(42_000L, PlaybackResumePolicy.target(detail, history(duration = 0L))?.positionMs)
    }

    @Test fun `another video history is never reused`() {
        assertEquals(PlaybackResumeTarget(first, 0L), PlaybackResumePolicy.target(detail, history().copy(mediaKey = "other")))
    }

    @Test fun `brightness starts from window override and remains visible at minimum`() {
        assertEquals(70, PlayerGesturePolicy.brightnessPercent(0.7f, 128))
        assertEquals(50, PlayerGesturePolicy.brightnessPercent(-1f, 128))
        assertEquals(1, PlayerGesturePolicy.adjustVertical(50, 1000f, 100, 1, 100))
    }
}
