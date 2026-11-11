package com.aiyifan.app.feature.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.data.remote.NavigationChannel
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivityNavigationBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class NavigationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNavigationBinding
    private lateinit var tagsAdapter: NavigationCategoryAdapter
    private lateinit var railAdapter: NavigationCategoryAdapter
    private var channels: List<NavigationChannel> = emptyList()
    private var selectedId: String? = null
    private var loading = false
    private val repository = AppGraph.channelNavigationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        selectedId = savedInstanceState?.getString("selectedChannel")
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(left = true, right = true, bottom = true)
        binding.navigationToolbar.applySystemBarsPadding(top = true, growHeight = true)
        binding.navigationBackButton.setOnClickListener { finish() }
        tagsAdapter = NavigationCategoryAdapter { index ->
            val channel = channels.firstOrNull { it.id == selectedId } ?: return@NavigationCategoryAdapter
            startActivity(NavigationResultsActivity.intent(this, channel, channel.tags[index]))
        }
        railAdapter = NavigationCategoryAdapter(rail = true) { selectChannel(it) }
        binding.navigationRailRecycler.layoutManager = LinearLayoutManager(this)
        binding.navigationRailRecycler.adapter = railAdapter
        binding.navigationRecycler.layoutManager = GridLayoutManager(this, 2)
        binding.navigationRecycler.adapter = tagsAdapter
        binding.navigationRetryButton.setOnClickListener { loadCategories() }
        loadCategories()
    }

    private fun selectChannel(index: Int) {
        val channel = channels[index]
        selectedId = channel.id
        railAdapter.submitList(channels.map { it.name }, index)
        tagsAdapter.submitList(channel.tags.map { it.name })
        binding.navigationRecycler.scrollToPosition(0)
    }

    private fun loadCategories() {
        if (loading) return
        loading = true
        binding.navigationProgress.isVisible = true
        binding.navigationEmptyState.isVisible = false
        lifecycleScope.launch {
            try {
                channels = repository.loadChannels()
                binding.navigationRailRecycler.isVisible = channels.isNotEmpty()
                binding.navigationRecycler.isVisible = channels.isNotEmpty()
                if (channels.isEmpty()) showEmpty(R.string.navigation_empty)
                else selectChannel(channels.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showEmpty(R.string.navigation_load_failed)
            } finally {
                loading = false
                binding.navigationProgress.isVisible = false
            }
        }
    }

    private fun showEmpty(message: Int) {
        binding.navigationRailRecycler.isVisible = false
        binding.navigationRecycler.isVisible = false
        binding.navigationEmptyText.setText(message)
        binding.navigationEmptyState.isVisible = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selectedChannel", selectedId)
        super.onSaveInstanceState(outState)
    }
}
