package com.aiyifan.app.feature.video

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlayerExperienceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val automation get() = instrumentation.uiAutomation

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    fun systemPipContainsOnlyVideoAndReturnsToDetail() = withPlayback { scenario, controller ->
        org.junit.Assume.assumeTrue(context.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE,
        ))
        lateinit var activity: VideoPlayerActivity
        scenario.onActivity {
            activity = it
            val entered = VideoPlayerActivity::class.java.getDeclaredMethod("enterSystemPictureInPicture")
                .apply { isAccessible = true }.invoke(it) as Boolean
            assertTrue(entered)
        }
        await { activity.isInPictureInPictureMode }
        instrumentation.waitForIdleSync()
        mainValue {
            val root = activity.findViewById<View>(R.id.root)
            val player = activity.findViewById<View>(R.id.playerContainer)
            assertEquals(root.height, player.height)
            assertEquals(root.width, player.width)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.contentScroll).visibility)
        }
        screenshot("system-pip.png")
        mainValue {
            activity.startActivity(VideoPlayerActivity.intent(activity, controller.activeSession!!.detail.mediaKey)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        await { !activity.isInPictureInPictureMode }
        instrumentation.waitForIdleSync()
        scenario.onActivity {
            assertEquals(View.VISIBLE, it.findViewById<View>(R.id.contentScroll).visibility)
            assertEquals(View.VISIBLE, it.findViewById<View>(R.id.playerTopBar).visibility)
            assertFalse(controller.isReleased)
        }
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun pipLayoutFillsWindowAndRestoresAfterRepeatedCallbacks() = withPlayback { scenario, controller ->
        var originalHeight = 0
        var originalBottomPadding = 0
        val session = mainValue { controller.activeSession }
        scenario.onActivity { activity ->
            originalHeight = activity.findViewById<View>(R.id.playerContainer).layoutParams.height
            originalBottomPadding = activity.findViewById<View>(R.id.pageContent).paddingBottom
            repeat(2) {
                VideoPlayerActivity::class.java.getDeclaredMethod("preparePictureInPictureLayout")
                    .apply { isAccessible = true }.invoke(activity)
            }
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            val page = activity.findViewById<View>(R.id.pageContent)
            val container = activity.findViewById<View>(R.id.playerContainer)
            val player = activity.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView)
            assertEquals(page.height, container.height)
            assertEquals(page.width, container.width)
            assertEquals(0, container.top)
            assertEquals(0, page.paddingBottom)
            assertEquals(container.height, player.height)
            assertFalse(player.useController)
            for (id in listOf(R.id.contentScroll, R.id.playerTopBar, R.id.fullScreenTitleBar, R.id.inAppMiniPlayer)) {
                assertEquals(View.GONE, activity.findViewById<View>(id).visibility)
            }
            repeat(2) {
                VideoPlayerActivity::class.java.getDeclaredMethod("restorePictureInPictureLayout")
                    .apply { isAccessible = true }.invoke(activity)
            }
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            assertEquals(originalHeight, activity.findViewById<View>(R.id.playerContainer).layoutParams.height)
            assertEquals(originalBottomPadding, activity.findViewById<View>(R.id.pageContent).paddingBottom)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.contentScroll).visibility)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.playerTopBar).visibility)
            assertTrue(activity.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView).useController)
            assertSame(session, controller.activeSession)
        }
    }

    @Test
    fun episodesExpandDownwardAndKeepSelectionWithoutScrollingTheGrid() =
        withPlayback(episodeCount = 30, startIndex = 24) { scenario, controller ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.episodeRecycler)
                val layout = list.layoutManager as androidx.recyclerview.widget.GridLayoutManager
                assertEquals(4, layout.spanCount)
                assertEquals(RecyclerView.VERTICAL, layout.orientation)
                assertFalse(list.isNestedScrollingEnabled)
                assertEquals(0, layout.findFirstVisibleItemPosition())
                assertEquals("All episodes must be bound", 30, list.adapter!!.itemCount)
                assertNotNull("Last episode must be laid out: height=${list.height}, children=${list.childCount}", list.findViewHolderForAdapterPosition(29))
                assertTrue(list.findViewHolderForAdapterPosition(24)!!.itemView.isSelected)
                assertTrue(list.findViewHolderForAdapterPosition(4)!!.itemView.top >
                    list.findViewHolderForAdapterPosition(0)!!.itemView.top)
                controller.pause()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.episodeRecycler)
                assertEquals(0, (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
                assertTrue(list.findViewHolderForAdapterPosition(24)!!.itemView.isSelected)
            }
        }
    @Test
    fun playbackEndAdvancesOnPageAndStopsAtFinalEpisode() = withPlayback { scenario, controller ->
        scenario.onActivity { activity ->
            assertNotNull(activity.findViewById<ImageButton>(R.id.playerPreviousEpisodeButton))
            assertNotNull(activity.findViewById<ImageButton>(R.id.playerNextEpisodeButton))
            controller.seekTo(controller.durationMs)
            controller.togglePlayPause()
        }
        await { controller.activeSession?.episode?.episodeKey == "qa-second" && controller.canSeek }
        scenario.onActivity { activity ->
            assertEquals("播放器验证 · 第 2 集", activity.findViewById<TextView>(R.id.videoTitle).text.toString())
            assertTrue(controller.playWhenReady)
            controller.seekTo(controller.durationMs)
        }
        await { controller.isPlaybackEnded }
        instrumentation.waitForIdleSync()
        assertEquals("qa-second", mainValue { controller.activeSession?.episode?.episodeKey })
        assertFalse(mainValue { controller.isLoading })
    }

    @Test
    fun overlayEndAdvancesWithoutEpisodeNavigationButtons() = withPlayback { scenario, controller ->
        val previousMode = shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        val restoreMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
            .find(previousMode)?.groupValues?.get(1) ?: "default"
        val originalFlags = automation.serviceInfo.flags
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            scenario.onActivity { activity ->
                activity.findViewById<ImageButton>(R.id.floatingWindowButton).performClick()
            }
            await { controller.hasOverlayOwner }
            waitForNode("floatingPlayPauseButton")
            assertNull(node("floatingPreviousEpisodeButton"))
            assertNull(node("floatingNextEpisodeButton"))
            screenshot("floating-player-without-episode-buttons.png")
            mainValue {
                controller.seekTo(controller.durationMs)
                controller.togglePlayPause()
            }
            await { controller.activeSession?.episode?.episodeKey == "qa-second" && controller.canSeek }
            assertTrue(mainValue { controller.hasOverlayOwner && controller.playWhenReady })
            mainValue { controller.seekTo(controller.durationMs) }
            await { controller.isPlaybackEnded }
            assertEquals("qa-second", mainValue { controller.activeSession?.episode?.episodeKey })
        } finally {
            context.stopService(FloatingPlayerService.intent(context))
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $restoreMode")
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    @Test
    fun titlesSelectionAndMiniPlayerControlsRetainPausedPlayback() = withPlayback { scenario, controller ->
        scenario.onActivity { activity ->
            assertEquals("播放器验证 · 第 1 集", activity.findViewById<TextView>(R.id.videoTitle).text.toString())
            assertEquals("播放器验证", activity.findViewById<TextView>(R.id.title).text.toString())
            val recycler = activity.findViewById<RecyclerView>(R.id.episodeRecycler)
            recycler.findViewHolderForAdapterPosition(1)!!.itemView.performClick()
            assertEquals("qa-second", controller.pendingEpisode?.episodeKey ?: controller.activeSession?.episode?.episodeKey)
        }
        await { controller.activeSession?.episode?.episodeKey == "qa-second" && controller.canSeek }
        scenario.onActivity { activity ->
            assertEquals("播放器验证 · 第 2 集", activity.findViewById<TextView>(R.id.fullScreenVideoTitle).text.toString())
            assertTrue(activity.findViewById<RecyclerView>(R.id.episodeRecycler)
                .findViewHolderForAdapterPosition(1)!!.itemView.isSelected)
            controller.pause()
            controller.seekTo(2_000L)
            VideoPlayerActivity::class.java.getDeclaredMethod("showInAppMiniPlayer").apply { isAccessible = true }.invoke(activity)
        }
        instrumentation.waitForIdleSync()
        await { controller.canSeek && controller.currentPositionMs == 2_000L }
        scenario.onActivity { activity ->
            activity.findViewById<ImageButton>(R.id.inAppMiniForwardButton).performClick()
        }
        await { controller.canSeek && controller.currentPositionMs == 12_000L }
        scenario.onActivity { activity ->
            activity.findViewById<ImageButton>(R.id.inAppMiniRewindButton).performClick()
        }
        await { controller.canSeek && controller.currentPositionMs == 2_000L }
        scenario.onActivity { activity ->
            assertFalse(controller.playWhenReady)
            val mini = activity.findViewById<View>(R.id.inAppMiniPlayer)
            val oldWidth = mini.width
            val left = mini.left
            val top = mini.top
            val handle = activity.findViewById<View>(R.id.inAppMiniResizeHandle)
            dragLocally(handle, -90f)
            assertTrue(mini.layoutParams.width < oldWidth)
            assertEquals(left, (mini.layoutParams as android.widget.FrameLayout.LayoutParams).leftMargin)
            assertEquals(top, (mini.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin)
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            val mini = activity.findViewById<View>(R.id.inAppMiniPlayer)
            val oldWidth = mini.width
            pinchLocally(mini, 1.3f)
            assertTrue("Mini pinch must grow $oldWidth to ${mini.layoutParams.width}", mini.layoutParams.width > oldWidth)
            assertEquals(mini.layoutParams.width * 9f / 16f, mini.layoutParams.height.toFloat(), 1f)
            assertFalse(controller.playWhenReady)
        }
        screenshot("mini-player.png")
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @androidx.media3.common.util.UnstableApi
    fun floatingWindowUsesHardwareTextureWithDecodedVideo() = withPlayback { scenario, controller ->
        val previousMode = shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        val restoreMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
            .find(previousMode)?.groupValues?.get(1) ?: "default"
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            scenario.onActivity { activity ->
                activity.findViewById<ImageButton>(R.id.floatingWindowButton).performClick()
            }
            await { controller.hasOverlayOwner }
            var texture: android.view.TextureView? = null
            await {
                val root = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .firstOrNull { it.findViewById<View>(R.id.floatingPlayerView) != null }
                if (root == null) false else {
                    assertTrue("Service window must be hardware accelerated", root.isHardwareAccelerated)
                    val player = root.findViewById<androidx.media3.ui.PlayerView>(R.id.floatingPlayerView)
                    assertTrue(player.videoSurfaceView is android.view.TextureView)
                    texture = player.videoSurfaceView as android.view.TextureView
                    texture.isAvailable
                }
            }
            mainValue {
                controller.seekTo(2_000L)
                controller.togglePlayPause()
            }
            await {
                val bitmap = texture!!.bitmap
                if (bitmap == null) false else {
                    try {
                        // The local test clip has coloured pixels; an unrendered texture stays black.
                        (1..3).any { y -> (1..3).any { x ->
                            bitmap.getPixel(bitmap.width * x / 4, bitmap.height * y / 4) and 0x00ffffff != 0
                        } }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            screenshot("floating-texture-video.png")
        } finally {
            context.stopService(FloatingPlayerService.intent(context))
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $restoreMode")
        }
    }

    @Test
    fun overlayControlsResizeHideAndRestoreWithoutLosingSession() = withPlayback { scenario, controller ->
        val previousMode = shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        val restoreMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
            .find(previousMode)?.groupValues?.get(1) ?: "default"
        val originalFlags = automation.serviceInfo.flags
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            scenario.onActivity { activity ->
                controller.pause()
                controller.seekTo(2_000L)
                activity.findViewById<ImageButton>(R.id.floatingWindowButton).performClick()
            }
            await { controller.hasOverlayOwner }
            await { controller.canSeek && controller.currentPositionMs == 2_000L }
            waitForNode("floatingForwardButton").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { controller.canSeek && controller.currentPositionMs == 12_000L }
            waitForNode("floatingRewindButton").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { controller.canSeek && controller.currentPositionMs == 2_000L }
            val before = bounds("floatingPlayerView")
            val handle = bounds("floatingResizeHandle")
            shell("input swipe ${handle.centerX()} ${handle.centerY()} ${handle.centerX() - 160} ${handle.centerY()} 500")
            val smaller = bounds("floatingPlayerView")
            assertTrue(smaller.width() < before.width())
            assertEquals(before.left, smaller.left)
            assertEquals(before.top, smaller.top)
            pinchOnScreen(smaller, 1.2f)
            val larger = bounds("floatingPlayerView")
            assertTrue("Overlay pinch must grow ${smaller.width()} to ${larger.width()}", larger.width() > smaller.width())
            assertEquals(larger.width() * 9f / 16f, larger.height().toFloat(), 2f)
            assertFalse(mainValue { controller.playWhenReady })
            screenshot("floating-player.png")
            SystemClock.sleep(2_800L)
            assertEquals(null, node("floatingForwardButton"))
            val x = larger.centerX()
            val y = larger.top + larger.height() / 3
            shell("input tap $x $y")
            waitForNode("floatingForwardButton")
            shell("input tap $x $y")
            shell("input tap $x $y")
            await { !controller.hasOverlayOwner }
            scenario.onActivity { activity ->
                assertEquals("播放器验证 · 第 1 集", activity.findViewById<TextView>(R.id.videoTitle).text.toString())
                assertEquals(2_000L, controller.currentPositionMs)
                assertFalse(controller.playWhenReady)
            }
        } finally {
            context.stopService(FloatingPlayerService.intent(context))
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $restoreMode")
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    @Test
    @androidx.media3.common.util.UnstableApi
    fun sleepTimerDialogSharesCountdownAcrossPortraitFullscreenAndMini() = withPlayback { scenario, controller ->
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        try {
            scenario.onActivity { activity ->
                activity.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView).showController()
                activity.findViewById<View>(R.id.playerSleepTimerButton).performClick()
            }
            waitForText("15 分钟后").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { controller.sleepTimerState.option == SleepTimerOption.MINUTES_15 }
            screenshot("sleep-timer-portrait.png")
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.fullScreenButton).performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                activity.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView).showController()
                assertTrue(activity.findViewById<TextView>(R.id.playerSleepTimerButton).text.startsWith("定时 "))
                activity.findViewById<View>(R.id.playerSleepTimerButton).performClick()
            }
            assertTrue(waitForText("15 分钟后").isChecked)
            screenshot("sleep-timer-fullscreen-dialog.png")
            waitForText("返回").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.fullScreenBackButton).performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                VideoPlayerActivity::class.java.getDeclaredMethod("showInAppMiniPlayer").apply { isAccessible = true }.invoke(activity)
                assertTrue(activity.findViewById<TextView>(R.id.inAppMiniSleepTimerButton).text.startsWith("定时 "))
                activity.findViewById<View>(R.id.inAppMiniSleepTimerButton).performClick()
            }
            waitForText("关闭定时").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { controller.sleepTimerState.option == SleepTimerOption.OFF }
            assertFalse(mainValue { controller.playWhenReady })
            screenshot("sleep-timer-mini.png")
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    @Test
    fun overlaySleepTimerDialogStopsAtEpisodeEndWithoutAdvancing() = withPlayback { scenario, controller ->
        val previousMode = shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        val restoreMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
            .find(previousMode)?.groupValues?.get(1) ?: "default"
        val originalFlags = automation.serviceInfo.flags
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            scenario.onActivity { activity ->
                controller.setSleepTimer(SleepTimerOption.MINUTES_30)
                activity.findViewById<View>(R.id.floatingWindowButton).performClick()
            }
            await { controller.hasOverlayOwner }
            waitForNode("floatingSleepTimerButton").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            assertTrue(waitForText("30 分钟后").isChecked)
            screenshot("sleep-timer-overlay-dialog.png")
            waitForText("播完当前集").performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await { controller.sleepTimerState.option == SleepTimerOption.END_OF_EPISODE }
            mainValue {
                controller.seekTo((controller.durationMs - 500L).coerceAtLeast(0L))
                controller.togglePlayPause()
            }
            await { controller.sleepTimerState.option == SleepTimerOption.OFF && !controller.playWhenReady }
            assertEquals("qa-first", mainValue { controller.activeSession?.episode?.episodeKey })
            assertTrue(mainValue { controller.hasOverlayOwner })
            screenshot("sleep-timer-overlay-ended.png")
        } finally {
            context.stopService(FloatingPlayerService.intent(context))
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $restoreMode")
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    private fun waitForText(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 8_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            automation.windows.asSequence().mapNotNull { it.root }
                .flatMap { it.findAccessibilityNodeInfosByText(text).asSequence() }
                .firstOrNull { it.isVisibleToUser && it.text?.toString() == text }?.let { return it }
            SystemClock.sleep(100L)
        }
        error("Missing visible text: $text")
    }

    private fun withPlayback(
        episodeCount: Int = 2,
        startIndex: Int = 0,
        block: (ActivityScenario<VideoPlayerActivity>, VideoPlaybackController) -> Unit,
    ) {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        val preferences = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
        val originalHistory = preferences.getString("watch_history", null)
        val clip = File(context.cacheDir, "player-experience-test.mp4")
        instrumentation.context.assets.open("playback-test.mp4").use { input -> clip.outputStream().use(input::copyTo) }
        val first = Episode("qa-first", "1", 1, clip.toURI().toString())
        val second = first.copy(episodeKey = "qa-second", episodeTitle = "2", uniqueId = 2)
        val episodes = listOf(first, second) + (3..episodeCount).map { index ->
            first.copy(episodeKey = "qa-episode-$index", episodeTitle = "$index", uniqueId = index)
        }
        val detail = VideoDetail("qa-experience", "播放器验证", "", 2, episodes = episodes)
        lateinit var controller: VideoPlaybackController
        mainValue {
            AppGraph.videoPlaybackController.release()
            controller = AppGraph.videoPlaybackController
            controller.openVideo(detail.mediaKey)
            controller.prepare(detail, episodes[startIndex], shouldPlay = false)
        }
        try {
            ActivityScenario.launch<VideoPlayerActivity>(VideoPlayerActivity.intent(context, detail.mediaKey)).use { scenario ->
                await { controller.canSeek }
                instrumentation.waitForIdleSync()
                block(scenario, controller)
            }
        } finally {
            mainValue { controller.release() }
            preferences.edit().putString("watch_history", originalHistory).commit()
            clip.delete()
        }
    }

    private fun dragLocally(view: View, deltaX: Float) {
        val start = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            MotionEvent.obtain(start, start + index * 30L, action, 12f + if (index == 0) 0f else deltaX, 12f, 0)
                .also { view.dispatchTouchEvent(it); it.recycle() }
        }
    }

    private fun pinchLocally(view: View, factor: Float) = pinch(view.left + view.width / 2f, view.top + view.height / 4f, view.width * 0.4f, factor) {
        // Dispatch through the real parent, including its ACTION_DOWN target selection.
        (view.parent as View).dispatchTouchEvent(it)
    }

    private fun pinchOnScreen(bounds: Rect, factor: Float) = pinch(bounds.exactCenterX(), bounds.top + bounds.height() / 4f, bounds.width() * 0.4f, factor) {
        automation.injectInputEvent(it, true)
    }

    private fun pinch(x: Float, y: Float, radius: Float, factor: Float, dispatch: (MotionEvent) -> Unit) {
        val start = SystemClock.uptimeMillis()
        val properties = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun send(action: Int, count: Int, scale: Float, step: Int) {
            val points = Array(count) { i -> MotionEvent.PointerCoords().apply {
                this.x = x + if (i == 0) -radius * scale else radius * scale
                this.y = y
                pressure = 1f
                size = 1f
            } }
            MotionEvent.obtain(start, start + step * 30L, action, count, properties, points, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                .also { dispatch(it); it.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 1, 1f, 0)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 1f, 1)
        for (step in 1..8) send(MotionEvent.ACTION_MOVE, 2, 1f + (factor - 1f) * step / 8f, step + 1)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, factor, 10)
        send(MotionEvent.ACTION_UP, 1, factor, 11)
    }

    private fun node(id: String): AccessibilityNodeInfo? = automation.windows.asSequence()
        .mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByViewId("${context.packageName}:id/$id").asSequence() }
        .firstOrNull { it.isVisibleToUser }

    private fun waitForNode(id: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 8_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            node(id)?.let { return it }
            SystemClock.sleep(100L)
        }
        error("Missing visible control: $id")
    }

    private fun bounds(id: String) = Rect().also { waitForNode(id).getBoundsInScreen(it) }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500L)
        instrumentation.waitForIdleSync()
        val bitmap = automation.takeScreenshot() ?: error("Screenshot unavailable")
        val file = File(context.getExternalFilesDir(null), name)
        file.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        // Gradle may uninstall the test target after the run; retain QA images outside its sandbox.
        shell("mkdir -p /data/local/tmp/aiyifan-player-qa")
        shell("cp ${file.absolutePath} /data/local/tmp/aiyifan-player-qa/$name")
    }

    private fun shell(command: String) = automation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun <T> mainValue(block: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (!mainValue(condition)) {
            check(SystemClock.elapsedRealtime() < deadline) { "Player condition timed out" }
            SystemClock.sleep(100L)
        }
    }
}
