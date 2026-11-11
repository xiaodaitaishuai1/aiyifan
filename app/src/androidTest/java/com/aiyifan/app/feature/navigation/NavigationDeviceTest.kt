package com.aiyifan.app.feature.navigation

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.aiyifan.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationDeviceTest {
    @Test fun remoteChannelsSwitchTagsAndRestoreSelection() {
        ActivityScenario.launch(NavigationActivity::class.java).use { scenario ->
            var loaded = false
            repeat(60) {
                if (!loaded) {
                    scenario.onActivity { loaded = (it.findViewById<RecyclerView>(R.id.navigationRailRecycler).adapter?.itemCount ?: 0) > 0 }
                    if (!loaded) Thread.sleep(500)
                }
            }
            assertTrue("Live navigation must load", loaded)
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "navigation-verified.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            scenario.onActivity {
                val rail = it.findViewById<RecyclerView>(R.id.navigationRailRecycler)
                val tags = it.findViewById<RecyclerView>(R.id.navigationRecycler)
                assertEquals("电影", (rail.findViewHolderForAdapterPosition(0)!!.itemView as TextView).text.toString())
                assertEquals("喜剧", (tags.findViewHolderForAdapterPosition(0)!!.itemView as TextView).text.toString())
                rail.findViewHolderForAdapterPosition(1)!!.itemView.performClick()
            }
            Thread.sleep(500)
            scenario.onActivity {
                val tags = it.findViewById<RecyclerView>(R.id.navigationRecycler)
                assertEquals("大陆", (tags.findViewHolderForAdapterPosition(0)!!.itemView as TextView).text.toString())
            }
            scenario.recreate()
            loaded = false
            repeat(60) {
                if (!loaded) {
                    scenario.onActivity { loaded = it.findViewById<RecyclerView>(R.id.navigationRecycler).findViewHolderForAdapterPosition(0) != null }
                    if (!loaded) Thread.sleep(500)
                }
            }
            scenario.onActivity {
                assertEquals("大陆", (it.findViewById<RecyclerView>(R.id.navigationRecycler).findViewHolderForAdapterPosition(0)!!.itemView as TextView).text.toString())
            }
        }
    }
}
