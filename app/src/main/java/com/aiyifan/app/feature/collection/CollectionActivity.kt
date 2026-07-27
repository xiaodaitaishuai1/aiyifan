package com.aiyifan.app.feature.collection

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

class CollectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySimpleListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(left = true, right = true, bottom = true)
        binding.topBar.applySystemBarsPadding(top = true, growHeight = true)
        binding.title.text = "我的收藏"
        binding.actionButton.text = ""
        binding.backButton.setOnClickListener { finish() }
        val adapter = VideoListAdapter { video ->
            startActivity(VideoPlayerActivity.intent(this, video.mediaKey))
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        val items = AppGraph.catalogRepository.getFavorites().map { it.toVideoSummary() }
        adapter.submitList(items)
        binding.emptyState.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.emptyView.text = "暂无收藏"
    }
}
