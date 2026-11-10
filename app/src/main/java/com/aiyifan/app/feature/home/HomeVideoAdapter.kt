package com.aiyifan.app.feature.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.databinding.ItemHomeLoadingBinding
import com.aiyifan.app.databinding.ItemHomeVideoBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class HomeVideoAdapter(
    private val onClick: (VideoSummary) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<HomeFeedItem>()
    private var videos = emptyList<VideoSummary>()
    private var isLoadingMore = false

    fun submitList(videos: List<VideoSummary>) {
        this.videos = videos
        isLoadingMore = false
        rebuildItems()
    }

    fun setLoadMoreLoading(isLoading: Boolean) {
        if (isLoadingMore == isLoading) return
        isLoadingMore = isLoading
        rebuildItems()
    }

    private fun rebuildItems() {
        items.clear()
        items.addAll(videos.map(HomeFeedItem::Card))
        if (videos.isNotEmpty() && isLoadingMore) items.add(HomeFeedItem.Loading)
        notifyDataSetChanged()
    }

    fun spanSizeAt(position: Int): Int = when (items.getOrNull(position)) {
        is HomeFeedItem.Card -> 1
        else -> 2
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HomeFeedItem.Card -> CARD_VIEW_TYPE
        HomeFeedItem.Loading -> LOADING_VIEW_TYPE
        is HomeFeedItem.Banner -> error("Banner belongs to HomeBannerAdapter")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        CARD_VIEW_TYPE -> CardViewHolder(
            ItemHomeVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick,
        )
        LOADING_VIEW_TYPE -> LoadingViewHolder(
            ItemHomeLoadingBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )
        else -> error("Unknown home view type: $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> holder.bind(
                (items[position] as HomeFeedItem.Card).video,
                isHighPriority = position < HIGH_PRIORITY_ITEM_COUNT,
            )
            is LoadingViewHolder -> Unit
        }
    }

    override fun getItemCount(): Int = items.size

    private class CardViewHolder(
        private val binding: ItemHomeVideoBinding,
        private val onClick: (VideoSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoSummary, isHighPriority: Boolean) {
            bindCardPoster(binding.cardPoster, video.coverUrl, isHighPriority)
            binding.cardTitle.text = video.title
            binding.cardMeta.text = video.updateStatus ?: listOfNotNull(video.year, video.area).joinToString(" / ")
            binding.root.setOnClickListener { onClick(video) }
        }
    }

    private class LoadingViewHolder(binding: ItemHomeLoadingBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val CARD_VIEW_TYPE = 2
        const val LOADING_VIEW_TYPE = 3
        const val HIGH_PRIORITY_ITEM_COUNT = 6

        fun bindCardPoster(view: android.widget.ImageView, coverUrl: String, isHighPriority: Boolean) =
            bindPoster(view, coverUrl, isHighPriority, HomePosterSizePolicy::card)

        private fun bindPoster(
            view: android.widget.ImageView,
            coverUrl: String,
            isHighPriority: Boolean,
            sizeForWidth: (Int) -> HomeImageSize,
        ) {
            if (!canLoadPoster(view)) return
            view.tag = coverUrl
            if (coverUrl.isBlank()) {
                Glide.with(view).clear(view)
            } else {
                view.post {
                    if (view.tag != coverUrl || !canLoadPoster(view)) return@post
                    val size = sizeForWidth(view.width)
                    Glide.with(view)
                        .load(coverUrl)
                        .placeholder(R.drawable.bg_poster)
                        .error(R.drawable.bg_poster)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .priority(if (isHighPriority) Priority.HIGH else Priority.NORMAL)
                        .override(size.width, size.height)
                        .transition(DrawableTransitionOptions.withCrossFade(160))
                        .centerCrop()
                        .into(view)
                }
            }
        }

        private fun canLoadPoster(view: View): Boolean {
            var context = view.context
            while (context is android.content.ContextWrapper) {
                if (context is android.app.Activity) return !context.isDestroyed && !context.isFinishing
                val base = context.baseContext
                if (base === context) break
                context = base
            }
            return true
        }
    }
}
