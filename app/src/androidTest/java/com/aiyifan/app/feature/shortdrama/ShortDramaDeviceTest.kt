package com.aiyifan.app.feature.shortdrama

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.graphics.drawable.BitmapDrawable
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.feature.localmedia.LocalMediaActivity
import com.aiyifan.app.feature.main.MainActivity
import com.aiyifan.app.feature.video.VideoPlayerActivity
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit live-service checks; run separately from deterministic unit tests. */
@RunWith(AndroidJUnit4::class)
class ShortDramaDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun navigationAndLocalMediaReturn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tabs = activity.findViewById<TabLayout>(R.id.bottomTabs)
                assertEquals(listOf("首页", "短剧", "我的"), (0..2).map { tabs.getTabAt(it)?.text.toString() })
                tabs.getTabAt(2)!!.select()
                activity.supportFragmentManager.executePendingTransactions()
            }
            await {
                var ordered = false
                scenario.onActivity { activity ->
                    ordered = activity.findViewById<View>(R.id.localMediaButton).top > activity.findViewById<View>(R.id.hotButton).top
                }
                ordered
            }
            screenshot("mine-local-entry")
            val monitor = instrumentation.addMonitor(LocalMediaActivity::class.java.name, null, false)
            try {
                scenario.onActivity { it.findViewById<View>(R.id.localMediaButton).performClick() }
                val local = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
                assertNotNull("Local media entry opens its activity", local)
                instrumentation.waitForIdleSync()
                screenshot("local-media")
                instrumentation.runOnMainSync {
                    local.findViewById<View>(R.id.localBackButton).performClick()
                }
                instrumentation.waitForIdleSync()
                scenario.recreate()
                scenario.onActivity {
                    assertEquals(2, it.findViewById<TabLayout>(R.id.bottomTabs).selectedTabPosition)
                    assertNotNull(it.findViewById<View>(R.id.localMediaButton))
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    @Test fun liveDramaLoadsPlaysSwitchesAndResumes() {
        val historyPrefs = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
        val historyBefore = historyPrefs.getString("watch_history", null)
        var player: Activity? = null
        var favoriteKey: String? = null
        var addedFavorite = false
        val monitor = instrumentation.addMonitor(VideoPlayerActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.findViewById<TabLayout>(R.id.bottomTabs).getTabAt(1)!!.select() }
                await {
                    var count = 0
                    scenario.onActivity { count = it.findViewById<RecyclerView>(R.id.dramaRecycler)?.adapter?.itemCount ?: 0 }
                    count >= 30
                }
                await {
                    var loaded = false
                    scenario.onActivity {
                        loaded = it.findViewById<RecyclerView>(R.id.dramaRecycler)
                            .findViewHolderForAdapterPosition(0)?.itemView
                            ?.findViewById<ImageView>(R.id.cardPoster)?.drawable is BitmapDrawable
                    }
                    loaded
                }
                screenshot("short-drama-list")
                scenario.onActivity {
                    it.findViewById<RecyclerView>(R.id.dramaRecycler).findViewHolderForAdapterPosition(0)!!.itemView.performClick()
                }
                player = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
                assertNotNull(player)
                await { mainValue { AppGraph.videoPlaybackController.isPlaying } }
                val detail = mainValue { AppGraph.videoPlaybackController.activeSession!!.detail }
                favoriteKey = detail.mediaKey
                if (!AppGraph.catalogRepository.isFavorite(detail.mediaKey)) {
                    instrumentation.runOnMainSync { player!!.findViewById<View>(R.id.favoriteButton).performClick() }
                    addedFavorite = true
                    assertTrue(AppGraph.catalogRepository.isFavorite(detail.mediaKey))
                }
                assertTrue(detail.episodes.size > 1)
                instrumentation.runOnMainSync {
                    player!!.findViewById<ImageButton>(R.id.playerNextEpisodeButton).performClick()
                }
                await { mainValue {
                    val controller = AppGraph.videoPlaybackController
                    controller.isPlaying && controller.activeSession?.episode?.episodeKey == detail.episodes[1].episodeKey
                } }
                instrumentation.runOnMainSync { AppGraph.videoPlaybackController.seekTo(70_000) }
                await { mainValue { AppGraph.videoPlaybackController.currentPositionMs >= 70_000 } }
                screenshot("short-drama-playback")
                instrumentation.runOnMainSync { AppGraph.videoPlaybackController.pause(); player!!.finish() }
                instrumentation.waitForIdleSync()
                val saved = AppGraph.catalogRepository.getHistory().first { it.mediaKey == detail.mediaKey }
                assertEquals(detail.episodes[1].episodeKey, saved.episodeKey)
                assertTrue(saved.progressMs >= 70_000)
                val resumed = ActivityScenario.launch<VideoPlayerActivity>(VideoPlayerActivity.intent(context, detail.mediaKey))
                resumed.onActivity { player = it }
                await { mainValue {
                    val controller = AppGraph.videoPlaybackController
                    controller.activeSession?.episode?.episodeKey == saved.episodeKey
                } }
                resumed.close()
            }
        } finally {
            instrumentation.runOnMainSync { player?.finish(); AppGraph.videoPlaybackController.release() }
            if (addedFavorite) favoriteKey?.let { AppGraph.catalogRepository.removeFavorite(it) }
            historyPrefs.edit().putString("watch_history", historyBefore).commit()
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun <T> mainValue(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 45_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(250)
        }
        fail("Condition not met within 45 seconds")
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
