package com.aiyifan.app.feature.history

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.feature.collection.CollectionActivity
import com.aiyifan.app.feature.home.ContinueWatchingStore
import com.aiyifan.app.feature.main.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ViewingExperienceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository get() = AppGraph.catalogRepository
    private val keys = setOf("qa-viewing-one", "qa-viewing-two")
    private val first = VideoDetail("qa-viewing-one", "明日旅途 · 体验验证", "", 1,
        episodes = listOf(Episode("qa-episode-one", "第 3 集", 3, null)))
    private val second = VideoDetail("qa-viewing-two", "山海之间 · 体验验证", "", 1,
        episodes = listOf(Episode("qa-episode-two", "第 8 集", 8, null)))

    @Test fun historySelectionAndHomeRefreshWorkInBothThemes() {
        val previousMode = AppCompatDelegate.getDefaultNightMode()
        try {
            main { seed() }
            for ((mode, suffix) in listOf(AppCompatDelegate.MODE_NIGHT_NO to "light", AppCompatDelegate.MODE_NIGHT_YES to "dark")) {
                main { AppCompatDelegate.setDefaultNightMode(mode) }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    await {
                        var visible = false
                        scenario.onActivity { visible = it.findViewById<RecyclerView>(R.id.videoRecycler)
                            .findViewHolderForAdapterPosition(0)?.itemView?.findViewById<RecyclerView>(R.id.records) != null }
                        visible
                    }
                    screenshot("continue-watching-$suffix")
                    var feedAdapter: RecyclerView.Adapter<*>? = null
                    scenario.onActivity { activity ->
                        feedAdapter = (activity.findViewById<RecyclerView>(R.id.videoRecycler).adapter as ConcatAdapter).adapters.last()
                        repository.saveHistory(first, first.episodes.first(), 1_500_000, 2_700_000)
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertSame(feedAdapter, (activity.findViewById<RecyclerView>(R.id.videoRecycler).adapter as ConcatAdapter).adapters.last())
                    }
                }
                ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
                    await {
                        var ready = false
                        scenario.onActivity { ready = it.findViewById<RecyclerView>(R.id.recycler).findViewHolderForAdapterPosition(0) != null }
                        ready
                    }
                    screenshot("watch-history-$suffix")
                    scenario.onActivity { activity ->
                        val holder = activity.findViewById<RecyclerView>(R.id.recycler).findViewHolderForAdapterPosition(0)!!
                        assertTrue(holder.itemView.findViewById<TextView>(R.id.time).text.contains(" / "))
                        holder.itemView.performLongClick()
                        assertEquals(context.getString(R.string.history_selected_count, 1), activity.findViewById<TextView>(R.id.title).text.toString())
                        activity.onBackPressedDispatcher.onBackPressed()
                        assertEquals(context.getString(R.string.hardcoded_text_039), activity.findViewById<TextView>(R.id.title).text.toString())
                    }
                }
                ActivityScenario.launch(CollectionActivity::class.java).use { scenario ->
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertTrue(activity.findViewById<RecyclerView>(R.id.recycler).adapter!!.itemCount >= 2)
                    }
                    screenshot("collection-$suffix")
                }
            }
            val prefs = context.getSharedPreferences("qa_continue_watching", Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            val record = repository.getHistory().first { it.mediaKey == first.mediaKey }
            ContinueWatchingStore(prefs).hide(record)
            assertTrue(ContinueWatchingStore(prefs).visible(listOf(record)).isEmpty())
            assertEquals(1, ContinueWatchingStore(prefs).visible(listOf(record.copy(updatedAt = record.updatedAt + 1))).size)
            prefs.edit().clear().commit()
        } finally {
            main {
                repository.removeHistory(keys)
                keys.forEach(repository::removeFavorite)
                AppCompatDelegate.setDefaultNightMode(previousMode)
            }
        }
    }

    @Test fun deferredHomePosterDoesNotLoadAfterActivityDestruction() {
        lateinit var oldActivity: MainActivity
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { oldActivity = it }
        scenario.close()
        main {
            val image = object : android.widget.ImageView(oldActivity) {
                override fun post(action: Runnable): Boolean { action.run(); return true }
            }
            val adapterClass = com.aiyifan.app.feature.home.HomeVideoAdapter::class.java
            val companion = adapterClass.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
            val bind = companion.javaClass.getDeclaredMethod("bindCardPoster", android.widget.ImageView::class.java,
                String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            bind.invoke(companion, image, "https://example.invalid/poster.jpg", true)
        }
    }

    /** Run with -e persistencePhase seed, force-stop the app, then -e persistencePhase verify. */
    @Test fun personalDataSurvivesProcessRestart() {
        val phase = InstrumentationRegistry.getArguments().getString("persistencePhase") ?: return
        val marker = context.getSharedPreferences("qa_restart_marker", Context.MODE_PRIVATE)
        if (phase == "seed") {
            main { seed() }
            context.getSharedPreferences("watch_history", Context.MODE_PRIVATE).edit().commit()
            context.getSharedPreferences("favorites", Context.MODE_PRIVATE).edit().commit()
            marker.edit().putInt("pid", android.os.Process.myPid()).commit()
        } else {
            try {
                assertNotEquals(marker.getInt("pid", -1), android.os.Process.myPid())
                assertTrue(repository.isFavorite(first.mediaKey))
                assertEquals("第 3 集", repository.getFavoriteTracking(first.mediaKey)?.latestEpisodeTitle)
                assertEquals(1_395_000L, repository.getHistory().first { it.mediaKey == first.mediaKey }.progressMs)
                main { assertEquals(com.aiyifan.app.feature.video.SleepTimerOption.OFF, AppGraph.videoPlaybackController.sleepTimerState.option) }
            } finally {
                main { repository.removeHistory(keys); keys.forEach(repository::removeFavorite); AppGraph.videoPlaybackController.release() }
                marker.edit().clear().commit()
            }
        }
    }

    private fun seed() {
        keys.forEach(repository::removeFavorite)
        repository.saveHistory(first, first.episodes.first(), 1_395_000, 2_700_000)
        repository.saveHistory(second, second.episodes.first(), 900_000, 3_000_000)
        repository.toggleFavorite(first)
        repository.toggleFavorite(second)
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("UI did not become ready", condition())
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
        val file = File(context.getExternalFilesDir(null), "viewing-qa/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        checkNotNull(bitmap)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
