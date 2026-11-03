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

    private fun withPlayback(block: (ActivityScenario<VideoPlayerActivity>, VideoPlaybackController) -> Unit) {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        val preferences = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
        val originalHistory = preferences.getString("watch_history", null)
        val clip = File(context.cacheDir, "player-experience-test.mp4")
        instrumentation.context.assets.open("playback-test.mp4").use { input -> clip.outputStream().use(input::copyTo) }
        val first = Episode("qa-first", "1", 1, clip.toURI().toString())
        val second = first.copy(episodeKey = "qa-second", episodeTitle = "2", uniqueId = 2)
        val detail = VideoDetail("qa-experience", "播放器验证", "", 2, episodes = listOf(first, second))
        lateinit var controller: VideoPlaybackController
        mainValue {
            AppGraph.videoPlaybackController.release()
            controller = AppGraph.videoPlaybackController
            controller.openVideo(detail.mediaKey)
            controller.prepare(detail, first, shouldPlay = false)
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
