package com.aiyifan.app.feature.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.data.remote.NavigationChannel
import com.aiyifan.app.core.data.remote.NavigationTag
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.core.ui.VideoListAdapter
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivitySimpleListBinding
import com.aiyifan.app.feature.video.VideoPlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class NavigationResultsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    private lateinit var adapter: VideoListAdapter
    private val repository = AppGraph.channelNavigationRepository
    private val videos = mutableListOf<VideoSummary>()
    private var page = 1
    private var loading = false
    private var hasMore = true
    private var failed = false
    private val tag by lazy {
        NavigationTag(intent.getStringExtra("tagId").orEmpty(), intent.getStringExtra("tagName").orEmpty(),
            intent.getIntExtra("classType", 1), intent.getIntExtra("index", 1), intent.getStringExtra("subId").orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(left = true, top = true, right = true, bottom = true)
        binding.backButton.setOnClickListener { finish() }
        binding.title.text = "${intent.getStringExtra("channelName").orEmpty()} · ${tag.name}"
        binding.actionButton.setOnClickListener { loadNext() }
        binding.emptyArtwork.isVisible = false
        adapter = VideoListAdapter { video -> startActivity(VideoPlayerActivity.intent(this, video.mediaKey)) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !recyclerView.canScrollVertically(1) && !failed) loadNext()
            }
        })
        loadNext()
    }

    private fun loadNext() {
        if (loading || !hasMore) return
        loading = true
        failed = false
        binding.actionButton.isEnabled = false
        binding.actionButton.setText(R.string.video_loading)
        lifecycleScope.launch {
            try {
                val result = repository.loadVideos(intent.getStringExtra("channelId").orEmpty(), tag, page)
                videos.addAll(result)
                val unique = videos.distinctBy { it.mediaKey }
                videos.clear()
                videos.addAll(unique)
                adapter.submitList(videos)
                hasMore = result.size >= 30
                page++
                binding.emptyState.isVisible = videos.isEmpty()
                binding.emptyView.setText(R.string.navigation_results_empty)
                binding.actionButton.setText(if (hasMore) R.string.navigation_more else R.string.navigation_end)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed = true
                binding.emptyState.isVisible = videos.isEmpty()
                binding.emptyView.setText(R.string.navigation_load_failed)
                binding.actionButton.setText(R.string.navigation_retry)
                if (videos.isNotEmpty()) Toast.makeText(this@NavigationResultsActivity, R.string.navigation_load_failed, Toast.LENGTH_SHORT).show()
            } finally {
                loading = false
                binding.actionButton.isEnabled = hasMore
            }
        }
    }

    companion object {
        fun intent(context: Context, channel: NavigationChannel, tag: NavigationTag): Intent =
            Intent(context, NavigationResultsActivity::class.java)
                .putExtra("channelId", channel.id).putExtra("channelName", channel.name)
                .putExtra("tagId", tag.id).putExtra("tagName", tag.name)
                .putExtra("classType", tag.classType).putExtra("index", tag.index).putExtra("subId", tag.subId)
    }
}
