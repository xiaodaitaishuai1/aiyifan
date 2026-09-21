package com.aiyifan.app.feature.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.aiyifan.app.core.data.CatalogRepository
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.core.ui.PlaybackScreenAwakeController
import com.aiyifan.app.core.ui.ScreenOffPlaybackObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface PlaybackEngine {
    val isPlaying: Boolean
    val playWhenReady: Boolean get() = isPlaying
    val isReady: Boolean get() = true
    val hasEnded: Boolean get() = false
    val currentPosition: Long
    val duration: Long

    fun setMediaUrl(mediaUrl: String)

    fun prepare()

    fun seekTo(positionMs: Long)

    fun addPositionListener(listener: (Long) -> Unit): () -> Unit
    fun addStateListener(listener: () -> Unit): () -> Unit = {}
    fun addSeekListener(listener: () -> Unit): () -> Unit = {}

    fun play()

    fun pause()

    fun attach(playerView: PlayerView)

    fun detach()

    fun release()
}

interface PlaybackSession {
    fun release()
}

data class ActivePlaybackSession(
    val detail: VideoDetail,
    val episode: Episode,
)

sealed interface EpisodeSwitchResult {
    data class Switched(val episode: Episode) : EpisodeSwitchResult

    data object Unavailable : EpisodeSwitchResult

    data object Failed : EpisodeSwitchResult
}

