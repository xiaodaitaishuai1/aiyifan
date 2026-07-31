package com.aiyifan.app.feature.video

import com.aiyifan.app.core.data.FakeCatalogRepository
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackControllerTest {

    @Test
    fun `release only reaches the engine and session once`() {
        val engine = FakePlaybackEngine()
        val session = FakePlaybackSession()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), session)

        controller.release()
        controller.release()

        assertEquals(1, engine.releaseCalls)
        assertEquals(1, session.releaseCalls)
    }

    @Test
    fun `provider recreates a controller after its cached controller is released`() {
        val engines = mutableListOf<FakePlaybackEngine>()
        val provider = VideoPlaybackControllerProvider {
            FakePlaybackEngine().also(engines::add).let { engine ->
                VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())
            }
        }

        val releasedController = provider.get()
        releasedController.release()
        val recreatedController = provider.get()

        assertNotSame(releasedController, recreatedController)
        assertEquals(1, engines.first().releaseCalls)
    }

    @Test
    fun `prepare rejects an episode without a media url`() {
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())

        val prepared = controller.prepare(sampleDetail(), sampleEpisode(mediaUrl = "  "))

        assertFalse(prepared)
        assertEquals(0, engine.setMediaCalls)
        assertFalse(controller.isPrepared)
    }

    @Test
    fun `save history records the active episode playback progress`() {
        val repository = FakeCatalogRepository()
        val engine = FakePlaybackEngine(currentPosition = 12_345L, duration = 67_890L)
        val detail = sampleDetail()
        val episode = sampleEpisode()
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession())

        assertTrue(controller.prepare(detail, episode))
        controller.saveHistory()

        val history = repository.getHistory().single()
        assertEquals(detail.mediaKey, history.mediaKey)
        assertEquals(episode.episodeKey, history.episodeKey)
        assertEquals(12_345L, history.progressMs)
        assertEquals(67_890L, history.durationMs)
    }

    @Test
    fun `prepare resumes from the supplied playback position`() {
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())

        assertTrue(controller.prepare(sampleDetail(), sampleEpisode(), startPositionMs = 12_345L))

        assertEquals(12_345L, engine.seekPositionMs)
    }

    @Test
    fun `prepare keeps playback paused when requested`() {
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())

        assertTrue(controller.prepare(sampleDetail(), sampleEpisode(), startPositionMs = 12_345L, shouldPlay = false))

        assertEquals(12_345L, engine.seekPositionMs)
        assertFalse(engine.isPlaying)
    }

    @Test
    fun `position listener receives playback progress from the engine`() {
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())
        val positions = mutableListOf<Long>()

        controller.addPositionListener(positions::add)
        engine.dispatchPosition(34_000L)

        assertEquals(listOf(34_000L), positions)
    }

    private fun sampleDetail() = VideoDetail(
        mediaKey = "video-1",
        title = "Sample video",
        coverUrl = "",
        videoType = 1,
    )

    private fun sampleEpisode(mediaUrl: String = "https://example.com/video.m3u8") = Episode(
        episodeKey = "episode-1",
        episodeTitle = "Episode 1",
        uniqueId = 1,
        mediaUrl = mediaUrl,
    )

    private class FakePlaybackEngine(
        override var currentPosition: Long = 0L,
        override var duration: Long = 0L,
    ) : PlaybackEngine {
        override var isPlaying = false
        var setMediaCalls = 0
        var releaseCalls = 0
        var seekPositionMs = 0L
        private var positionListener: ((Long) -> Unit)? = null

        override fun setMediaUrl(mediaUrl: String) {
            setMediaCalls++
        }

        override fun prepare() = Unit

        override fun seekTo(positionMs: Long) {
            seekPositionMs = positionMs
        }

        override fun addPositionListener(listener: (Long) -> Unit): () -> Unit {
            positionListener = listener
            return { positionListener = null }
        }

        fun dispatchPosition(positionMs: Long) {
            currentPosition = positionMs
            positionListener?.invoke(positionMs)
        }

        override fun play() {
            isPlaying = true
        }

        override fun pause() {
            isPlaying = false
        }

        override fun attach(playerView: androidx.media3.ui.PlayerView) = Unit

        override fun detach() = Unit

        override fun release() {
            releaseCalls++
        }
    }

    private class FakePlaybackSession : PlaybackSession {
        var releaseCalls = 0

        override fun release() {
            releaseCalls++
        }
    }
}
