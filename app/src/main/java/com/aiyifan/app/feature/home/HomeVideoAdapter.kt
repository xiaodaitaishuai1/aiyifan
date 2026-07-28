package com.aiyifan.app.feature.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.databinding.ItemHomeBannerBinding
import com.aiyifan.app.databinding.ItemHomeVideoBinding
import com.bumptech.glide.Glide

class HomeVideoAdapter(
    private val onClick: (VideoSummary) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<HomeFeedItem>()

    fun submitList(videos: List<VideoSummary>) {
        items.clear()
        items.addAll(HomeFeedItemFactory.create(videos))
        notifyDataSetChanged()
    }

    fun spanSizeAt(position: Int): Int = if (items.getOrNull(position) is HomeFeedItem.Banner) 2 else 1

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HomeFeedItem.Banner -> BANNER_VIEW_TYPE
        is HomeFeedItem.Card -> CARD_VIEW_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            BANNER_VIEW_TYPE -> BannerViewHolder(
                ItemHomeBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                onClick,
            )
            else -> CardViewHolder(
                ItemHomeVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                onClick,
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is BannerViewHolder -> holder.bind((items[position] as HomeFeedItem.Banner).video)
            is CardViewHolder -> holder.bind((items[position] as HomeFeedItem.Card).video)
        }
    }

    override fun getItemCount(): Int = items.size

    private class BannerViewHolder(
        private val binding: ItemHomeBannerBinding,
        private val onClick: (VideoSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoSummary) {
            bindPoster(binding.bannerPoster, video.coverUrl)
            binding.bannerTitle.text = video.title
            binding.root.setOnClickListener { onClick(video) }
        }
    }

    private class CardViewHolder(
        private val binding: ItemHomeVideoBinding,
        private val onClick: (VideoSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoSummary) {
            bindPoster(binding.cardPoster, video.coverUrl)
            binding.cardTitle.text = video.title
            binding.cardMeta.text = video.updateStatus ?: listOfNotNull(video.year, video.area).joinToString(" / ")
            binding.root.setOnClickListener { onClick(video) }
        }
    }

    private companion object {
        const val BANNER_VIEW_TYPE = 1
        const val CARD_VIEW_TYPE = 2

        fun bindPoster(view: android.widget.ImageView, coverUrl: String) {
            if (coverUrl.isBlank()) {
                Glide.with(view).clear(view)
            } else {
                Glide.with(view).load(coverUrl).centerCrop().into(view)
            }
        }
    }
}
