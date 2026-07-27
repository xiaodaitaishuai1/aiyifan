package com.aiyifan.app.feature.history

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.core.ui.VideoListAdapter
import com.aiyifan.app.core.ui.toVideoSummary
import com.aiyifan.app.databinding.ActivitySimpleListBinding
import com.aiyifan.app.feature.video.VideoPlayerActivity

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    private lateinit var adapter: VideoListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(left = true, right = true, bottom = true)
        binding.topBar.applySystemBarsPadding(top = true, growHeight = true)
        binding.title.text = "观看记录"
        binding.actionButton.text = "清空"
        binding.backButton.setOnClickListener { finish() }
        binding.actionButton.setOnClickListener {
            AppGraph.catalogRepository.clearHistory()
            render()
        }
        adapter = VideoListAdapter { video ->
            startActivity(VideoPlayerActivity.intent(this, video.mediaKey))
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        render()
    }

    private fun render() {
        val items = AppGraph.catalogRepository.getHistory().map { it.toVideoSummary() }
        adapter.submitList(items)
        binding.emptyState.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.emptyView.text = "暂无观看记录"
    }
}
