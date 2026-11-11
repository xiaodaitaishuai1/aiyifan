package com.aiyifan.app.feature.video

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerLifecycleSafetyTest {

    @Test
    fun `detail and playback requests propagate cancellation`() {
        val source = videoPlayerSource()

        assertTrue(source.contains("getVideoDetail(mediaKey)"))
        assertTrue(source.contains("playbackController.loadEpisode(detail, episode, resumePositionMs)"))
        assertTrue(source.contains(".onFailure { error ->"))
        assertTrue(source.contains("if (error is CancellationException) throw error"))
    }

    @Test
    fun `picture in picture prepares a player only layout before entering`() {
        val source = videoPlayerSource()

        val entry = source.substringAfter("private fun enterSystemPictureInPicture(): Boolean")
            .substringBefore("private fun showFloatingPresentation()")
        assertTrue(entry.indexOf("preparePictureInPictureLayout()") >= 0)
        assertTrue(entry.indexOf("preparePictureInPictureLayout()") < entry.indexOf("enterPictureInPictureMode("))
        assertTrue(entry.contains("if (!entered) restorePictureInPictureLayout()"))
    }

    private fun videoPlayerSource(): String = sequenceOf(
        File("src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt"),
        File("app/src/main/java/com/aiyifan/app/feature/video/VideoPlayerActivity.kt"),
    ).first(File::isFile).readText()
}
