package com.aiyifan.app.feature.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.model.FavoriteVideo
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivityCollectionBinding
import com.aiyifan.app.databinding.ItemCollectionBinding
import com.aiyifan.app.feature.video.VideoPlayerActivity
import com.bumptech.glide.Glide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CollectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCollectionBinding
    private val repository get() = AppGraph.catalogRepository
    private val adapter = CollectionAdapter()
    private var checkJob: Job? = null
    private var checkGeneration = 0L
    private var onlyUpdated = false
    private val failedKeys = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(left = true, right = true, bottom = true)
        binding.topBar.applySystemBarsPadding(top = true, growHeight = true)
        binding.backButton.setOnClickListener { finish() }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        onlyUpdated = savedInstanceState?.getBoolean("onlyUpdated") ?: false
        binding.filters.check(if (onlyUpdated) R.id.updatedFilter else R.id.allFilter)
        binding.filters.setOnCheckedChangeListener { _, id -> onlyUpdated = id == R.id.updatedFilter; render() }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ -> binding.recycler.canScrollVertically(-1) }
        binding.swipeRefresh.setOnRefreshListener { checkUpdates(force = true) }
        binding.checkStatus.setOnClickListener { checkUpdates(force = true, retryOnly = failedKeys.isNotEmpty()) }
    }

    override fun onStart() {
        super.onStart()
        render()
        checkUpdates(force = false)
    }
    override fun onStop() {
        checkGeneration++
        checkJob?.cancel()
        checkJob = null
        binding.swipeRefresh.isRefreshing = false
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("onlyUpdated", onlyUpdated)
        super.onSaveInstanceState(outState)
    }

    private fun checkUpdates(force: Boolean, retryOnly: Boolean = false) {
        val generation = ++checkGeneration
        checkJob?.cancel()
        checkJob = lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            binding.checkStatus.setText(R.string.collection_checking)
            val keys = repository.getFavorites().map { it.mediaKey }.filter { !retryOnly || it in failedKeys }
            failedKeys.retainAll(repository.getFavorites().map { it.mediaKey }.toSet())
            try {
                for (key in keys) {
                    try {
                        repository.checkFavoriteUpdates(key, force)
                        failedKeys.remove(key)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        if (repository.isFavorite(key)) failedKeys.add(key)
                    }
                    render()
                }
                binding.checkStatus.setText(if (failedKeys.isEmpty()) R.string.collection_check_done else R.string.collection_check_failed)
            } finally {
                if (generation == checkGeneration) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun render() {
        val items = repository.getFavorites().filter { !onlyUpdated || repository.getFavoriteTracking(it.mediaKey)?.hasUpdates == true }
        adapter.items = items
        adapter.notifyDataSetChanged()
        binding.emptyView.isVisible = items.isEmpty()
        binding.emptyView.setText(if (onlyUpdated) R.string.collection_no_updates else R.string.collection_empty)
    }

    private inner class CollectionAdapter : RecyclerView.Adapter<CollectionAdapter.Holder>() {
        var items = emptyList<FavoriteVideo>()
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemCollectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
        inner class Holder(private val row: ItemCollectionBinding) : RecyclerView.ViewHolder(row.root) {
            fun bind(video: FavoriteVideo) {
                val snapshot = repository.getFavoriteTracking(video.mediaKey)
                val history = repository.getHistory().firstOrNull { it.mediaKey == video.mediaKey }
                row.title.text = video.title
                Glide.with(row.poster).load(video.coverUrl.takeIf { it.isNotBlank() }).placeholder(R.color.poster_placeholder).centerCrop().into(row.poster)
                row.updateBadge.isVisible = snapshot?.hasUpdates == true
                row.meta.text = listOf(
                    getString(R.string.collection_latest, snapshot?.latestEpisodeTitle ?: getString(R.string.collection_unknown_episode)),
                    history?.let { getString(R.string.collection_watched, it.episodeTitle ?: getString(R.string.collection_unknown_episode)) } ?: getString(R.string.collection_not_watched),
                    snapshot?.let { getString(R.string.collection_checked, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.checkedAt))) } ?: getString(R.string.collection_not_checked),
                ).joinToString("\n")
                row.root.setOnClickListener { startActivity(VideoPlayerActivity.intent(this@CollectionActivity, video.mediaKey)) }
                row.removeButton.setOnClickListener {
                    repository.removeFavorite(video.mediaKey)
                    failedKeys.remove(video.mediaKey)
                    render()
                }
            }
        }
    }
}
