package com.aiyifan.app.feature.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.model.WatchHistory
import com.aiyifan.app.databinding.ItemContinueCardBinding
import com.aiyifan.app.databinding.ItemContinueWatchingBinding
import com.aiyifan.app.feature.history.WatchProgressPresentation
import com.bumptech.glide.Glide

class ContinueWatchingAdapter(
    private val onOpen: (WatchHistory) -> Unit,
    private val onHide: (WatchHistory) -> Unit,
    private val onAll: () -> Unit,
) : RecyclerView.Adapter<ContinueWatchingAdapter.Holder>() {
    private var records = emptyList<WatchHistory>()
    fun submitRecords(values: List<WatchHistory>) {
        if (values == records) return
        val wasEmpty = records.isEmpty()
        records = values
        when {
            wasEmpty && values.isNotEmpty() -> notifyItemInserted(0)
            !wasEmpty && values.isEmpty() -> notifyItemRemoved(0)
            values.isNotEmpty() -> notifyItemChanged(0)
        }
    }
    override fun getItemCount(): Int = if (records.isEmpty()) 0 else 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemContinueWatchingBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(records)

    inner class Holder(binding: ItemContinueWatchingBinding) : RecyclerView.ViewHolder(binding.root) {
        private val cards = Cards(onOpen, onHide)
        init {
            binding.records.layoutManager = LinearLayoutManager(binding.root.context, RecyclerView.HORIZONTAL, false)
            binding.records.adapter = cards
            binding.allButton.setOnClickListener { onAll() }
        }
        fun bind(records: List<WatchHistory>) { cards.submitList(records) }
    }

    private class Cards(val onOpen: (WatchHistory) -> Unit, val onHide: (WatchHistory) -> Unit) :
        ListAdapter<WatchHistory, Card>(object : DiffUtil.ItemCallback<WatchHistory>() {
            override fun areItemsTheSame(oldItem: WatchHistory, newItem: WatchHistory) = oldItem.mediaKey == newItem.mediaKey
            override fun areContentsTheSame(oldItem: WatchHistory, newItem: WatchHistory) = oldItem == newItem
        }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Card(
            ItemContinueCardBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )
        override fun onBindViewHolder(holder: Card, position: Int) {
            val record = getItem(position)
            with(holder.binding) {
                title.text = record.title
                progress.progress = WatchProgressPresentation.percent(record.progressMs, record.durationMs)
                time.text = if (WatchProgressPresentation.completed(record.progressMs, record.durationMs))
                    root.context.getString(R.string.episode_completed)
                else WatchProgressPresentation.compactTime(record.progressMs, record.durationMs)
                Glide.with(poster).load(record.coverUrl.ifBlank { null }).placeholder(R.color.poster_placeholder).into(poster)
                root.setOnClickListener { onOpen(record) }
                clearButton.setOnClickListener { onHide(record) }
            }
        }
    }
    private class Card(val binding: ItemContinueCardBinding) : RecyclerView.ViewHolder(binding.root)

}
