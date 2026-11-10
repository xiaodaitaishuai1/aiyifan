package com.aiyifan.app.feature.main

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.aiyifan.app.R
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivityMainBinding
import com.aiyifan.app.feature.home.HomeFragment
import com.aiyifan.app.feature.shortdrama.ShortDramaFragment
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

        val selectedTabPosition = (savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: 0).takeIf {
            it in 0 until binding.bottomTabs.tabCount
        } ?: 0
        for (index in 0 until binding.bottomTabs.tabCount) {
            val tab = binding.bottomTabs.getTabAt(index) ?: continue
            tab.customView = TextView(this).apply {
                text = tab.text
                gravity = Gravity.CENTER
                maxLines = 1
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
        binding.bottomTabs.getTabAt(selectedTabPosition)?.select()
        updateTabTextSizes(selectedTabPosition)
        updateTopBackground(selectedTabPosition)
        if (savedInstanceState == null) {
            show(fragmentFor(selectedTabPosition))
        }
        binding.bottomTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                updateTabTextSizes(tab.position)
                updateTopBackground(tab.position)
                show(fragmentFor(tab.position))
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit

            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })
    }

    private fun updateTabTextSizes(selectedPosition: Int) {
        for (index in 0 until binding.bottomTabs.tabCount) {
            val textView = binding.bottomTabs.getTabAt(index)?.customView as? TextView
            textView?.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(if (index == selectedPosition) R.dimen.sp_16 else R.dimen.sp_14),
            )
            textView?.setTextColor(getColor(if (index == selectedPosition) R.color.accent else R.color.text_secondary))
        }
    }

    private fun updateTopBackground(selectedPosition: Int) {
        val color = getColor(if (selectedPosition == 1) R.color.page_bg else R.color.surface)
        binding.fragmentContainer.setBackgroundColor(color)
        window.statusBarColor = color
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_TAB, binding.bottomTabs.selectedTabPosition)
        super.onSaveInstanceState(outState)
    }

    private fun fragmentFor(position: Int): Fragment {
        val type = when (position) {
            1 -> ShortDramaFragment::class.java
            2 -> MineFragment::class.java
            else -> HomeFragment::class.java
        }
        supportFragmentManager.findFragmentByTag(type.name)?.let { return it }
        return when (position) {
            1 -> ShortDramaFragment()
            2 -> MineFragment()
            else -> HomeFragment()
        }
    }

    private fun show(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments.filter { it.id == R.id.fragmentContainer && it !== fragment }.forEach {
            transaction.hide(it).setMaxLifecycle(it, Lifecycle.State.STARTED)
        }
        if (fragment.isAdded) transaction.show(fragment)
        else transaction.add(R.id.fragmentContainer, fragment, fragment.javaClass.name)
        transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            .setPrimaryNavigationFragment(fragment)
            .commitNow()
    }

    private companion object {
        const val KEY_SELECTED_TAB = "selected_tab"
    }
}
