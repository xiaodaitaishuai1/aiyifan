package com.aiyifan.app.feature.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.model.WatchHistory
import com.aiyifan.app.databinding.ItemWatchHistoryBinding
import com.bumptech.glide.Glide

class WatchHistoryAdapter(
    private val onOpen: (WatchHistory) -> Unit,
    private val onSelect: (WatchHistory) -> Unit,
    private val onDelete: (WatchHistory) -> Unit,
) : ListAdapter<WatchHistory, WatchHistoryAdapter.Holder>(DIFF) {
    private var selection: Set<String>? = null
    fun showSelection(keys: Set<String>?) {
        selection = keys?.toSet()
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, "selection")
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemWatchHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemWatchHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: WatchHistory) {
            binding.title.text = record.title
            binding.episode.text = record.episodeTitle ?: binding.root.context.getString(R.string.history_episode_unknown)
            binding.time.text = WatchProgressPresentation.time(record.progressMs, record.durationMs)
            binding.progress.progress = WatchProgressPresentation.percent(record.progressMs, record.durationMs)
            binding.selection.isVisible = selection != null
            binding.selection.isChecked = selection?.contains(record.mediaKey) == true
            binding.deleteButton.isVisible = selection == null
            Glide.with(binding.poster).load(record.coverUrl.ifBlank { null }).placeholder(R.color.poster_placeholder).into(binding.poster)
            binding.root.setOnClickListener { if (selection == null) onOpen(record) else onSelect(record) }
            binding.root.setOnLongClickListener { onSelect(record); true }
            binding.deleteButton.setOnClickListener { onDelete(record) }
        }
    }
    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<WatchHistory>() {
            override fun areItemsTheSame(oldItem: WatchHistory, newItem: WatchHistory) = oldItem.mediaKey == newItem.mediaKey
            override fun areContentsTheSame(oldItem: WatchHistory, newItem: WatchHistory) = oldItem == newItem
        }
    }
}
