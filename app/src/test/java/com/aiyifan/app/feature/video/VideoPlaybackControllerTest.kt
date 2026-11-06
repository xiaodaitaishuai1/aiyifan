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
    fun `pending selection survives callbacks and rolls back on failure`() = runBlocking {
        val first = sampleEpisode(key = "first")
        val second = sampleEpisode(key = "second")
        val gate = CompletableDeferred<Episode>()
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean) = gate.await()
        }
        val controller = VideoPlaybackController(FakePlaybackEngine(), repository, FakePlaybackSession())
        val detail = sampleDetail(listOf(first, second))
        controller.prepare(detail, first)
        val selections = mutableListOf<String?>()
        controller.addStateListener {
            selections += (controller.pendingEpisode ?: controller.activeSession?.episode)?.episodeKey
        }
        val request = async(start = CoroutineStart.UNDISPATCHED) { controller.loadEpisode(detail, second) }
        assertEquals(second, controller.pendingEpisode)
        assertEquals("second", selections.last())
        controller.pause()
        assertEquals("second", selections.last())
        assertEquals(EpisodeSwitchResult.Unavailable, controller.loadEpisode(detail, first))
        assertEquals(second, controller.pendingEpisode)
        gate.completeExceptionally(IllegalStateException("network"))
        assertEquals(EpisodeSwitchResult.Failed, request.await())
        assertEquals(null, controller.pendingEpisode)
        assertEquals("first", selections.last())
        assertFalse(controller.isLoading)
    }

    @Test
    fun `cancelled pending selection restores active episode`() = runBlocking {
        val first = sampleEpisode(key = "first")
        val second = sampleEpisode(key = "second")
        val gate = CompletableDeferred<Episode>()
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean) = gate.await()
        }
        val controller = VideoPlaybackController(FakePlaybackEngine(), repository, FakePlaybackSession())
        val detail = sampleDetail(listOf(first, second))
        controller.prepare(detail, first)
        val request = async(start = CoroutineStart.UNDISPATCHED) { controller.loadEpisode(detail, second) }
        request.cancel()
        request.join()
        assertEquals(null, controller.pendingEpisode)
        assertEquals(first, controller.activeSession?.episode)
        assertFalse(controller.isLoading)
    }

    @Test
    fun `successful selection clears pending before publishing new session`() = runBlocking {
        val first = sampleEpisode(key = "first")
        val second = sampleEpisode(key = "second")
        val controller = VideoPlaybackController(FakePlaybackEngine(), FakeCatalogRepository(), FakePlaybackSession())
        val detail = sampleDetail(listOf(first, second))
        controller.prepare(detail, first)
        controller.loadEpisode(detail, second)
        assertEquals(null, controller.pendingEpisode)
        assertFalse(controller.isLoading)
        assertEquals(second, controller.activeSession?.episode)
    }

    @Test
    fun `new video and release clear a pending selection without accepting its late response`() = runBlocking {
        val gate = CompletableDeferred<Episode>()
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean) = gate.await()
        }
        val controller = VideoPlaybackController(FakePlaybackEngine(), repository, FakePlaybackSession())
        val episode = sampleEpisode()
        controller.openVideo("video-1")
        val request = async(start = CoroutineStart.UNDISPATCHED) { controller.loadEpisode(sampleDetail(), episode) }
        controller.openVideo("video-2")
        assertEquals(null, controller.pendingEpisode)
        controller.release()
        gate.complete(episode)
        assertEquals(EpisodeSwitchResult.Unavailable, request.await())
        assertEquals(null, controller.activeSession)
    }

    @Test
    fun `relative seek clamps both ends and persists each change while paused`() {
        val repository = FakeCatalogRepository()
        val engine = FakePlaybackEngine(currentPosition = 5_000L, duration = 25_000L)
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession())
        controller.prepare(sampleDetail(), sampleEpisode(), shouldPlay = false)
        controller.seekBy(-10_000L)
        assertEquals(0L, engine.currentPosition)
        controller.seekBy(10_000L)
        controller.seekBy(10_000L)
        assertEquals(20_000L, engine.currentPosition)
        controller.seekBy(10_000L)
        assertEquals(25_000L, engine.currentPosition)
        assertEquals(25_000L, repository.getHistory().single().progressMs)
        assertFalse(controller.playWhenReady)
    }

    @Test
    fun `relative seek ignores unknown duration unprepared and loading states`() = runBlocking {
        val gate = CompletableDeferred<Episode>()
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean) = gate.await()
        }
        val engine = FakePlaybackEngine(currentPosition = 5_000L, duration = 25_000L)
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession())
        controller.seekBy(10_000L)
        assertEquals(5_000L, engine.currentPosition)
        controller.prepare(sampleDetail(), sampleEpisode())
        engine.duration = 0L
        controller.seekBy(10_000L)
        assertFalse(controller.canSeek)
        assertEquals(5_000L, engine.currentPosition)
        engine.duration = 25_000L
        val request = async(start = CoroutineStart.UNDISPATCHED) { controller.loadEpisode(sampleDetail(), sampleEpisode()) }
        controller.seekBy(10_000L)
        assertFalse(controller.canSeek)
        assertEquals(5_000L, engine.currentPosition)
        request.cancel()
        request.join()
        assertTrue(controller.canSeek)
        controller.release()
        controller.seekBy(10_000L)
        assertEquals(5_000L, engine.currentPosition)
    }

    @Test
    fun `resolved stream keeps catalog identity for next episode and history`() = runBlocking {
        val first = sampleEpisode(key = "first")
        val second = sampleEpisode(key = "second")
        val base = FakeCatalogRepository()
        val repository = object : CatalogRepository by base {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean) =
                episode.copy(episodeKey = "stream-${episode.episodeKey}", uniqueId = 999)
        }
        val controller = VideoPlaybackController(FakePlaybackEngine(), repository, FakePlaybackSession())
        val detail = sampleDetail(listOf(first, second))

        controller.loadEpisode(detail, first)
        assertEquals(first.episodeKey, controller.activeSession?.episode?.episodeKey)
        assertEquals(first.uniqueId, controller.activeSession?.episode?.uniqueId)
        assertTrue(controller.switchEpisode(1) is EpisodeSwitchResult.Switched)
        controller.saveHistory()
        val history = base.getHistory().single()
        assertEquals(second.episodeKey, history.episodeKey)
        assertEquals(second, PlaybackResumePolicy.target(detail, history)?.episode)
    }

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
    fun `ended playback advances to the next episode only once`() = runBlocking {
        val first = sampleEpisode(key = "episode-1")
        val second = sampleEpisode(key = "episode-2")
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())
        val detail = sampleDetail(episodes = listOf(first, second))
        controller.prepare(detail, first)
        engine.hasEnded = true

        assertEquals(EpisodeSwitchResult.Switched(second), controller.advanceToNextEpisodeIfEnded())
        assertEquals(EpisodeSwitchResult.Unavailable, controller.advanceToNextEpisodeIfEnded())
        assertEquals(second, controller.activeSession?.episode)
    }

    @Test
    fun `ended final episode does not advance`() = runBlocking {
        val final = sampleEpisode(key = "episode-final")
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())
        controller.prepare(sampleDetail(episodes = listOf(final)), final)
        engine.hasEnded = true

        assertEquals(EpisodeSwitchResult.Unavailable, controller.advanceToNextEpisodeIfEnded())
        assertEquals(final, controller.activeSession?.episode)
    }

    @Test
    fun `failed automatic advance keeps the episode and does not repeatedly resolve`() = runBlocking {
        val first = sampleEpisode(key = "first")
        val second = sampleEpisode(key = "second")
        var requests = 0
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean): Episode {
                requests++
                throw IllegalStateException("network")
            }
        }
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession())
        controller.prepare(sampleDetail(listOf(first, second)), first)
        engine.hasEnded = true

        assertEquals(EpisodeSwitchResult.Failed, controller.advanceToNextEpisodeIfEnded())
        assertEquals(EpisodeSwitchResult.Unavailable, controller.advanceToNextEpisodeIfEnded())
        assertEquals(1, requests)
        assertEquals(first, controller.activeSession?.episode)
        assertFalse(controller.isLoading)
    }

    @Test
    fun `automatic advance waits for playback end and allows each subsequent episode`() = runBlocking {
        val episodes = (1..3).map { sampleEpisode(key = "episode-$it") }
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession())
        controller.prepare(sampleDetail(episodes), episodes.first())
        assertEquals(EpisodeSwitchResult.Unavailable, controller.advanceToNextEpisodeIfEnded())
        engine.hasEnded = true
        assertEquals(EpisodeSwitchResult.Switched(episodes[1]), controller.advanceToNextEpisodeIfEnded())
        assertEquals(EpisodeSwitchResult.Unavailable, controller.advanceToNextEpisodeIfEnded())
        engine.hasEnded = true
        assertEquals(EpisodeSwitchResult.Switched(episodes[2]), controller.advanceToNextEpisodeIfEnded())
        assertTrue(engine.playWhenReady)
    }

    @Test
    fun `outro switch in progress prevents duplicate automatic advance`() = runBlocking {
        val episodes = (1..3).map { sampleEpisode(key = "episode-$it") }
        val gate = CompletableDeferred<Episode>()
        var requests = 0
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean): Episode {
                requests++
                return gate.await()
            }
        }
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession())
        val detail = sampleDetail(episodes)
        controller.prepare(detail, episodes.first())
        val outro = async(start = CoroutineStart.UNDISPATCHED) { controller.loadEpisode(detail, episodes[1]) }
        engine.hasEnded = true
        assertEquals(EpisodeSwitchResult.Unavailable, controller.advanceToNextEpisodeIfEnded())
        gate.complete(episodes[1])
        assertEquals(EpisodeSwitchResult.Switched(episodes[1]), outro.await())
        assertEquals(EpisodeSwitchResult.Unavailable, controller.advanceToNextEpisodeIfEnded())
        assertEquals(1, requests)
    }

    @Test
    fun `replaying after a failed advance allows a new end attempt`() = runBlocking {
        val episodes = (1..2).map { sampleEpisode(key = "episode-$it") }
        val repository = FakeCatalogRepository().apply { resolvePlaybackFailure = IllegalStateException("network") }
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession())
        controller.prepare(sampleDetail(episodes), episodes.first())
        engine.hasEnded = true
        assertEquals(EpisodeSwitchResult.Failed, controller.advanceToNextEpisodeIfEnded())
        engine.hasEnded = false
        engine.dispatchState()
        repository.resolvePlaybackFailure = null
        engine.hasEnded = true
        assertEquals(EpisodeSwitchResult.Switched(episodes[1]), controller.advanceToNextEpisodeIfEnded())
    }

    @Test
    fun `cancelled automatic advance can resume when the playback surface changes`() = runBlocking {
        val episodes = (1..2).map { sampleEpisode(key = "episode-$it") }
        val gate = CompletableDeferred<Episode>()
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean) = gate.await()
        }
        val engine = FakePlaybackEngine()
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession())
        controller.prepare(sampleDetail(episodes), episodes.first())
        engine.hasEnded = true
        val request = async(start = CoroutineStart.UNDISPATCHED) { controller.advanceToNextEpisodeIfEnded() }
        request.cancel()
        request.join()
        assertFalse(controller.isLoading)

        gate.complete(episodes[1])
        assertEquals(EpisodeSwitchResult.Switched(episodes[1]), controller.advanceToNextEpisodeIfEnded())
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

    @Test
    fun `sleep deadline counts paused time and clears after saving`() {
        var now = 0L
        val engine = FakePlaybackEngine(currentPosition = 1234L)
        val repository = FakeCatalogRepository()
        val controller = VideoPlaybackController(engine, repository, FakePlaybackSession(), monotonicClock = { now })
        controller.prepare(sampleDetail(), sampleEpisode())
        controller.setSleepTimer(SleepTimerOption.MINUTES_15)
        controller.pause()
        now = 900_000L
        controller.tickSleepTimer()
        assertEquals(SleepTimerOption.OFF, controller.sleepTimerState.option)
        assertFalse(controller.playWhenReady)
        assertEquals(1234L, repository.getHistory().single().progressMs)
    }

    @Test
    fun `timer replacement cancellation and surface handoff retain correct deadline`() {
        var now = 0L
        val controller = VideoPlaybackController(FakePlaybackEngine(), FakeCatalogRepository(), FakePlaybackSession(), monotonicClock = { now })
        controller.prepare(sampleDetail(), sampleEpisode())
        controller.setSleepTimer(SleepTimerOption.MINUTES_15)
        now = 60_000L
        controller.detach()
        controller.hasOverlayOwner = true
        controller.openVideo("video-1")
        assertEquals(840_000L, controller.sleepTimerState.remainingMs)
        controller.setSleepTimer(SleepTimerOption.MINUTES_30)
        assertEquals(1_800_000L, controller.sleepTimerState.remainingMs)
        controller.setSleepTimer(SleepTimerOption.OFF)
        now = 10_000_000L
        controller.tickSleepTimer()
        assertTrue(controller.playWhenReady)
        controller.setSleepTimer(SleepTimerOption.MINUTES_60)
        controller.release()
        assertEquals(SleepTimerOption.OFF, controller.sleepTimerState.option)
    }

    @Test
    fun `expiry during initial async loading prevents late autoplay`() = runBlocking {
        var now = 0L
        val gate = CompletableDeferred<Episode>()
        val repository = object : CatalogRepository by FakeCatalogRepository() {
            override suspend fun resolvePlayback(detail: VideoDetail, episode: Episode, forceRefresh: Boolean) = gate.await()
        }
        val controller = VideoPlaybackController(FakePlaybackEngine(), repository, FakePlaybackSession(), monotonicClock = { now })
        val request = async(start = CoroutineStart.UNDISPATCHED) { controller.loadEpisode(sampleDetail(), sampleEpisode()) }
        controller.setSleepTimer(SleepTimerOption.MINUTES_15)
        now = 900_000L
        controller.tickSleepTimer()
        gate.complete(sampleEpisode())
        assertTrue(request.await() is EpisodeSwitchResult.Switched)
        assertFalse(controller.playWhenReady)
    }

    @Test
    fun `episode timer takes priority over outro and reentrant ended callbacks`() = runBlocking {
        val engine = FakePlaybackEngine()
        val episodes = listOf(sampleEpisode().copy(epSecond = 10), sampleEpisode(key = "second"))
        val controller = VideoPlaybackController(engine, FakeCatalogRepository(), FakePlaybackSession(), skipIntro = { true })
        controller.prepare(sampleDetail(episodes), episodes.first())
        controller.setSleepTimer(SleepTimerOption.END_OF_EPISODE)
        assertEquals(EpisodeSwitchResult.Unavailable, controller.advanceAutomatically(20_000L))
        controller.addStateListener {
            if (controller.isPlaybackEnded) runBlocking { controller.advanceToNextEpisodeIfEnded() }
        }
        engine.hasEnded = true
        engine.dispatchState()
        assertFalse(controller.playWhenReady)
        assertEquals(episodes.first(), controller.activeSession?.episode)
        assertEquals(SleepTimerOption.OFF, controller.sleepTimerState.option)
        assertEquals(EpisodeSwitchResult.Unavailable, controller.advanceToNextEpisodeIfEnded())
    }

    @Test
    fun `manual episode or video selection cancels episode timer but preserves minute timer`() = runBlocking {
        val episodes = listOf(sampleEpisode(), sampleEpisode(key = "second"))
        val controller = VideoPlaybackController(FakePlaybackEngine(), FakeCatalogRepository(), FakePlaybackSession())
        controller.prepare(sampleDetail(episodes), episodes.first())
        controller.setSleepTimer(SleepTimerOption.END_OF_EPISODE)
        controller.switchEpisode(1)
        assertEquals(SleepTimerOption.OFF, controller.sleepTimerState.option)
        controller.setSleepTimer(SleepTimerOption.END_OF_EPISODE)
        controller.openVideo("another")
        assertEquals(SleepTimerOption.OFF, controller.sleepTimerState.option)
        controller.setSleepTimer(SleepTimerOption.MINUTES_15)
        controller.openVideo("third")
        assertEquals(SleepTimerOption.MINUTES_15, controller.sleepTimerState.option)
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
        override var hasEnded = false
        override var isPlaying = false
        override var playWhenReady = false
        var setMediaCalls = 0
        var releaseCalls = 0
        var seekPositionMs = 0L
        private val positionListeners = linkedSetOf<(Long) -> Unit>()
        private val stateListeners = linkedSetOf<() -> Unit>()

        override fun setMediaUrl(mediaUrl: String) {
            setMediaCalls++
            hasEnded = false
        }

        override fun addStateListener(listener: () -> Unit): () -> Unit {
            stateListeners += listener
            return { stateListeners -= listener }
        }

        fun dispatchState() = stateListeners.toList().forEach { it() }

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
