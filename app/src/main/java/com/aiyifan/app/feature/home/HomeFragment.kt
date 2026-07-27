package com.aiyifan.app.feature.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.children
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.model.Category
import com.aiyifan.app.core.ui.VideoListAdapter
import com.aiyifan.app.databinding.FragmentHomeBinding
import com.aiyifan.app.feature.history.HistoryActivity
import com.aiyifan.app.feature.search.SearchActivity
import com.aiyifan.app.feature.video.VideoPlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val repository = AppGraph.catalogRepository
    private lateinit var adapter: VideoListAdapter
    private var selectedCategory: Category? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = VideoListAdapter { video ->
            startActivity(VideoPlayerActivity.intent(requireContext(), video.mediaKey))
        }
        binding.videoRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.videoRecycler.adapter = adapter
        binding.searchBox.setOnClickListener { startActivity(Intent(requireContext(), SearchActivity::class.java)) }
        binding.historyButton.setOnClickListener { startActivity(Intent(requireContext(), HistoryActivity::class.java)) }
        binding.homeRefresh.setOnRefreshListener { loadHome(refresh = true) }
        loadHome()
    }

    private fun loadHome(refresh: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (refresh) {
                    repository.refreshHome()
                }
                val categories = repository.getCategories()
                val category = categories.firstOrNull { it.id == selectedCategory?.id } ?: categories.first()
                selectedCategory = category
                renderCategories(categories)
                adapter.submitList(repository.getHomeVideos(category.id))
            } catch (exception: Throwable) {
                if (exception is CancellationException) throw exception
                context?.let { Toast.makeText(it, "首页刷新失败", Toast.LENGTH_SHORT).show() }
                if (!refresh) {
                    adapter.submitList(emptyList())
                }
            } finally {
                _binding?.homeRefresh?.isRefreshing = false
            }
        }
    }

    private fun renderCategories(categories: List<Category>) {
        binding.categoryContainer.removeAllViews()
        categories.forEach { category ->
            val appearance = HomeCategoryAppearance.forSelection(category.id == selectedCategory?.id)
            val tab = TextView(requireContext()).apply {
                text = category.name
                textSize = 14f
                tag = category.id
                setTextColor(resources.getColor(appearance.textColorRes, null))
                setBackgroundResource(appearance.backgroundRes)
                val params = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                params.setMargins(4)
                layoutParams = params
                setOnClickListener { selectCategory(category) }
            }
            binding.categoryContainer.addView(tab)
        }
    }

    private fun selectCategory(category: Category) {
        selectedCategory = category
        binding.categoryContainer.children.forEach { tab ->
            val appearance = HomeCategoryAppearance.forSelection(tab.tag == category.id)
            tab.setBackgroundResource(appearance.backgroundRes)
            (tab as TextView).setTextColor(resources.getColor(appearance.textColorRes, null))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.getHomeVideos(category.id) }
                .onSuccess { videos -> adapter.submitList(videos) }
                .onFailure {
                    Toast.makeText(requireContext(), "分类数据加载失败", Toast.LENGTH_SHORT).show()
                    adapter.submitList(emptyList())
                }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
