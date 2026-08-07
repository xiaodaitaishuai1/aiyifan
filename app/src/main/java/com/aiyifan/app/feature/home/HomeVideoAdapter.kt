package com.aiyifan.app.feature.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.aiyifan.app.R
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.databinding.ItemHomeBannerBinding
import com.aiyifan.app.databinding.ItemHomeBannerPageBinding
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
    private var isBannerVisible = true
    private var currentBannerHolder: BannerViewHolder? = null

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

    fun setBannerVisible(isVisible: Boolean) {
        isBannerVisible = isVisible
        currentBannerHolder?.setFragmentVisible(isVisible)
    }

    private fun rebuildItems() {
        items.clear()
        items.addAll(HomeFeedItemFactory.create(videos, isLoadingMore))
        notifyDataSetChanged()
    }

    fun spanSizeAt(position: Int): Int = when (items.getOrNull(position)) {
        is HomeFeedItem.Card -> 1
        else -> 2
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HomeFeedItem.Banner -> BANNER_VIEW_TYPE
        is HomeFeedItem.Card -> CARD_VIEW_TYPE
        HomeFeedItem.Loading -> LOADING_VIEW_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            BANNER_VIEW_TYPE -> BannerViewHolder(
                ItemHomeBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                onClick,
            )
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
            is BannerViewHolder -> {
                currentBannerHolder?.takeIf { it !== holder }?.cancelAutoScroll()
                currentBannerHolder = holder
                holder.bind((items[position] as HomeFeedItem.Banner).videos, isBannerVisible)
            }
            is CardViewHolder -> holder.bind(
                (items[position] as HomeFeedItem.Card).video,
                isHighPriority = position < HIGH_PRIORITY_ITEM_COUNT,
            )
            is LoadingViewHolder -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is BannerViewHolder) {
            holder.setAttached(false, isBannerVisible)
            if (currentBannerHolder === holder) currentBannerHolder = null
        }
        super.onViewRecycled(holder)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is BannerViewHolder) {
            holder.setAttached(false, isBannerVisible)
            if (currentBannerHolder === holder) currentBannerHolder = null
        }
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder is BannerViewHolder) {
            currentBannerHolder = holder
            holder.setAttached(true, isBannerVisible)
        }
        super.onViewAttachedToWindow(holder)
    }

    override fun getItemCount(): Int = items.size

    private class BannerViewHolder(
        private val binding: ItemHomeBannerBinding,
        private val onClick: (VideoSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val pageAdapter = BannerPageAdapter(onClick)
        private var videos = emptyList<VideoSummary>()
        private var isAttached = false
        private var isFragmentVisible = true
        private val autoScroll = Runnable {
            if (canAutoScroll()) {
                binding.bannerPager.setCurrentItem(
                    HomeBannerCarouselPolicy.nextPage(binding.bannerPager.currentItem, videos.size),
                    true,
                )
                scheduleAutoScroll()
            }
        }

        init {
            binding.bannerPager.adapter = pageAdapter
            binding.bannerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateIndicator(position)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    when (state) {
                        ViewPager2.SCROLL_STATE_DRAGGING -> cancelAutoScroll()
                        ViewPager2.SCROLL_STATE_IDLE -> scheduleAutoScroll()
                    }
                }
            })
        }

        fun bind(videos: List<VideoSummary>, isFragmentVisible: Boolean) {
            cancelAutoScroll()
            this.videos = videos
            this.isFragmentVisible = isFragmentVisible
            pageAdapter.submitList(videos)
            binding.bannerPager.setCurrentItem(0, false)
            updateIndicator(0)
            scheduleAutoScroll()
        }

        fun setAttached(isAttached: Boolean, isFragmentVisible: Boolean) {
            this.isAttached = isAttached
            this.isFragmentVisible = isFragmentVisible
            if (isAttached && isFragmentVisible) scheduleAutoScroll() else cancelAutoScroll()
        }

        fun setFragmentVisible(isVisible: Boolean) {
            isFragmentVisible = isVisible
            if (isAttached && isVisible) scheduleAutoScroll() else cancelAutoScroll()
        }

        fun cancelAutoScroll() {
            binding.root.removeCallbacks(autoScroll)
        }

        private fun scheduleAutoScroll() {
            cancelAutoScroll()
            if (canAutoScroll()) {
                binding.root.postDelayed(autoScroll, AUTO_SCROLL_DELAY_MS)
            }
        }

        private fun canAutoScroll(): Boolean =
            HomeBannerCarouselPolicy.canAutoScroll(videos.size, isAttached && isFragmentVisible)

        private fun updateIndicator(position: Int) {
            binding.bannerPageIndicator.visibility = if (videos.size > 1) View.VISIBLE else View.GONE
            if (videos.size > 1) renderIndicatorDots(position) else binding.bannerPageIndicator.removeAllViews()
        }

        private fun renderIndicatorDots(currentPage: Int) {
            val density = binding.root.resources.displayMetrics.density
            binding.bannerPageIndicator.removeAllViews()
            videos.indices.forEach { index ->
                val isSelected = index == currentPage
                val diameter = (INDICATOR_DOT_SIZE_DP * density).toInt()
                val params = LinearLayout.LayoutParams(
                    (if (isSelected) INDICATOR_SELECTED_WIDTH_DP else INDICATOR_DOT_SIZE_DP * density).toInt(),
                    diameter,
                ).apply {
                    if (index < videos.lastIndex) marginEnd = (INDICATOR_DOT_MARGIN_DP * density).toInt()
                }
                binding.bannerPageIndicator.addView(View(binding.root.context).apply {
                    layoutParams = params
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = diameter / 2f
                        setColor(if (isSelected) binding.root.context.getColor(R.color.accent) else Color.WHITE)
                        alpha = if (isSelected) 255 else INDICATOR_INACTIVE_ALPHA
                    }
                })
            }
        }

        private companion object {
            const val INDICATOR_DOT_SIZE_DP = 6
            const val INDICATOR_SELECTED_WIDTH_DP = 14
            const val INDICATOR_DOT_MARGIN_DP = 4
            const val INDICATOR_INACTIVE_ALPHA = 128
        }
    }

    private class BannerPageAdapter(
        private val onClick: (VideoSummary) -> Unit,
    ) : RecyclerView.Adapter<BannerPageViewHolder>() {
        private var videos = emptyList<VideoSummary>()

        fun submitList(videos: List<VideoSummary>) {
            this.videos = videos
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerPageViewHolder =
            BannerPageViewHolder(
                ItemHomeBannerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                onClick,
            )

        override fun onBindViewHolder(holder: BannerPageViewHolder, position: Int) {
            holder.bind(videos[position])
        }

        override fun getItemCount(): Int = videos.size
    }

    private class BannerPageViewHolder(
        private val binding: ItemHomeBannerPageBinding,
        private val onClick: (VideoSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoSummary) {
            bindPoster(binding.bannerPagePoster, video.coverUrl, 16, 8, isHighPriority = true)
            binding.bannerPageTitle.text = video.title
            binding.root.setOnClickListener { onClick(video) }
        }
    }

    private class CardViewHolder(
        private val binding: ItemHomeVideoBinding,
        private val onClick: (VideoSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoSummary, isHighPriority: Boolean) {
            bindPoster(binding.cardPoster, video.coverUrl, 2, 3, isHighPriority)
            binding.cardTitle.text = video.title
            binding.cardMeta.text = video.updateStatus ?: listOfNotNull(video.year, video.area).joinToString(" / ")
            binding.root.setOnClickListener { onClick(video) }
        }
    }

    private class LoadingViewHolder(
        binding: ItemHomeLoadingBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val BANNER_VIEW_TYPE = 1
        const val CARD_VIEW_TYPE = 2
        const val LOADING_VIEW_TYPE = 3
        const val HIGH_PRIORITY_ITEM_COUNT = 6
        const val AUTO_SCROLL_DELAY_MS = 5_000L

        fun bindPoster(
            view: android.widget.ImageView,
            coverUrl: String,
            ratioWidth: Int,
            ratioHeight: Int,
            isHighPriority: Boolean,
        ) {
            if (coverUrl.isBlank()) {
                Glide.with(view).clear(view)
            } else {
                val targetWidth = (view.resources.displayMetrics.widthPixels / 2).coerceAtLeast(1)
                val targetHeight = (targetWidth * ratioHeight / ratioWidth).coerceAtLeast(1)
                Glide.with(view)
                    .load(coverUrl)
                    .placeholder(R.drawable.bg_poster)
                    .error(R.drawable.bg_poster)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .priority(if (isHighPriority) Priority.HIGH else Priority.NORMAL)
                    .override(targetWidth, targetHeight)
                    .transition(DrawableTransitionOptions.withCrossFade(160))
                    .centerCrop()
                    .into(view)
            }
        }

    }
}
