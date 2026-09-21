package com.aiyifan.app.feature.video

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.media.AudioManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageButton
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
import com.aiyifan.app.core.ui.ScreenOffPlaybackObserver
import com.aiyifan.app.databinding.ActivityVideoPlayerBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.abs

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var playbackController: VideoPlaybackController
    private var detail: VideoDetail? = null
    private var selectedEpisode: Episode? = null
    private var playingEpisode: Episode? = null
    private lateinit var episodeAdapter: EpisodeAdapter
    private var isFullScreen = false
    private var isInAppMiniPlayerVisible = false
    private var restoreMiniPlayerOnStart = false
    private var controllerVisibility = View.GONE
    private lateinit var autoSkipPreferenceStore: AutoSkipPreferenceStore
    private var removePositionListener: (() -> Unit)? = null
    private var removeStateListener: (() -> Unit)? = null
    private var playbackJob: Job? = null
    private var detailJob: Job? = null
    private var overlayHandoffPending = false
    private var playerBrightness = -1f
    private var hasAutomaticallyAdvancedOutro = false
    private val gestureFeedbackHideRunnable = Runnable { binding.playerGestureFeedback.isVisible = false }

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        playbackController = AppGraph.videoPlaybackController
        autoSkipPreferenceStore = AutoSkipPreferenceStore(this)
        binding.pageContent.applySystemBarsPadding(
            left = true,
            right = true,
            bottom = true,
            shouldApply = { FullScreenInsetsPolicy.shouldApplyPageInsets(isFullScreen) },
        )
        binding.playerTopBar.applySystemBarsPadding(top = true, growHeight = true)

        binding.backButton.setOnClickListener { handleBack() }
        binding.fullScreenButton.setOnClickListener { setFullScreen(!isFullScreen) }
        binding.fullScreenBackButton.setOnClickListener { setFullScreen(false) }
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { controllerVisibility ->
                this.controllerVisibility = controllerVisibility
                updateFullScreenTitleBar()
            },
        )
        binding.playerView.setOnTouchListener(PlayerGestureTouchListener())
        binding.playerView.findViewById<ImageButton>(R.id.playerPreviousEpisodeButton).setOnClickListener { switchEpisode(-1) }
        binding.playerView.findViewById<ImageButton>(R.id.playerNextEpisodeButton).setOnClickListener { switchEpisode(1) }
        binding.floatingWindowButton.setOnClickListener { showFloatingPresentation() }
        binding.autoSkipIntroOutroSwitch.apply {
            isChecked = autoSkipPreferenceStore.isEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                autoSkipPreferenceStore.setEnabled(isChecked)
            }
        }
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
    }

    override fun onStart() {
        super.onStart()
        if (playbackController.isReleased) playbackController = AppGraph.videoPlaybackController
        if (playbackController.hasOverlayOwner) {
            playbackController.hasOverlayOwner = false
            stopService(FloatingPlayerService.intent(this))
        }
        val mediaKey = intent.getStringExtra(EXTRA_MEDIA_KEY).orEmpty()
        val needsLoad = playbackController.requestedMediaKey != mediaKey || playbackController.activeSession == null && !playbackController.isLoading
        playbackController.openVideo(mediaKey)
        overlayHandoffPending = false
        window.attributes = window.attributes.apply { screenBrightness = playerBrightness }
        registerPositionListener()
        removeStateListener = playbackController.addStateListener {
            syncFromActivePlaybackSession()
            updateEpisodeButtons()
            updateInAppMiniPlayPauseButton()
        }
        syncFromActivePlaybackSession()
        if (needsLoad) loadDetail(mediaKey)
        val recovery = FloatingPlayerRecovery.consumePosition(this)
        if (playbackController.activeSession?.detail?.mediaKey == intent.getStringExtra(EXTRA_MEDIA_KEY)) {
            if (recovery != null || playbackController.hasOverlayOwner) {
                playbackController.hasOverlayOwner = false
                stopService(FloatingPlayerService.intent(this))
            }
            attachPlayerToCurrentSurface()
        }
        if (restoreMiniPlayerOnStart && !isInPictureInPictureMode) {
            restoreMiniPlayerOnStart = false
            showInAppMiniPlayer()
        }
    }

    private fun setupStaticLists() {
        episodeAdapter = EpisodeAdapter { episode ->
            detail?.let { currentDetail ->
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
        if (playbackController.activeSession?.detail?.mediaKey == mediaKey) {
            syncFromActivePlaybackSession()
            attachPlayerToCurrentSurface()
            return
        }
        binding.title.setText(R.string.video_loading)
        binding.videoTitle.setText(R.string.video_loading)
        binding.fullScreenVideoTitle.setText(R.string.video_loading)
        detailJob?.cancel()
        detailJob = lifecycleScope.launch {
            runCatching { AppGraph.catalogRepository.getVideoDetail(mediaKey) }
                .onSuccess { loadedDetail ->
                    if (playbackController.requestedMediaKey != mediaKey) return@onSuccess
                    detail = loadedDetail
                    renderDetail(loadedDetail)
                    val history = AppGraph.catalogRepository.getHistory().firstOrNull { it.mediaKey == mediaKey }
                    PlaybackResumePolicy.target(loadedDetail, history)?.let { target ->
                        episodeAdapter.submitList(loadedDetail.episodes, target.episode)
                        loadEpisodePlayback(loadedDetail, target.episode, resumePositionMs = target.positionMs)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (playbackController.requestedMediaKey != mediaKey) return@onFailure
                    Toast.makeText(this@VideoPlayerActivity, R.string.video_detail_load_failed, Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

    private fun renderDetail(detail: VideoDetail) {
        binding.title.text = detail.title
        binding.videoTitle.text = detail.title
        binding.fullScreenVideoTitle.text = detail.title
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

    private fun loadEpisodePlayback(
        detail: VideoDetail,
        episode: Episode,
        resetOutroAdvance: Boolean = true,
        resumePositionMs: Long = 0L,
    ) {
        if (playbackController.isLoading) return
        if (resetOutroAdvance) hasAutomaticallyAdvancedOutro = false
        binding.meta.text = listOfNotNull(
            detail.typeName,
            detail.publishTime,
            detail.updateMsg,
            getString(R.string.video_play_count, detail.playCount),
            getString(R.string.video_stream_loading),
        ).joinToString(" / ")
        playbackJob = lifecycleScope.launch {
            handleEpisodeResult(playbackController.loadEpisode(detail, episode, resumePositionMs))
            binding.meta.text = listOfNotNull(
                detail.typeName, detail.publishTime, detail.updateMsg,
                getString(R.string.video_play_count, detail.playCount),
            ).joinToString(" / ")
        }
    }

    private fun switchEpisode(offset: Int) {
        if (playbackController.isLoading) return
        hasAutomaticallyAdvancedOutro = false
        playbackJob = lifecycleScope.launch { handleEpisodeResult(playbackController.switchEpisode(offset)) }
    }

    private fun handleEpisodeResult(result: EpisodeSwitchResult) {
        when (result) {
            is EpisodeSwitchResult.Switched -> {
                hasAutomaticallyAdvancedOutro = false
                syncFromActivePlaybackSession()
                if (!playbackController.hasOverlayOwner) attachPlayerToCurrentSurface()
            }
            EpisodeSwitchResult.Failed -> Toast.makeText(this, R.string.video_stream_load_failed, Toast.LENGTH_SHORT).show()
            EpisodeSwitchResult.Unavailable -> Unit
        }
        updateEpisodeButtons()
    }

    private fun updateEpisodeButtons() {
        val session = playbackController.activeSession?.takeIf { it.detail.mediaKey == intent.getStringExtra(EXTRA_MEDIA_KEY) }
        val index = session?.detail?.episodes?.indexOfFirst { it.episodeKey == session.episode.episodeKey } ?: -1
        val count = session?.detail?.episodes?.size ?: 0
        for ((id, offset) in listOf(R.id.playerPreviousEpisodeButton to -1, R.id.playerNextEpisodeButton to 1)) {
            binding.playerView.findViewById<ImageButton>(id).apply {
                isEnabled = !playbackController.isLoading && index >= 0 && index + offset in 0 until count
                alpha = if (isEnabled) 1f else 0.4f
            }
        }
    }

    private fun handlePlaybackPosition(positionMs: Long) {
        val activeDetail = detail ?: return
        val activeEpisode = playingEpisode ?: return
        val activeIndex = activeDetail.episodes.indexOfFirst { it.episodeKey == activeEpisode.episodeKey }
        if (activeIndex < 0) return
        val nextEpisode = activeDetail.episodes.getOrNull(activeIndex + 1) ?: return
        if (
            !AutoSkipPolicy.shouldAdvance(
                positionMs = positionMs,
                outroSecond = activeEpisode.epSecond,
                enabled = autoSkipPreferenceStore.isEnabled(),
                hasNextEpisode = true,
                hasAdvanced = hasAutomaticallyAdvancedOutro,
            )
        ) {
            return
        }

        hasAutomaticallyAdvancedOutro = true
        Toast.makeText(this, R.string.auto_skip_outro, Toast.LENGTH_SHORT).show()
        loadEpisodePlayback(activeDetail, nextEpisode, resetOutroAdvance = false)
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
        updateFullScreenTitleBar()
        binding.contentScroll.isVisible = !enabled && !isInAppMiniPlayerVisible
        (binding.playerContainer.layoutParams as LinearLayout.LayoutParams).apply {
            height = if (enabled) 0 else dpToPx(NORMAL_PLAYER_HEIGHT_DP)
            weight = if (enabled) 1f else 0f
            binding.playerContainer.layoutParams = this
        }
    }

    private fun updateFullScreenTitleBar() {
        binding.fullScreenTitleBar.isVisible = FullScreenControlVisibility.shouldShowTitleBar(
            isFullScreen = isFullScreen,
            controllerVisibility = controllerVisibility,
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (overlayHandoffPending || playbackController.hasOverlayOwner) return
        if (!ScreenOffPlaybackObserver.canPlay(this)) return
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
        binding.fullScreenTitleBar.isVisible = !isInPictureInPictureMode &&
            FullScreenControlVisibility.shouldShowTitleBar(isFullScreen, controllerVisibility)
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
        overlayHandoffPending = true
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
            if (playbackController.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play,
        )
        binding.inAppMiniPlayPauseButton.contentDescription = if (playbackController.playWhenReady) "暂停播放" else "继续播放"
    }

    private fun syncFromActivePlaybackSession() {
        val session = playbackController.activeSession ?: return
        val sessionDetail = session.detail
        if (sessionDetail.mediaKey != intent.getStringExtra(EXTRA_MEDIA_KEY)) return
        if (detail?.mediaKey != sessionDetail.mediaKey) {
            detail = sessionDetail
            renderDetail(sessionDetail)
        }
        selectedEpisode = session.episode
        playingEpisode = session.episode
        episodeAdapter.submitList(sessionDetail.episodes, session.episode)
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
        unregisterPositionListener()
        removeStateListener?.invoke()
        removeStateListener = null
        window.attributes = window.attributes.apply { screenBrightness = -1f }
        super.onStop()
        if (!isInPictureInPictureMode && isInAppMiniPlayerVisible && !isChangingConfigurations) {
            restoreMiniPlayerOnStart = true
            isInAppMiniPlayerVisible = false
            binding.inAppMiniPlayer.isVisible = false
        }
        if (playbackController.requestedMediaKey == intent.getStringExtra(EXTRA_MEDIA_KEY) &&
            (!ScreenOffPlaybackObserver.canPlay(this) ||
            (!overlayHandoffPending && !playbackController.hasOverlayOwner && !isChangingConfigurations)
            )
        ) playbackController.pause()
        playbackController.saveHistory()
    }

    override fun onDestroy() {
        playbackJob?.cancel()
        detailJob?.cancel()
        unregisterPositionListener()
        removeStateListener?.invoke()
        binding.root.removeCallbacks(gestureFeedbackHideRunnable)
        binding.playerView.player = null
        binding.inAppMiniPlayerView.player = null
        if (isFinishing && !playbackController.hasOverlayOwner &&
            playbackController.requestedMediaKey == intent.getStringExtra(EXTRA_MEDIA_KEY)
        ) {
            playbackController.saveHistory()
            playbackController.release()
        }
        super.onDestroy()
    }

    private fun registerPositionListener() {
        if (removePositionListener == null) {
            removePositionListener = playbackController.addPositionListener(::handlePlaybackPosition)
        }
    }

    private fun unregisterPositionListener() {
        removePositionListener?.invoke()
        removePositionListener = null
    }

    private fun closePlayback() {
        playbackController.saveHistory()
        playbackController.release()
        finish()
    }

    private fun showGestureFeedback(text: String) {
        binding.playerGestureFeedback.text = text
        binding.playerGestureFeedback.isVisible = true
        binding.root.removeCallbacks(gestureFeedbackHideRunnable)
        binding.root.postDelayed(gestureFeedbackHideRunnable, GESTURE_FEEDBACK_HIDE_DELAY_MS)
    }

    private fun formatPosition(positionMs: Long): String {
        val seconds = positionMs.coerceAtLeast(0L) / 1_000L
        return "%02d:%02d".format(seconds / 60L, seconds % 60L)
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

    private inner class PlayerGestureTouchListener : View.OnTouchListener {
        private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
        private var downX = 0f
        private var downY = 0f
        private var downAtMs = 0L
        private var startBrightness = 0
        private var startVolume = 0
        private var startPositionMs = 0L
        private var previewPositionMs = 0L
        private var isVerticalGesture = false
        private var isSeekGesture = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downAtMs = SystemClock.elapsedRealtime()
                    startBrightness = PlayerGesturePolicy.brightnessPercent(
                        window.attributes.screenBrightness,
                        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, DEFAULT_SYSTEM_BRIGHTNESS),
                    )
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    startPositionMs = playbackController.currentPositionMs
                    previewPositionMs = startPositionMs
                    isVerticalGesture = false
                    isSeekGesture = false
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY
                    val movedDistance = maxOf(abs(deltaX), abs(deltaY))
                    val touchSlop = ViewConfiguration.get(this@VideoPlayerActivity).scaledTouchSlop
                    if (!isVerticalGesture && !isSeekGesture && PlayerGesturePolicy.isSeekLongPress(
                            elapsedMs = SystemClock.elapsedRealtime() - downAtMs,
                            movedPx = movedDistance,
                            touchSlopPx = touchSlop,
                        )
                    ) {
                        isSeekGesture = true
                    }
                    if (isSeekGesture) {
                        val durationMs = playbackController.durationMs
                        previewPositionMs = PlayerGesturePolicy.seekPreview(startPositionMs, deltaX, view.width, durationMs)
                        showGestureFeedback(getString(R.string.player_gesture_seek, formatPosition(previewPositionMs)))
                        return true
                    }
                    if (abs(deltaY) <= touchSlop || abs(deltaY) < abs(deltaX)) return isVerticalGesture

                    isVerticalGesture = true
                    when (PlayerGesturePolicy.kindFor(downX, view.width)) {
                        PlayerGestureKind.BRIGHTNESS -> updateWindowBrightness(deltaY, view.height)
                        PlayerGestureKind.VOLUME -> updateMediaVolume(deltaY, view.height)
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSeekGesture && event.actionMasked == MotionEvent.ACTION_UP) {
                        playbackController.seekTo(previewPositionMs)
                    }
                    val handled = isVerticalGesture || isSeekGesture
                    if (handled) binding.root.postDelayed(gestureFeedbackHideRunnable, GESTURE_FEEDBACK_HIDE_DELAY_MS)
                    return handled
                }
            }
            return false
        }

        private fun updateWindowBrightness(deltaY: Float, height: Int) {
            val brightness = PlayerGesturePolicy.adjustVertical(startBrightness, deltaY, height, 1, 100)
            playerBrightness = brightness / 100f
            window.attributes = window.attributes.apply { screenBrightness = playerBrightness }
            showGestureFeedback(getString(R.string.player_gesture_brightness, brightness))
        }

        private fun updateMediaVolume(deltaY: Float, height: Int) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val volume = PlayerGesturePolicy.adjustVertical(startVolume, deltaY, height, 0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            showGestureFeedback(getString(R.string.player_gesture_volume, volume * 100 / maxVolume.coerceAtLeast(1)))
        }
    }

    companion object {
        private const val EXTRA_MEDIA_KEY = "mediaKey"
        private const val NORMAL_PLAYER_HEIGHT_DP = 211
        private const val IN_APP_MINI_MIN_WIDTH_DP = 180
        private const val IN_APP_MINI_MARGIN_DP = 16
        private const val DEFAULT_SYSTEM_BRIGHTNESS = 128
        private const val GESTURE_FEEDBACK_HIDE_DELAY_MS = 1_500L
        fun intent(context: Context, mediaKey: String): Intent =
            Intent(context, VideoPlayerActivity::class.java).putExtra(EXTRA_MEDIA_KEY, mediaKey)
    }
}
