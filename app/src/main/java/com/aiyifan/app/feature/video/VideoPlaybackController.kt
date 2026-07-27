package com.aiyifan.app.feature.video

import android.content.Context
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.aiyifan.app.core.data.CatalogRepository
import com.aiyifan.app.core.data.remote.LocalProxyEndpoint
import com.aiyifan.app.core.data.remote.ProxyConnectionPolicy
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import okhttp3.OkHttpClient

interface PlaybackEngine {
    val isPlaying: Boolean
    val currentPosition: Long
    val duration: Long

    fun setMediaUrl(mediaUrl: String)

    fun prepare()

    fun seekTo(positionMs: Long)

    fun play()

    fun pause()

    fun attach(playerView: PlayerView)

    fun detach()

    fun release()
}

interface PlaybackSession {
    fun release()
}

class VideoPlaybackController(
    private val engine: PlaybackEngine,
    private val repository: CatalogRepository,
    private val session: PlaybackSession,
) {
    private var activeDetail: VideoDetail? = null
    private var activeEpisode: Episode? = null
    private var released = false

    val isPrepared: Boolean
        get() = !released && activeDetail != null && activeEpisode != null

    val isReleased: Boolean
        get() = released

    val isPlaying: Boolean
        get() = isPrepared && engine.isPlaying

    val currentPositionMs: Long
        get() = if (released) 0L else engine.currentPosition.coerceAtLeast(0L)

    fun prepare(
        detail: VideoDetail,
        episode: Episode,
        startPositionMs: Long = 0L,
        shouldPlay: Boolean = true,
    ): Boolean {
        val mediaUrl = episode.mediaUrl?.trim().orEmpty()
        if (released || mediaUrl.isBlank()) return false

        engine.setMediaUrl(mediaUrl)
        engine.prepare()
        if (startPositionMs > 0L) engine.seekTo(startPositionMs)
        if (shouldPlay) engine.play() else engine.pause()
        activeDetail = detail
        activeEpisode = episode
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

        if (engine.isPlaying) {
            engine.pause()
        } else {
            engine.play()
        }
    }

    fun saveHistory() {
        val detail = activeDetail ?: return
        val episode = activeEpisode ?: return
        if (released) return

        repository.saveHistory(
            detail = detail,
            episode = episode,
            progressMs = engine.currentPosition.coerceAtLeast(0L),
            durationMs = engine.duration.coerceAtLeast(0L),
        )
    }

    fun release() {
        if (released) return

        released = true
        engine.detach()
        session.release()
        engine.release()
    }

    companion object {
        @AndroidxOptIn(markerClass = [UnstableApi::class])
        fun create(
            applicationContext: Context,
            repository: CatalogRepository,
            proxyEndpointProvider: () -> LocalProxyEndpoint? = { null },
        ): VideoPlaybackController {
            val dataSourceFactory = ProxyAwareDataSourceFactory(applicationContext, proxyEndpointProvider)
            val player = ExoPlayer.Builder(applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
            val session = MediaSession.Builder(applicationContext, player).build()
            return VideoPlaybackController(
                engine = Media3PlaybackEngine(player),
                repository = repository,
                session = Media3PlaybackSession(session),
            )
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

private class ProxyAwareDataSourceFactory(
    private val context: Context,
    private val proxyEndpointProvider: () -> LocalProxyEndpoint?,
) : DataSource.Factory {
    @AndroidxOptIn(markerClass = [UnstableApi::class])
    override fun createDataSource(): DataSource {
        val httpClient = OkHttpClient.Builder().apply {
            ProxyConnectionPolicy.select(proxyEndpointProvider())?.let(::proxy)
        }.build()
        return DefaultDataSource.Factory(context, OkHttpDataSource.Factory(httpClient)).createDataSource()
    }
}

private class Media3PlaybackEngine(
    private val player: ExoPlayer,
) : PlaybackEngine {
    private var attachedPlayerView: PlayerView? = null

    override val isPlaying: Boolean
        get() = player.isPlaying

    override val currentPosition: Long
        get() = player.currentPosition

    override val duration: Long
        get() = player.duration

    override fun setMediaUrl(mediaUrl: String) {
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(mediaUrl))
    }

    override fun prepare() {
        player.prepare()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun attach(playerView: PlayerView) {
        if (attachedPlayerView === playerView) return

        attachedPlayerView?.player = null
        playerView.player = player
        attachedPlayerView = playerView
    }

    override fun detach() {
        attachedPlayerView?.player = null
        attachedPlayerView = null
    }

    override fun release() {
        player.release()
    }
}

private class Media3PlaybackSession(
    private val session: MediaSession,
) : PlaybackSession {
    override fun release() {
        session.release()
    }
}
