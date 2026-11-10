package com.aiyifan.app.feature.main

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiyifan.app.R
import com.aiyifan.app.feature.home.HomeFragment
import com.aiyifan.app.feature.shortdrama.ShortDramaFragment
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabRetentionTest {
    @Test fun switchingTabsKeepsViewsAndAdaptersAndRestoresHiddenState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var home: Fragment
            lateinit var drama: Fragment
            lateinit var homeList: RecyclerView
            lateinit var dramaList: RecyclerView
            scenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                val tabs = activity.findViewById<TabLayout>(R.id.bottomTabs)
                home = manager.findFragmentByTag(HomeFragment::class.java.name)!!
                homeList = home.requireView().findViewById(R.id.videoRecycler)
                val homeAdapter = homeList.adapter
                tabs.getTabAt(1)!!.select()
                drama = manager.findFragmentByTag(ShortDramaFragment::class.java.name)!!
                dramaList = drama.requireView().findViewById(R.id.dramaRecycler)
                val dramaAdapter = dramaList.adapter
                repeat(3) {
                    tabs.getTabAt(0)!!.select()
                    assertSame(homeList, home.requireView().findViewById(R.id.videoRecycler))
                    assertSame(homeAdapter, homeList.adapter)
                    assertTrue(drama.isHidden)
                    assertEquals(Lifecycle.State.STARTED, drama.lifecycle.currentState)
                    tabs.getTabAt(1)!!.select()
                    assertSame(dramaList, drama.requireView().findViewById(R.id.dramaRecycler))
                    assertSame(dramaAdapter, dramaList.adapter)
                    assertTrue(home.isHidden)
                }
                assertEquals(2, manager.fragments.size)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                assertEquals(1, activity.findViewById<TabLayout>(R.id.bottomTabs).selectedTabPosition)
                assertTrue(manager.findFragmentByTag(HomeFragment::class.java.name)!!.isHidden)
                assertFalse(manager.findFragmentByTag(ShortDramaFragment::class.java.name)!!.isHidden)
            }
        }
    }
}
