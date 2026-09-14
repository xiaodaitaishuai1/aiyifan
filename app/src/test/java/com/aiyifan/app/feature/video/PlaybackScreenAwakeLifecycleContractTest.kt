package com.aiyifan.app.feature.video

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackScreenAwakeLifecycleContractTest {

    @Test
    fun `remote playback updates the attached screen awake controller and clears it when detached`() {
        val source = sourceFile("app/src/main/java/com/aiyifan/app/feature/video/VideoPlaybackController.kt")

        assertTrue(source.contains("import com.aiyifan.app.core.ui.PlaybackScreenAwakeController"))
        assertTrue(source.contains("screenAwakeController?.onIsPlayingChanged(isPlaying)"))
        assertTrue(source.contains("screenAwakeController = PlaybackScreenAwakeController(playerView::setKeepScreenOn)"))
        assertTrue(source.contains("screenAwakeController?.detach()"))
    }

    @Test
    fun `local playback updates screen awake only while the activity owns the player`() {
        val source = sourceFile("app/src/main/java/com/aiyifan/app/feature/localmedia/LocalVideoPlayerActivity.kt")

        assertTrue(source.contains("import com.aiyifan.app.core.ui.PlaybackScreenAwakeController"))
        assertTrue(source.contains("PlaybackScreenAwakeController(binding.localPlayerView::setKeepScreenOn)"))
        assertTrue(source.contains("screenAwakeController.attach(player.isPlaying)"))
        assertTrue(source.contains("screenAwakeController.onIsPlayingChanged(isPlaying)"))
        assertTrue(source.contains("screenAwakeController.onIsPlayingChanged(player.isPlaying)"))
        assertTrue(source.contains("screenAwakeController.detach()"))
    }

    private fun sourceFile(path: String): String = sequenceOf(
        File(path),
        File("../$path"),
    ).first(File::isFile).readText()
}
