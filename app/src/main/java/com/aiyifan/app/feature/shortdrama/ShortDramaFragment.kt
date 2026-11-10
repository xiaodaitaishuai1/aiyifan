package com.aiyifan.app.feature.shortdrama

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
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
        load(refresh = true)
    }

    private fun load(refresh: Boolean = false) {
        val ui = binding ?: return
        val request = pagination.begin(refresh) ?: return
        if (refresh) requestJob?.cancel()
        failed = false
        render()
        requestJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = if (request.page == 1 && refreshRequired) {
                    AppGraph.catalogRepository.refreshShortDramaPage()
                } else {
                    AppGraph.catalogRepository.getShortDramaPage(request.page)
                }
                if (pagination.complete(request, response)) {
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
