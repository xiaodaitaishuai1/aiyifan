package com.aiyifan.app.feature.video

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.core.ui.CommentAdapter
import com.aiyifan.app.core.ui.EpisodeAdapter
import com.aiyifan.app.core.ui.VideoListAdapter
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivityVideoPlayerBinding
import kotlinx.coroutines.launch

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private val playbackController: VideoPlaybackController
        get() = AppGraph.videoPlaybackController
    private var detail: VideoDetail? = null
    private var selectedEpisode: Episode? = null
    private var playingEpisode: Episode? = null
    private lateinit var episodeAdapter: EpisodeAdapter
    private var isFullScreen = false
    private var isInAppMiniPlayerVisible = false
    private var restoreMiniPlayerOnStart = false
    private var pendingFloatingRecoveryPositionMs: Long? = null
    private var controllerVisibility = View.GONE

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.pageContent.applySystemBarsPadding(
            left = true,
            right = true,
            bottom = true,
            shouldApply = { FullScreenInsetsPolicy.shouldApplyPageInsets(isFullScreen) },
        )
        binding.playerTopBar.applySystemBarsPadding(top = true, growHeight = true)

        binding.backButton.setOnClickListener { handleBack() }
        binding.fullScreenButton.setOnClickListener { setFullScreen(!isFullScreen) }
        binding.fullscreenExitButton.setOnClickListener { setFullScreen(false) }
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { controllerVisibility ->
                this.controllerVisibility = controllerVisibility
                updateFullScreenExitButton()
            },
        )
        binding.floatingWindowButton.setOnClickListener { showFloatingPresentation() }
        binding.inAppMiniPlayPauseButton.setOnClickListener {
            playbackController.togglePlayPause()
            updateInAppMiniPlayPauseButton()
        }
        binding.inAppMiniCloseButton.setOnClickListener { closePlayback() }
        binding.inAppMiniResizeHandle.setOnTouchListener(InAppMiniResizeTouchListener())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })
        setupStaticLists()
        loadDetail(intent.getStringExtra(EXTRA_MEDIA_KEY).orEmpty())
    }

    override fun onStart() {
        super.onStart()
        FloatingPlayerRecovery.consumePosition(this)?.let(::restorePlaybackAfterFloatingWindow)
        if (restoreMiniPlayerOnStart && !isInPictureInPictureMode) {
            restoreMiniPlayerOnStart = false
            showInAppMiniPlayer()
        }
    }

    private fun setupStaticLists() {
        episodeAdapter = EpisodeAdapter { episode ->
            selectedEpisode = episode
            detail?.let { currentDetail ->
                episodeAdapter.submitList(currentDetail.episodes, episode)
                loadEpisodePlayback(currentDetail, episode)
            }
        }
        binding.episodeRecycler.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.episodeRecycler.adapter = episodeAdapter

        binding.relatedRecycler.layoutManager = LinearLayoutManager(this)
        binding.commentRecycler.layoutManager = LinearLayoutManager(this)
    }

    private fun loadDetail(mediaKey: String) {
        if (mediaKey.isBlank()) {
            Toast.makeText(this, R.string.video_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.title.setText(R.string.video_loading)
        lifecycleScope.launch {
            runCatching { AppGraph.catalogRepository.getVideoDetail(mediaKey) }
                .onSuccess { loadedDetail ->
                    detail = loadedDetail
                    selectedEpisode = loadedDetail.defaultEpisode
                    renderDetail(loadedDetail)
                    loadedDetail.defaultEpisode?.let { episode ->
                        episodeAdapter.submitList(loadedDetail.episodes, episode)
                        loadEpisodePlayback(loadedDetail, episode)
                    }
                }
                .onFailure {
                    Toast.makeText(this@VideoPlayerActivity, R.string.video_detail_load_failed, Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

    private fun renderDetail(detail: VideoDetail) {
        binding.title.text = detail.title
        binding.meta.text = listOfNotNull(
            detail.typeName,
            detail.publishTime,
            detail.updateMsg,
            getString(R.string.video_play_count, detail.playCount),
        ).joinToString(" / ")
        binding.intro.text = listOfNotNull(
            detail.director?.takeIf(String::isNotBlank)?.let { getString(R.string.video_director, it) },
            detail.actor?.takeIf(String::isNotBlank)?.let { getString(R.string.video_cast, it) },
            detail.introduce?.takeIf(String::isNotBlank),
        ).joinToString("\n")
        binding.favoriteButton.setText(
            if (AppGraph.catalogRepository.isFavorite(detail.mediaKey)) R.string.video_favorited else R.string.video_favorite,
        )
        binding.favoriteButton.setOnClickListener {
            val isFavorite = AppGraph.catalogRepository.toggleFavorite(detail)
            binding.favoriteButton.setText(if (isFavorite) R.string.video_favorited else R.string.video_favorite)
            Toast.makeText(
                this,
                if (isFavorite) R.string.video_favorite_added else R.string.video_favorite_removed,
                Toast.LENGTH_SHORT,
            ).show()
        }
        binding.shareButton.setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, detail.title)
            }, getString(R.string.video_share_chooser)))
        }

        val relatedAdapter = VideoListAdapter { video ->
            startActivity(intent(this, video.mediaKey))
        }
        binding.relatedRecycler.adapter = relatedAdapter
        relatedAdapter.submitList(detail.related)

        val commentAdapter = CommentAdapter()
        binding.commentRecycler.adapter = commentAdapter
        commentAdapter.submitList(AppGraph.catalogRepository.getComments(detail.mediaKey))
    }

    private fun loadEpisodePlayback(detail: VideoDetail, episode: Episode) {
        binding.meta.text = listOfNotNull(
            detail.typeName,
            detail.publishTime,
            detail.updateMsg,
            getString(R.string.video_play_count, detail.playCount),
            getString(R.string.video_stream_loading),
        ).joinToString(" / ")
        lifecycleScope.launch {
            runCatching { AppGraph.catalogRepository.resolvePlayback(detail, episode) }
                .onSuccess { playableEpisode ->
                    playingEpisode = playableEpisode
                    val resumePositionMs = pendingFloatingRecoveryPositionMs ?: 0L
                    if (playbackController.prepare(detail, playableEpisode, resumePositionMs)) {
                        pendingFloatingRecoveryPositionMs = null
                        attachPlayerToCurrentSurface()
                    } else {
                        Toast.makeText(this@VideoPlayerActivity, R.string.video_no_playable_stream, Toast.LENGTH_SHORT).show()
                    }
                    binding.meta.text = listOfNotNull(
                        detail.typeName,
                        detail.publishTime,
                        detail.updateMsg,
                        getString(R.string.video_play_count, detail.playCount),
                    ).joinToString(" / ")
                }
                .onFailure {
                    Toast.makeText(this@VideoPlayerActivity, R.string.video_stream_load_failed, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun attachPlayerToCurrentSurface() {
        if (isInAppMiniPlayerVisible) {
            playbackController.attach(binding.inAppMiniPlayerView)
        } else {
            playbackController.attach(binding.playerView)
        }
    }

    private fun handleBack() {
        when (
            VideoPlayerBackBehavior.action(
                isFullScreen = isFullScreen,
                canMinimizeInApp = currentDestination() == PlaybackDestination.IN_APP_MINI_PLAYER,
            )
        ) {
            VideoPlayerBackAction.EXIT_FULL_SCREEN -> setFullScreen(false)
            VideoPlayerBackAction.MINIMIZE_TO_IN_APP_PLAYER -> showInAppMiniPlayer()
            VideoPlayerBackAction.NAVIGATE_UP -> closePlayback()
        }
    }

    private fun setFullScreen(enabled: Boolean) {
        isFullScreen = enabled
        requestedOrientation = if (enabled) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        binding.fullScreenButton.setImageResource(
            if (enabled) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,
        )
        binding.fullScreenButton.contentDescription = if (enabled) "退出全屏" else "全屏播放"
        WindowCompat.getInsetsController(window, binding.root).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
        binding.playerTopBar.isVisible = !enabled
        ViewCompat.requestApplyInsets(binding.pageContent)
        updateFullScreenExitButton()
        binding.contentScroll.isVisible = !enabled && !isInAppMiniPlayerVisible
        (binding.playerContainer.layoutParams as LinearLayout.LayoutParams).apply {
            height = if (enabled) 0 else dpToPx(NORMAL_PLAYER_HEIGHT_DP)
            weight = if (enabled) 1f else 0f
            binding.playerContainer.layoutParams = this
        }
    }

    private fun updateFullScreenExitButton() {
        binding.fullscreenExitButton.isVisible = FullScreenControlVisibility.shouldShowExitButton(
            isFullScreen = isFullScreen,
            controllerVisibility = controllerVisibility,
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        when (currentDestination()) {
            PlaybackDestination.PICTURE_IN_PICTURE -> {
                if (!enterSystemPictureInPicture()) {
                    if (Settings.canDrawOverlays(this)) {
                        startOverlayPlayer()
                    } else {
                        showInAppMiniPlayer()
                    }
                }
            }

            PlaybackDestination.OVERLAY -> startOverlayPlayer()
            PlaybackDestination.IN_APP_MINI_PLAYER,
            PlaybackDestination.NONE,
            -> Unit
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        binding.contentScroll.isVisible = !isInPictureInPictureMode && !isInAppMiniPlayerVisible && !isFullScreen
        binding.fullScreenButton.isVisible = !isInPictureInPictureMode
        binding.floatingWindowButton.isVisible = !isInPictureInPictureMode
        binding.backButton.isVisible = !isInPictureInPictureMode
        binding.playerTopBar.isVisible = !isInPictureInPictureMode && !isFullScreen
    }

    private fun enterSystemPictureInPicture(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }.getOrDefault(false)
    }

    private fun showFloatingPresentation() {
        if (Settings.canDrawOverlays(this)) {
            startOverlayPlayer()
        } else {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun startOverlayPlayer() {
        if (!Settings.canDrawOverlays(this)) return
        ContextCompat.startForegroundService(this, FloatingPlayerService.intent(this))
        moveTaskToBack(true)
    }

    private fun showInAppMiniPlayer() {
        if (!playbackController.isPrepared) return
        isInAppMiniPlayerVisible = true
        binding.inAppMiniPlayer.isVisible = true
        binding.contentScroll.isVisible = false
        binding.playerContainer.isVisible = false
        playbackController.attach(binding.inAppMiniPlayerView)
        updateInAppMiniPlayPauseButton()
    }

    private fun updateInAppMiniPlayPauseButton() {
        binding.inAppMiniPlayPauseButton.setImageResource(
            if (playbackController.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        )
        binding.inAppMiniPlayPauseButton.contentDescription = if (playbackController.isPlaying) "暂停播放" else "继续播放"
    }

    private fun restorePlaybackAfterFloatingWindow(positionMs: Long) {
        val activeDetail = detail
        val activeEpisode = playingEpisode
        if (activeDetail == null || activeEpisode == null) {
            pendingFloatingRecoveryPositionMs = positionMs
            return
        }
        if (playbackController.prepare(activeDetail, activeEpisode, positionMs)) {
            pendingFloatingRecoveryPositionMs = null
            isInAppMiniPlayerVisible = false
            binding.inAppMiniPlayer.isVisible = false
            binding.playerContainer.isVisible = true
            attachPlayerToCurrentSurface()
        }
    }

    private fun currentDestination(): PlaybackDestination = PlaybackPresentationPolicy.destinationWhenLeaving(
        PlaybackCapabilities(
            hasPlayableMedia = playingEpisode?.mediaUrl?.isNotBlank() == true,
            isPrepared = playbackController.isPrepared,
            isPlaying = playbackController.isPlaying,
            supportsPictureInPicture = supportsSystemPictureInPicture(),
            pictureInPictureEnabled = supportsSystemPictureInPicture(),
            hasOverlayPermission = Settings.canDrawOverlays(this),
        ),
    )

    private fun supportsSystemPictureInPicture(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode && isInAppMiniPlayerVisible && !isChangingConfigurations) {
            restoreMiniPlayerOnStart = true
            isInAppMiniPlayerVisible = false
            binding.inAppMiniPlayer.isVisible = false
            if (playbackController.isPlaying) playbackController.togglePlayPause()
        }
        playbackController.saveHistory()
    }

    override fun onDestroy() {
        binding.playerView.player = null
        binding.inAppMiniPlayerView.player = null
        if (isFinishing && !isInPictureInPictureMode) {
            playbackController.saveHistory()
            playbackController.release()
        }
        super.onDestroy()
    }

    private fun closePlayback() {
        playbackController.saveHistory()
        playbackController.release()
        finish()
    }

    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class InAppMiniResizeTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var initialWidth = 0

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = binding.inAppMiniPlayer.layoutParams as FrameLayout.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    initialWidth = params.width
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val maxWidth = (binding.root.width - dpToPx(IN_APP_MINI_MARGIN_DP * 2)).coerceAtLeast(dpToPx(IN_APP_MINI_MIN_WIDTH_DP))
                    val size = FloatingWindowSizePolicy.resize(
                        requestedWidth = initialWidth + (downRawX - event.rawX).toInt(),
                        minWidth = dpToPx(IN_APP_MINI_MIN_WIDTH_DP),
                        maxWidth = maxWidth,
                    )
                    params.width = size.width
                    params.height = size.height
                    binding.inAppMiniPlayer.layoutParams = params
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val EXTRA_MEDIA_KEY = "mediaKey"
        private const val NORMAL_PLAYER_HEIGHT_DP = 211
        private const val IN_APP_MINI_MIN_WIDTH_DP = 180
        private const val IN_APP_MINI_MARGIN_DP = 16
        fun intent(context: Context, mediaKey: String): Intent =
            Intent(context, VideoPlayerActivity::class.java).putExtra(EXTRA_MEDIA_KEY, mediaKey)
    }
}
