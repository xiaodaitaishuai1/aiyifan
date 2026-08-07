package com.aiyifan.app.feature.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.aiyifan.app.R
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivityMainBinding
import com.aiyifan.app.feature.home.HomeFragment
import com.aiyifan.app.feature.hot.HotFragment
import com.aiyifan.app.feature.mine.MineFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.fragmentContainer.applySystemBarsPadding(left = true, top = true, right = true)
        binding.bottomTabs.applySystemBarsPadding(left = true, right = true, bottom = true, growHeight = true)

        val selectedTabPosition = savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: 0
        binding.bottomTabs.getTabAt(selectedTabPosition)?.select()
        show(fragmentFor(selectedTabPosition))
        binding.bottomTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                show(fragmentFor(tab.position))
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit

            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_TAB, binding.bottomTabs.selectedTabPosition)
        super.onSaveInstanceState(outState)
    }

    private fun fragmentFor(position: Int): Fragment = when (position) {
        1 -> HotFragment()
        2 -> MineFragment()
        else -> HomeFragment()
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private companion object {
        const val KEY_SELECTED_TAB = "selected_tab"
    }
}
