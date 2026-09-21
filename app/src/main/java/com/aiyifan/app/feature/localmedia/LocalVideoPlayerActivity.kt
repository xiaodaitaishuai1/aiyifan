package com.aiyifan.app.feature.localmedia

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.aiyifan.app.R
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.PlaybackScreenAwakeController
import com.aiyifan.app.core.ui.ScreenOffPlaybackObserver
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivityLocalVideoPlayerBinding
import com.aiyifan.app.feature.localmedia.data.LocalPlaybackStore
import com.aiyifan.app.feature.localmedia.model.LocalVideo

class LocalVideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLocalVideoPlayerBinding
    private lateinit var player: ExoPlayer
    private lateinit var playbackStore: LocalPlaybackStore
    private lateinit var screenAwakeController: PlaybackScreenAwakeController
    private var video: LocalVideo? = null
    private var isFullScreen = false
    private var finishedPlayback = false
    private var screenObserver: ScreenOffPlaybackObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivityLocalVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        screenAwakeController = PlaybackScreenAwakeController(binding.localPlayerView::setKeepScreenOn)
        binding.localPlayerTopBar.applySystemBarsPadding(top = true, growHeight = true)

        playbackStore = LocalPlaybackStore(this)
        val parsed = parseVideo(intent)
        if (parsed == null) {
            finish()
            return
        }
        video = parsed

        player = ExoPlayer.Builder(this).build()
        binding.localPlayerView.player = player
        screenAwakeController.attach(player.isPlaying)
        player.setMediaItem(MediaItem.fromUri(parsed.contentUri))
        player.playWhenReady = ScreenOffPlaybackObserver.canPlay(this)

        val resumePosition = LocalPlayerPositionPolicy.clampPosition(
            playbackStore.load().firstOrNull { it.mediaStoreId == parsed.id }?.positionMs ?: 0L,
            parsed.durationMs,
        )
        if (resumePosition > 0L) player.seekTo(resumePosition)
        player.prepare()
        screenObserver = ScreenOffPlaybackObserver(this) { pausePlayback() }

        binding.localVideoTitle.text = parsed.displayName
        binding.localBackButton.setOnClickListener { finish() }
        binding.localFullScreenButton.setOnClickListener { setFullScreen(!isFullScreen) }
        binding.localSpeedButton.setOnClickListener { showSpeedDialog() }

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (player.playWhenReady && !ScreenOffPlaybackObserver.canPlay(this@LocalVideoPlayerActivity)) {
                    pausePlayback()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                screenAwakeController.onIsPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    screenAwakeController.onIsPlayingChanged(false)
                    finishedPlayback = true
                    persistPosition(0L)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                screenAwakeController.onIsPlayingChanged(false)
                persistPosition(player.currentPosition)
                finish()
            }
        })
        screenAwakeController.onIsPlayingChanged(player.isPlaying)

        binding.localPlayerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visible ->
                binding.localPlayerTopBar.isVisible = visible == View.VISIBLE
            },
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isFullScreen = true
        }
    }

    override fun onStop() {
        if (::player.isInitialized) player.pause()
        if (::player.isInitialized && !finishedPlayback) {
            persistPosition(player.currentPosition)
        }
        super.onStop()
    }

    override fun onDestroy() {
        screenObserver?.release()
        if (::screenAwakeController.isInitialized) {
            screenAwakeController.detach()
        }
        if (::player.isInitialized) {
            binding.localPlayerView.player = null
            player.release()
        }
        super.onDestroy()
    }

    private fun setFullScreen(fullScreen: Boolean) {
        isFullScreen = fullScreen
        requestedOrientation = if (fullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        binding.localPlayerTopBar.applySystemBarsPadding(top = true, growHeight = true)
    }

    private fun showSpeedDialog() {
        val speeds = LocalPlayerPositionPolicy.speedOptions
        val labels = speeds.map { "%.2fx".format(it) }.toTypedArray()
        val current = speeds.indexOfFirst { it == player.playbackParameters.speed }.coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.local_media_speed)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                player.playbackParameters = PlaybackParameters(speeds[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun persistPosition(positionMs: Long) {
        val video = video ?: return
        val clamped = LocalPlayerPositionPolicy.clampPosition(positionMs, video.durationMs)
        playbackStore.save(
            LocalPlayerPositionPolicy.recordFor(
                mediaStoreId = video.id,
                contentUri = video.contentUri,
                displayName = video.displayName,
                durationMs = video.durationMs,
                positionMs = clamped,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun pausePlayback() {
        if (!::player.isInitialized) return
        player.pause()
        if (!finishedPlayback) persistPosition(player.currentPosition)
    }

    private fun parseVideo(intent: Intent): LocalVideo? {
        val contentUri = LocalPlayerPositionPolicy.contentUriFrom(intent.getStringExtra(EXTRA_CONTENT_URI))
        if (contentUri.isEmpty()) return null
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0L) return null
        return LocalVideo(
            id = id,
            contentUri = contentUri,
            displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty(),
            durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L),
            sizeBytes = intent.getLongExtra(EXTRA_SIZE_BYTES, 0L),
            dateAddedMs = intent.getLongExtra(EXTRA_DATE_ADDED_MS, 0L),
            bucketName = intent.getStringExtra(EXTRA_BUCKET_NAME),
        )
    }

    companion object {
        private const val EXTRA_CONTENT_URI = "contentUri"
        private const val EXTRA_ID = "id"
        private const val EXTRA_DISPLAY_NAME = "displayName"
        private const val EXTRA_DURATION_MS = "durationMs"
        private const val EXTRA_SIZE_BYTES = "sizeBytes"
        private const val EXTRA_DATE_ADDED_MS = "dateAddedMs"
        private const val EXTRA_BUCKET_NAME = "bucketName"

        fun intent(context: Context, video: LocalVideo): Intent =
            Intent(context, LocalVideoPlayerActivity::class.java)
                .putExtra(EXTRA_CONTENT_URI, video.contentUri)
                .putExtra(EXTRA_ID, video.id)
                .putExtra(EXTRA_DISPLAY_NAME, video.displayName)
                .putExtra(EXTRA_DURATION_MS, video.durationMs)
                .putExtra(EXTRA_SIZE_BYTES, video.sizeBytes)
                .putExtra(EXTRA_DATE_ADDED_MS, video.dateAddedMs)
                .putExtra(EXTRA_BUCKET_NAME, video.bucketName)
    }
}