class VideoPlaybackController(
    private val engine: PlaybackEngine,
    private val repository: CatalogRepository,
    private val session: PlaybackSession,
    private val canPlay: () -> Boolean = { true },
    private val skipIntro: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var activeDetail: VideoDetail? = null
    private var activeEpisode: Episode? = null
    private var released = false
    private var paused = false
    private var replacingMedia = false
    private var requestVersion = 0L
    private var lastSaveAt = 0L
    private var lastPlayWhenReady = engine.playWhenReady
    private val stateListeners = linkedSetOf<() -> Unit>()
    private var screenObserver: ScreenOffPlaybackObserver? = null
    var isLoading: Boolean = false
        private set
    var hasOverlayOwner: Boolean = false
    var requestedMediaKey: String? = null
        private set
    val playWhenReady: Boolean get() = !released && engine.playWhenReady

    private val removeStateListener = engine.addStateListener {
        if (!replacingMedia && !released) {
            if (engine.playWhenReady && !canPlay()) pause()
            else {
                if (lastPlayWhenReady != engine.playWhenReady) paused = !engine.playWhenReady
                lastPlayWhenReady = engine.playWhenReady
                if (!engine.playWhenReady || engine.hasEnded) saveHistory()
                notifyState()
            }
        }
    }
    private val removeProgressListener = engine.addPositionListener {
        if (clock() - lastSaveAt >= 5_000L) saveHistory()
    }
    private val removeSeekListener = engine.addSeekListener(::saveHistory)

    /** Claims this shared controller when navigating between two video pages. */
    fun openVideo(mediaKey: String) {
        if (requestedMediaKey == mediaKey) return
        saveHistory()
        requestVersion++
        isLoading = false
        requestedMediaKey = mediaKey
        if (activeDetail?.mediaKey != mediaKey) {
            replacingMedia = true
            engine.pause()
            lastPlayWhenReady = false
            replacingMedia = false
            paused = !canPlay()
        }
    }

    val isPrepared: Boolean
        get() = !released && activeDetail != null && activeEpisode != null

    val isReleased: Boolean
        get() = released

    val isPlaying: Boolean
        get() = isPrepared && engine.isPlaying

    val currentPositionMs: Long
        get() = if (released) 0L else engine.currentPosition.coerceAtLeast(0L)

    val durationMs: Long
        get() = if (released) 0L else engine.duration.coerceAtLeast(0L)

    val activeSession: ActivePlaybackSession?
        get() = activeDetail?.let { detail ->
            activeEpisode?.let { episode -> ActivePlaybackSession(detail, episode) }
        }

    fun prepare(
        detail: VideoDetail,
        episode: Episode,
        startPositionMs: Long = 0L,
        shouldPlay: Boolean = true,
    ): Boolean {
        val mediaUrl = episode.mediaUrl?.trim().orEmpty()
        if (released || mediaUrl.isBlank()) return false
        saveHistory()
        requestVersion++
        isLoading = false
        replacingMedia = true
        try {
            engine.setMediaUrl(mediaUrl)
            activeDetail = detail
            activeEpisode = episode
            engine.prepare()
            if (startPositionMs > 0L) engine.seekTo(startPositionMs)
            if (shouldPlay && !paused && canPlay()) engine.play() else engine.pause()
            paused = !engine.playWhenReady
            lastPlayWhenReady = engine.playWhenReady
            lastSaveAt = clock()
        } finally {
            replacingMedia = false
        }
        notifyState()
        return true
    }

    fun attach(playerView: PlayerView) {
        if (!released) engine.attach(playerView)
    }

    fun detach() {
        engine.detach()
    }

    fun togglePlayPause() {
        if (!isPrepared) return

        if (engine.playWhenReady) {
            pause()
        } else if (canPlay()) {
            paused = false
            engine.play()
            notifyState()
        }
    }

    fun pause() {
        if (released) return
        paused = true
        lastPlayWhenReady = false
        engine.pause()
        saveHistory()
        notifyState()
    }

    fun seekTo(positionMs: Long) {
        if (isPrepared) {
            engine.seekTo(positionMs.coerceAtLeast(0L))
            saveHistory()
        }
    }

    suspend fun switchEpisode(offset: Int): EpisodeSwitchResult {
        val detail = activeDetail ?: return EpisodeSwitchResult.Unavailable
        val activeEpisode = activeEpisode ?: return EpisodeSwitchResult.Unavailable
        val activeIndex = detail.episodes.indexOfFirst { it.episodeKey == activeEpisode.episodeKey }
        if (activeIndex < 0 || offset !in listOf(-1, 1)) return EpisodeSwitchResult.Unavailable
        val targetEpisode = detail.episodes.getOrNull(activeIndex + offset) ?: return EpisodeSwitchResult.Unavailable
        return loadEpisode(detail, targetEpisode)
    }

    suspend fun loadEpisode(
        detail: VideoDetail,
        episode: Episode,
        resumePositionMs: Long = 0L,
    ): EpisodeSwitchResult {
        if (released || isLoading) return EpisodeSwitchResult.Unavailable
        if (requestedMediaKey != null && requestedMediaKey != detail.mediaKey) return EpisodeSwitchResult.Unavailable
        val version = ++requestVersion
        isLoading = true
        notifyState()
        try {
            val playable = repository.resolvePlayback(detail, episode)
            currentCoroutineContext().ensureActive()
            if (released || version != requestVersion) return EpisodeSwitchResult.Unavailable
            val position = AutoSkipPolicy.initialPositionMs(resumePositionMs, playable.opSecond, skipIntro())
            return if (prepare(detail, playable, position)) EpisodeSwitchResult.Switched(playable)
            else EpisodeSwitchResult.Failed
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return EpisodeSwitchResult.Failed
        } finally {
            if (version == requestVersion) {
                isLoading = false
                notifyState()
            }
        }
    }

    fun addStateListener(listener: () -> Unit): () -> Unit {
        stateListeners += listener
        listener()
        return { stateListeners -= listener }
    }

    private fun notifyState() = stateListeners.toList().forEach { it() }

    fun addPositionListener(listener: (Long) -> Unit): () -> Unit = engine.addPositionListener(listener)

    fun saveHistory() {
        val detail = activeDetail ?: return
        val episode = activeEpisode ?: return
        if (released || replacingMedia || (!engine.isReady && engine.currentPosition <= 0L)) return

        repository.saveHistory(
            detail = detail,
            episode = episode,
            progressMs = engine.currentPosition.coerceAtLeast(0L),
            durationMs = engine.duration.coerceAtLeast(0L),
        )
        lastSaveAt = clock()
    }

    fun release() {
        if (released) return

        saveHistory()
        released = true
        requestVersion++
        isLoading = false
        screenObserver?.release()
        removeProgressListener()
        removeStateListener()
        removeSeekListener()
        stateListeners.clear()
        engine.detach()
        session.release()
        engine.release()
    }

    companion object {
        @AndroidxOptIn(markerClass = [UnstableApi::class])
        fun create(
            applicationContext: Context,
            repository: CatalogRepository,
        ): VideoPlaybackController {
            val player = ExoPlayer.Builder(applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(applicationContext)))
                .build()
            val session = MediaSession.Builder(applicationContext, player).build()
            return VideoPlaybackController(
                engine = Media3PlaybackEngine(player),
                repository = repository,
                session = Media3PlaybackSession(session),
                canPlay = { ScreenOffPlaybackObserver.canPlay(applicationContext) },
                skipIntro = { AutoSkipPreferenceStore(applicationContext).isEnabled() },
            ).also { controller ->
                controller.screenObserver = ScreenOffPlaybackObserver(applicationContext, controller::pause)
            }
        }
    }
}

