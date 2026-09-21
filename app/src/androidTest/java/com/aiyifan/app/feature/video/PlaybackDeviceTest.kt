package com.aiyifan.app.feature.video

import android.content.Context
import android.content.ContentValues
import android.provider.MediaStore
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.feature.localmedia.LocalVideoPlayerActivity
import com.aiyifan.app.feature.localmedia.model.LocalVideo
import com.aiyifan.app.feature.localmedia.data.LocalPlaybackStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import java.io.File

/** Uses a small deterministic clip, without depending on the catalog or streaming network. */
@RunWith(AndroidJUnit4::class)
class PlaybackDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var historyBefore: String? = null
    private var localBefore: String? = null

    @Before fun preserveHistory() {
        historyBefore = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE).getString("watch_history", null)
        localBefore = context.getSharedPreferences("local_media_playback", Context.MODE_PRIVATE).getString("records", null)
    }

    @After fun restoreHistory() {
        instrumentation.runOnMainSync { AppGraph.videoPlaybackController.release() }
        context.getSharedPreferences("watch_history", Context.MODE_PRIVATE).edit().putString("watch_history", historyBefore).commit()
        context.getSharedPreferences("local_media_playback", Context.MODE_PRIVATE).edit().putString("records", localBefore).commit()
    }

    @Test
    fun nextBrightnessLockAndResume() {
        val clip = File(context.cacheDir, "playback-test.mp4")
        instrumentation.context.assets.open("playback-test.mp4").use { input ->
            clip.outputStream().use(input::copyTo)
        }
        val first = Episode("qa-first", "第一集", 1, clip.toURI().toString())
        val second = first.copy(episodeKey = "qa-second", episodeTitle = "第二集", uniqueId = 2)
        val detail = VideoDetail("qa-playback", "播放器验证", "", 2, episodes = listOf(first, second))
        lateinit var controller: VideoPlaybackController
        instrumentation.runOnMainSync {
            AppGraph.videoPlaybackController.release()
            controller = AppGraph.videoPlaybackController
            controller.openVideo(detail.mediaKey)
            assertTrue(controller.prepare(detail, first))
        }
        var scenario: ActivityScenario<VideoPlayerActivity>? = null
        try {
            scenario = ActivityScenario.launch(VideoPlayerActivity.intent(context, detail.mediaKey))
            await { controller.isPlaying }
            scenario.onActivity { activity ->
                val playerView = activity.findViewById<PlayerView>(R.id.playerView)
                val previous = playerView.findViewById<ImageButton>(R.id.playerPreviousEpisodeButton)
                val next = playerView.findViewById<ImageButton>(R.id.playerNextEpisodeButton)
                assertFalse(previous.isEnabled)
                assertTrue(next.isEnabled)
                next.performClick()
            }
            await { controller.activeSession?.episode?.episodeKey == second.episodeKey && controller.isPlaying }
            scenario.onActivity { activity ->
                val playerView = activity.findViewById<PlayerView>(R.id.playerView)
                assertFalse(playerView.findViewById<ImageButton>(R.id.playerNextEpisodeButton).isEnabled)
                val now = SystemClock.uptimeMillis()
                val x = playerView.width * 0.2f
                val y = playerView.height * 0.7f
                for ((action, eventTime, touchY) in listOf(
                    Triple(MotionEvent.ACTION_DOWN, now, y),
                    Triple(MotionEvent.ACTION_MOVE, now + 30, y - playerView.height * 0.3f),
                    Triple(MotionEvent.ACTION_UP, now + 60, y - playerView.height * 0.3f),
                )) {
                    MotionEvent.obtain(now, eventTime, action, x, touchY, 0).also {
                        playerView.dispatchTouchEvent(it)
                        it.recycle()
                    }
                }
                assertTrue("Window brightness must change without permission", activity.window.attributes.screenBrightness in 0.01f..1f)
                controller.seekTo(7_000L)
            }
            await { controller.currentPositionMs >= 7_000L }
            scenario.onActivity { it.findViewById<ImageButton>(R.id.fullScreenButton).performClick() }
            SystemClock.sleep(600L)
            shell("input keyevent KEYCODE_SLEEP")
            await { !controller.playWhenReady }
            val lockedPosition = mainValue { controller.currentPositionMs }
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            assertFalse(mainValue { controller.playWhenReady })
            assertTrue(kotlin.math.abs(mainValue { controller.currentPositionMs } - lockedPosition) < 500L)
            val saved = AppGraph.catalogRepository.getHistory().first { it.mediaKey == detail.mediaKey }
            assertEquals(second.episodeKey, saved.episodeKey)
            assertTrue(saved.progressMs >= 7_000L)
            scenario.onActivity { controller.togglePlayPause() }
            await { controller.isPlaying }
            scenario.onActivity { it.findViewById<ImageButton>(R.id.fullScreenBackButton).performClick() }
            shell("input keyevent KEYCODE_HOME")
            SystemClock.sleep(1_000L)
            assertTrue("Visible picture in picture keeps playing", mainValue { controller.playWhenReady })
            shell("input keyevent KEYCODE_SLEEP")
            await { !controller.playWhenReady }
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            assertFalse(mainValue { controller.playWhenReady })
            scenario.close()
            scenario = null

            // Reopening a still-active paused session must preserve the selected episode and position.
            instrumentation.runOnMainSync {
                controller = AppGraph.videoPlaybackController
                controller.openVideo(detail.mediaKey)
                val target = PlaybackResumePolicy.target(detail, saved)!!
                controller.prepare(detail, target.episode, target.positionMs, shouldPlay = false)
            }
            scenario = ActivityScenario.launch(VideoPlayerActivity.intent(context, detail.mediaKey))
            await { controller.durationMs > 0 }
            assertEquals(second.episodeKey, mainValue { controller.activeSession?.episode?.episodeKey })
            assertTrue(mainValue { controller.currentPositionMs } >= saved.progressMs)
            assertFalse(mainValue { controller.playWhenReady })
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            scenario?.close()
            instrumentation.runOnMainSync { controller.release() }
            clip.delete()
        }
    }

    @Test
    fun floatingWindowPausesOnScreenOff() {
        val clip = File(context.cacheDir, "floating-test.mp4")
        instrumentation.context.assets.open("playback-test.mp4").use { input -> clip.outputStream().use(input::copyTo) }
        val episode = Episode("qa-floating-1", "1", 1, clip.toURI().toString())
        val detail = VideoDetail("qa-floating", "悬浮窗验证", "", 2, episodes = listOf(episode))
        val previousMode = shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        val restoreMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)").find(previousMode)?.groupValues?.get(1) ?: "default"
        lateinit var controller: VideoPlaybackController
        var scenario: ActivityScenario<VideoPlayerActivity>? = null
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            instrumentation.runOnMainSync {
                AppGraph.videoPlaybackController.release()
                controller = AppGraph.videoPlaybackController
                controller.openVideo(detail.mediaKey)
                controller.prepare(detail, episode)
            }
            scenario = ActivityScenario.launch(VideoPlayerActivity.intent(context, detail.mediaKey))
            await { controller.isPlaying }
            scenario.onActivity { it.findViewById<ImageButton>(R.id.floatingWindowButton).performClick() }
            await { controller.hasOverlayOwner }
            assertTrue(mainValue { controller.playWhenReady })
            shell("input keyevent KEYCODE_SLEEP")
            await { !controller.playWhenReady }
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            assertFalse(mainValue { controller.playWhenReady })
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            context.stopService(FloatingPlayerService.intent(context))
            scenario?.close()
            instrumentation.runOnMainSync { AppGraph.videoPlaybackController.release() }
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $restoreMode")
            clip.delete()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun localVideoPausesAndRetainsPosition() {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "aiyifan-qa-playback.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
        var scenario: ActivityScenario<LocalVideoPlayerActivity>? = null
        try {
            instrumentation.context.assets.open("playback-test.mp4").use { input ->
                context.contentResolver.openOutputStream(uri)!!.use(input::copyTo)
            }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            val video = LocalVideo(uri.lastPathSegment!!.toLong(), uri.toString(), "播放器验证", 20_000L, 0L, 0L, null)
            scenario = ActivityScenario.launch(LocalVideoPlayerActivity.intent(context, video))
            lateinit var player: androidx.media3.common.Player
            scenario.onActivity { player = it.findViewById<PlayerView>(R.id.localPlayerView).player!! }
            await { player.isPlaying }
            instrumentation.runOnMainSync { player.seekTo(6_000L) }
            await { player.currentPosition >= 6_000L }
            shell("input keyevent KEYCODE_SLEEP")
            await { !player.playWhenReady }
            val record = LocalPlaybackStore(context).load().first { it.mediaStoreId == video.id }
            assertTrue(record.positionMs >= 6_000L)
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            assertFalse(mainValue { player.playWhenReady })
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            scenario?.close()
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
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
            check(SystemClock.elapsedRealtime() < deadline) { "Playback condition timed out" }
            SystemClock.sleep(100L)
        }
    }
}
