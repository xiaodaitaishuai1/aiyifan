package com.aiyifan.app.feature.shortdrama

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.model.ShortDramaCategory
import com.aiyifan.app.core.ui.ChipAppearanceResolver
import com.aiyifan.app.databinding.FragmentShortDramaBinding
import com.aiyifan.app.feature.video.VideoPlayerActivity
import com.bumptech.glide.Glide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ShortDramaFragment : Fragment() {
    private var binding: FragmentShortDramaBinding? = null
    private var pagination = ShortDramaPagination()
    private lateinit var adapter: ShortDramaAdapter
    private var requestJob: Job? = null
    private var failed = false
    private var refreshRequired = false
    private var selectedCategory: ShortDramaCategory? = null
    private var categories: List<ShortDramaCategory> = emptyList()
    private var requestVersion = 0L
    private var categoryJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentShortDramaBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = requireNotNull(binding)
        pagination = ShortDramaPagination()
        adapter = ShortDramaAdapter(Glide.with(this)) { video ->
            startActivity(VideoPlayerActivity.intent(requireContext(), video.mediaKey))
        }
        val layout = GridLayoutManager(requireContext(), 2)
        ui.dramaRecycler.layoutManager = layout
        ui.dramaRecycler.adapter = adapter
        ui.dramaRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !failed && layout.findLastVisibleItemPosition() >= adapter.itemCount - 6) load()
            }
        })
        ui.dramaRefresh.setOnRefreshListener { refreshRequired = true; load(refresh = true) }
        ui.dramaStatus.setOnClickListener { if (failed) load() }
        renderCategories()
        loadCategories()
        load(refresh = true)
    }

    private fun loadCategories() {
        categoryJob?.cancel()
        categoryJob = viewLifecycleOwner.lifecycleScope.launch {
            runCatching { AppGraph.catalogRepository.getShortDramaCategories() }
                .onSuccess {
                    categories = it
                    renderCategories()
                }
        }
    }

    private fun renderCategories() {
        val ui = binding ?: return
        ui.dramaCategoryContainer.removeAllViews()
        val all = TextView(requireContext()).apply {
            text = getString(R.string.short_drama_all)
            tag = null
            setOnClickListener { selectCategory(null) }
            layoutParams = categoryLayoutParams()
        }
        ui.dramaCategoryContainer.addView(all)
        categories.forEach { category ->
            val tab = TextView(requireContext()).apply {
                text = category.name
                tag = category.id
                setOnClickListener { selectCategory(category) }
                layoutParams = categoryLayoutParams()
            }
            ui.dramaCategoryContainer.addView(tab)
        }
        updateCategoryAppearance()
    }

    private fun categoryLayoutParams() = ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { setMargins(4, 0, 4, 0) }

    private fun updateCategoryAppearance() {
        binding?.dramaCategoryContainer?.children?.forEach { view ->
            val appearance = ChipAppearanceResolver.forSelection(view.tag == selectedCategory?.id)
            view.setBackgroundResource(appearance.backgroundRes)
            (view as TextView).setTextColor(resources.getColor(appearance.textColorRes, null))
            view.setPadding(20, 10, 20, 10)
        }
    }

    private fun selectCategory(category: ShortDramaCategory?) {
        if (selectedCategory?.id == category?.id && pagination.videos.isNotEmpty()) return
        selectedCategory = category
        requestVersion++
        requestJob?.cancel()
        pagination = ShortDramaPagination()
        adapter.submitList(emptyList())
        updateCategoryAppearance()
        load(refresh = true)
    }

    private fun load(refresh: Boolean = false) {
        val ui = binding ?: return
        val request = pagination.begin(refresh) ?: return
        val category = selectedCategory
        val categoryId = category?.id
        val version = requestVersion
        if (refresh) requestJob?.cancel()
        failed = false
        render()
        requestJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = if (category == null && request.page == 1 && refreshRequired) {
                    AppGraph.catalogRepository.refreshShortDramaPage()
                } else if (category != null && request.page == 1 && refresh) {
                    AppGraph.catalogRepository.refreshShortDramaPage(category)
                } else if (category != null) {
                    AppGraph.catalogRepository.getShortDramaPage(category, request.page)
                } else {
                    AppGraph.catalogRepository.getShortDramaPage(request.page)
                }
                if (version == requestVersion && selectedCategory?.id == categoryId && pagination.complete(request, response)) {
                    if (request.page == 1) refreshRequired = false
                    adapter.submitList(pagination.videos) {
                        if (binding === ui && request.page == 1) ui.dramaRecycler.scrollToPosition(0)
                        ui.dramaRecycler.post { loadIfViewportHasRoom() }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (pagination.fail(request)) failed = true
            } finally {
                if (binding === ui) render()
            }
        }
    }

    private fun loadIfViewportHasRoom() {
        val ui = binding ?: return
        if (!pagination.loading && pagination.hasMore && !ui.dramaRecycler.canScrollVertically(1)) load()
    }

    private fun render() {
        val ui = binding ?: return
        ui.dramaRefresh.isRefreshing = pagination.loading && pagination.videos.isEmpty()
        ui.dramaStatus.isVisible = failed || pagination.videos.isEmpty() || pagination.loading || !pagination.hasMore
        ui.dramaStatus.isEnabled = failed
        ui.dramaStatus.setText(when {
            failed -> R.string.short_drama_retry
            pagination.loading -> R.string.short_drama_loading
            pagination.videos.isEmpty() -> R.string.short_drama_empty
            else -> R.string.short_drama_end
        })
    }

    override fun onDestroyView() {
        requestJob?.cancel()
        binding?.dramaRecycler?.adapter = null
        binding = null
        super.onDestroyView()
    }
}