class VideoPlaybackControllerProvider(
    private val createController: () -> VideoPlaybackController,
) {
    private var controller: VideoPlaybackController? = null

    @Synchronized
    fun get(): VideoPlaybackController {
        val activeController = controller
        if (activeController != null && !activeController.isReleased) return activeController

        return createController().also { controller = it }
    }
}

private class Media3PlaybackEngine(
    private val player: ExoPlayer,
) : PlaybackEngine {
    private var attachedPlayerView: PlayerView? = null
    private var screenAwakeController: PlaybackScreenAwakeController? = null
    private val positionListeners = linkedSetOf<(Long) -> Unit>()
    private val stateListeners = linkedSetOf<() -> Unit>()
    private val seekListeners = linkedSetOf<() -> Unit>()
    private var pendingInitialSeek = false
    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionRunnable = object : Runnable {
        override fun run() {
            if (!player.isPlaying) return

            positionListeners.toList().forEach { listener -> listener(player.currentPosition.coerceAtLeast(0L)) }
            positionHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) seekListeners.toList().forEach { it() }
        }
        override fun onEvents(player: Player, events: Player.Events) {
            if (pendingInitialSeek && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED)) {
                pendingInitialSeek = false
                if (player.duration > 0 && player.currentPosition >= player.duration) player.seekTo(0L)
            }
            stateListeners.toList().forEach { it() }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            screenAwakeController?.onIsPlayingChanged(isPlaying)
            if (isPlaying) startPositionUpdates() else positionHandler.removeCallbacks(positionRunnable)
        }
    }

    init {
        player.addListener(playerListener)
    }

    override val isPlaying: Boolean
        get() = player.isPlaying
    override val playWhenReady: Boolean get() = player.playWhenReady
    override val isReady: Boolean
        get() = player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED
    override val hasEnded: Boolean get() = player.playbackState == Player.STATE_ENDED

    override fun addSeekListener(listener: () -> Unit): () -> Unit {
        seekListeners += listener
        return { seekListeners -= listener }
    }

    override fun addStateListener(listener: () -> Unit): () -> Unit {
        stateListeners += listener
        return { stateListeners -= listener }
    }

    override val currentPosition: Long
        get() = player.currentPosition

    override val duration: Long
        get() = player.duration

    override fun setMediaUrl(mediaUrl: String) {
        pendingInitialSeek = true
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(mediaUrl))
    }

    override fun prepare() {
        player.prepare()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun addPositionListener(listener: (Long) -> Unit): () -> Unit {
        positionListeners += listener
        if (player.isPlaying) startPositionUpdates()
        return {
            positionListeners -= listener
            if (positionListeners.isEmpty()) positionHandler.removeCallbacks(positionRunnable)
        }
    }

    override fun play() {
        player.play()
        startPositionUpdates()
    }

    override fun pause() {
        player.pause()
    }

    override fun attach(playerView: PlayerView) {
        if (attachedPlayerView === playerView) return

        screenAwakeController?.detach()
        attachedPlayerView?.player = null
        playerView.player = player
        attachedPlayerView = playerView
        screenAwakeController = PlaybackScreenAwakeController(playerView::setKeepScreenOn).also {
            it.attach(player.isPlaying)
        }
    }

    override fun detach() {
        screenAwakeController?.detach()
        screenAwakeController = null
        attachedPlayerView?.player = null
        attachedPlayerView = null
    }

    override fun release() {
        detach()
        positionHandler.removeCallbacks(positionRunnable)
        positionListeners.clear()
        stateListeners.clear()
        seekListeners.clear()
        player.removeListener(playerListener)
        player.release()
    }

    private fun startPositionUpdates() {
        positionHandler.removeCallbacks(positionRunnable)
        positionHandler.post(positionRunnable)
    }

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 1_000L
    }
}

private class Media3PlaybackSession(
    private val session: MediaSession,
) : PlaybackSession {
    override fun release() {
        session.release()
    }
}
