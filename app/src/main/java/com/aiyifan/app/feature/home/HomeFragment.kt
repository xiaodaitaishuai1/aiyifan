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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.model.Category
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
    private lateinit var adapter: HomeVideoAdapter
    private val pagination = HomeFeedPagination()
    private var selectedCategory: Category? = null
    private var homeRequestVersion = 0L
    private var isInitialPageLoading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = HomeVideoAdapter { video ->
            startActivity(VideoPlayerActivity.intent(requireContext(), video.mediaKey))
        }
        binding.videoRecycler.layoutManager = GridLayoutManager(requireContext(), 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = adapter.spanSizeAt(position)
            }
        }
        binding.videoRecycler.adapter = adapter
        binding.homeRefresh.setColorSchemeResources(R.color.accent)
        binding.homeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface)
        binding.videoRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || recyclerView.canScrollVertically(1) || isInitialPageLoading) return
                val category = selectedCategory ?: return
                val page = pagination.beginNextPage()
                if (page == null) {
                    if (!pagination.hasMore) showToast("没有更多了")
                    return
                }
                val requestVersion = homeRequestVersion
                recyclerView.post {
                    if (isCurrentRequest(requestVersion) && selectedCategory?.id == category.id) {
                        loadNextPage(category, page, requestVersion)
                    }
                }
            }
        })
        binding.searchBox.setOnClickListener { startActivity(Intent(requireContext(), SearchActivity::class.java)) }
        binding.historyButton.setOnClickListener { startActivity(Intent(requireContext(), HistoryActivity::class.java)) }
        binding.homeRefresh.setOnRefreshListener(::loadHome)
        loadHome()
    }

    private fun loadHome() {
        val requestVersion = ++homeRequestVersion
        isInitialPageLoading = true
        pagination.cancelPending()
        adapter.setLoadMoreLoading(false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val categories = repository.getCategories()
                if (!isCurrentRequest(requestVersion)) return@launch
                val category = categories.firstOrNull { it.id == selectedCategory?.id } ?: categories.first()
                selectedCategory = category
                renderCategories(categories)
                submitHomeFeed(category, requestVersion)
            } catch (exception: Throwable) {
                if (exception is CancellationException) throw exception
                showToast("首页刷新失败")
            } finally {
                if (isCurrentRequest(requestVersion)) {
                    isInitialPageLoading = false
                    _binding?.homeRefresh?.isRefreshing = false
                }
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
        val requestVersion = ++homeRequestVersion
        selectedCategory = category
        isInitialPageLoading = true
        binding.categoryContainer.children.forEach { tab ->
            val appearance = HomeCategoryAppearance.forSelection(tab.tag == category.id)
            tab.setBackgroundResource(appearance.backgroundRes)
            (tab as TextView).setTextColor(resources.getColor(appearance.textColorRes, null))
        }
        pagination.clear()
        adapter.submitList(emptyList())
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                submitHomeFeed(category, requestVersion)
            } catch (exception: Throwable) {
                if (exception is CancellationException) throw exception
                showToast("分类数据加载失败")
            } finally {
                if (isCurrentRequest(requestVersion)) {
                    isInitialPageLoading = false
                }
            }
        }
    }

    private suspend fun submitHomeFeed(category: Category, requestVersion: Long) {
        val response = repository.getHomeVideoPage(category, page = 1)
        if (isCurrentRequest(requestVersion) && selectedCategory?.id == category.id) {
            adapter.submitList(pagination.reset(response))
        }
    }

    private fun loadNextPage(category: Category, page: Int, requestVersion: Long) {
        val categoryId = category.id
        adapter.setLoadMoreLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = repository.getHomeVideoPage(category, page)
                if (isCurrentRequest(requestVersion) && selectedCategory?.id == categoryId) {
                    adapter.submitList(pagination.append(page, response))
                }
            } catch (exception: Throwable) {
                if (exception is CancellationException) throw exception
                if (isCurrentRequest(requestVersion) && selectedCategory?.id == categoryId) {
                    pagination.fail(page)
                    adapter.setLoadMoreLoading(false)
                    showToast("加载更多失败")
                }
            }
        }
    }

    private fun isCurrentRequest(requestVersion: Long): Boolean =
        requestVersion == homeRequestVersion

    private fun showToast(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
