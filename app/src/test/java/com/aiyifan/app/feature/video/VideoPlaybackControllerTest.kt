package com.aiyifan.app.feature.video

import com.aiyifan.app.core.data.FakeCatalogRepository
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import com.aiyifan.app.core.data.CatalogRepository
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

    @Test
    fun `prepared episode is exposed as the active playback session`() {
        val detail = sampleDetail()
        val episode = sampleEpisode()
        val controller = VideoPlaybackController(FakePlaybackEngine(), FakeCatalogRepository(), FakePlaybackSession())

        assertTrue(controller.prepare(detail, episode))

        assertEquals(episode, controller.activeSession?.episode)
    }

    @Test
    fun `next episode resolves before replacing the active playback session`() = runBlocking {
        val first = sampleEpisode(key = "episode-1")
        val second = sampleEpisode(key = "episode-2")
        val controller = VideoPlaybackController(FakePlaybackEngine(), FakeCatalogRepository(), FakePlaybackSession())

        assertTrue(controller.prepare(sampleDetail(episodes = listOf(first, second)), first))

        assertEquals(EpisodeSwitchResult.Switched(second), controller.switchEpisode(offset = 1))
        assertEquals(second, controller.activeSession?.episode)
    }

    @Test
    fun `failed adjacent resolution keeps the active playback session`() = runBlocking {
        val first = sampleEpisode(key = "episode-1")
        val second = sampleEpisode(key = "episode-2")
        val repository = FakeCatalogRepository().apply {
            resolvePlaybackFailure = IllegalStateException("network")
        }
        val controller = VideoPlaybackController(FakePlaybackEngine(), repository, FakePlaybackSession())

        assertTrue(controller.prepare(sampleDetail(episodes = listOf(first, second)), first))

        assertEquals(EpisodeSwitchResult.Failed, controller.switchEpisode(offset = 1))
        assertEquals(first, controller.activeSession?.episode)
    }

    @Test
    fun `switch saves the outgoing episode before replacing media`() = runBlocking {
        val first = sampleEpisode(key = "first")
        val second = sampleEpisode(key = "second")
        val repository = FakeCatalogRepository()
        val engine = FakePlaybackEngine(currentPosition = 42_000L, duration = 100_000L)
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession())
        controller.prepare(sampleDetail(listOf(first, second)), first)

        controller.switchEpisode(1)

        assertEquals("first", repository.getHistory().single().episodeKey)
        assertEquals(42_000L, repository.getHistory().single().progressMs)
    }

    @Test
    fun `unknown active episode cannot jump to the first episode`() = runBlocking {
        val controller = VideoPlaybackController(FakePlaybackEngine(), FakeCatalogRepository(), FakePlaybackSession())
        controller.prepare(sampleDetail(listOf(sampleEpisode(key = "first"))), sampleEpisode(key = "missing"))

        assertEquals(EpisodeSwitchResult.Unavailable, controller.switchEpisode(1))
    }

    @Test
    fun `release persists the final position`() {
        val repository = FakeCatalogRepository()
        val controller = VideoPlaybackController(FakePlaybackEngine(currentPosition = 9_000L), repository, FakePlaybackSession())
        controller.prepare(sampleDetail(), sampleEpisode())
        controller.release()

        assertEquals(9_000L, repository.getHistory().single().progressMs)
    }

    @Test
    fun `pause during resolution prevents late autoplay`() = runBlocking {
        val gate = CompletableDeferred<Episode>()
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean): Episode = gate.await()
        }
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession())
        val episode = sampleEpisode()
        val request = async(start = CoroutineStart.UNDISPATCHED) { controller.loadEpisode(sampleDetail(), episode) }
        controller.pause()
        gate.complete(episode)

        assertTrue(request.await() is EpisodeSwitchResult.Switched)
        assertFalse(engine.isPlaying)
    }

    @Test
    fun `duplicate switches are ignored and stale response cannot replace a new session`() = runBlocking {
        val gate = CompletableDeferred<Episode>()
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean): Episode = gate.await()
        }
        val controller = VideoPlaybackController(FakePlaybackEngine(), repository, FakePlaybackSession())
        val first = sampleEpisode(key = "first")
        val second = sampleEpisode(key = "second")
        controller.prepare(sampleDetail(listOf(first, second)), first)
        val request = async(start = CoroutineStart.UNDISPATCHED) { controller.switchEpisode(1) }
        assertEquals(EpisodeSwitchResult.Unavailable, controller.switchEpisode(1))
        controller.prepare(sampleDetail(), sampleEpisode(key = "new"))
        gate.complete(second)

        assertEquals(EpisodeSwitchResult.Unavailable, request.await())
        assertEquals("new", controller.activeSession?.episode?.episodeKey)
    }

    @Test
    fun `buffering play intent can be paused without toggling back to play`() {
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())
        controller.prepare(sampleDetail(), sampleEpisode())
        engine.isPlaying = false
        controller.togglePlayPause()

        assertFalse(engine.playWhenReady)
    }

    @Test
    fun `progress is persisted every five seconds even without a page listener`() {
        var now = 1_000L
        val repository = FakeCatalogRepository()
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession(), clock = { now })
        controller.prepare(sampleDetail(), sampleEpisode())
        now = 5_999L
        engine.dispatchPosition(4_999L)
        assertTrue(repository.getHistory().isEmpty())
        now = 6_000L
        engine.dispatchPosition(5_000L)
        assertEquals(5_000L, repository.getHistory().single().progressMs)
        controller.seekTo(8_000L)
        assertEquals(8_000L, repository.getHistory().single().progressMs)
    }

    @Test
    fun `screen locked at prepare stays paused after screen becomes interactive`() {
        var interactive = false
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession(), canPlay = { interactive })
        controller.prepare(sampleDetail(), sampleEpisode())
        assertFalse(engine.playWhenReady)
        interactive = true
        assertFalse(controller.playWhenReady)
        controller.togglePlayPause()
        assertTrue(engine.playWhenReady)
    }

    @Test
    fun `cancellation propagates and releases the loading guard`() = runBlocking {
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean): Episode {
                throw kotlinx.coroutines.CancellationException("cancelled")
            }
        }
        val controller = VideoPlaybackController(FakePlaybackEngine(), repository, FakePlaybackSession())
        var cancelled = false
        try { controller.loadEpisode(sampleDetail(), sampleEpisode()) }
        catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertFalse(controller.isLoading)
        assertFalse(controller.isPrepared)
    }

    @Test
    fun `old video cannot switch episodes while a new video detail is loading`() = runBlocking {
        val first = sampleEpisode(key = "first")
        val second = sampleEpisode(key = "second")
        val controller = VideoPlaybackController(FakePlaybackEngine(), FakeCatalogRepository(), FakePlaybackSession())
        controller.openVideo("video-1")
        controller.prepare(sampleDetail(listOf(first, second)), first)
        controller.openVideo("video-2")

        assertEquals(EpisodeSwitchResult.Unavailable, controller.switchEpisode(1))
        assertFalse(controller.isLoading)
        assertFalse(controller.playWhenReady)
    }

    private fun sampleDetail(episodes: List<Episode> = emptyList()) = VideoDetail(
        mediaKey = "video-1",
        title = "Sample video",
        coverUrl = "",
        videoType = 1,
        episodes = episodes,
    )

    private fun sampleEpisode(
        key: String = "episode-1",
        mediaUrl: String = "https://example.com/video.m3u8",
    ) = Episode(
        episodeKey = key,
        episodeTitle = "Episode 1",
        uniqueId = 1,
        mediaUrl = mediaUrl,
    )

    private class FakePlaybackEngine(
        override var currentPosition: Long = 0L,
        override var duration: Long = 0L,
    ) : PlaybackEngine {
        override var isPlaying = false
        override var playWhenReady = false
        var setMediaCalls = 0
        var releaseCalls = 0
        var seekPositionMs = 0L
        private val positionListeners = linkedSetOf<(Long) -> Unit>()

        override fun setMediaUrl(mediaUrl: String) {
            setMediaCalls++
        }

        override fun prepare() = Unit

        override fun seekTo(positionMs: Long) {
            seekPositionMs = positionMs
            currentPosition = positionMs
        }

        override fun addPositionListener(listener: (Long) -> Unit): () -> Unit {
            positionListeners += listener
            return { positionListeners -= listener }
        }

        fun dispatchPosition(positionMs: Long) {
            currentPosition = positionMs
            positionListeners.toList().forEach { it(positionMs) }
        }

        override fun play() {
            isPlaying = true
            playWhenReady = true
        }

        override fun pause() {
            isPlaying = false
            playWhenReady = false
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
