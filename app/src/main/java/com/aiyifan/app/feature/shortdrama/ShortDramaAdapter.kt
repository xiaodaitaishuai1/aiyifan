package com.aiyifan.app.feature.shortdrama

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.databinding.ItemHomeVideoBinding
import com.bumptech.glide.RequestManager

class ShortDramaAdapter(
    private val images: RequestManager,
    private val onClick: (VideoSummary) -> Unit,
) :
    ListAdapter<VideoSummary, ShortDramaAdapter.Holder>(object : DiffUtil.ItemCallback<VideoSummary>() {
        override fun areItemsTheSame(old: VideoSummary, new: VideoSummary) = old.mediaKey == new.mediaKey
        override fun areContentsTheSame(old: VideoSummary, new: VideoSummary) = old == new
    }) {
    class Holder(val binding: ItemHomeVideoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemHomeVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val video = getItem(position)
        with(holder.binding) {
            cardTitle.text = video.title
            cardTitle.setTextColor(Color.WHITE)
            cardMeta.text = video.updateStatus.orEmpty()
            cardMeta.setTextColor(Color.WHITE)
            cardBottomScrim.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, 0xCC000000.toInt()),
            )
            images.load(video.coverUrl.ifBlank { null })
                .placeholder(R.drawable.bg_poster).error(R.drawable.bg_poster).centerCrop().into(cardPoster)
            root.setOnClickListener { onClick(video) }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        images.clear(holder.binding.cardPoster)
        super.onViewRecycled(holder)
    }
}
