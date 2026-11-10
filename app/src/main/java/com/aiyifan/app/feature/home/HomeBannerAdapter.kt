package com.aiyifan.app.feature.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.aiyifan.app.R
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.databinding.ItemHomeBannerBinding
import com.aiyifan.app.databinding.ItemHomeBannerPageBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class HomeBannerAdapter(
    private val onClick: (VideoSummary) -> Unit,
) : RecyclerView.Adapter<HomeBannerAdapter.BannerViewHolder>() {
    private var videos = emptyList<VideoSummary>()
    private var isBannerVisible = true
    private var currentBannerHolder: BannerViewHolder? = null

    fun submitList(videos: List<VideoSummary>) {
        this.videos = videos
        notifyDataSetChanged()
    }

    fun setBannerVisible(isVisible: Boolean) {
        isBannerVisible = isVisible
        currentBannerHolder?.setFragmentVisible(isVisible)
    }

    override fun getItemCount(): Int = if (videos.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder = BannerViewHolder(
        ItemHomeBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick,
    )

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        currentBannerHolder?.takeIf { it !== holder }?.cancelAutoScroll()
        currentBannerHolder = holder
        holder.bind(videos, isBannerVisible)
    }

    override fun onViewRecycled(holder: BannerViewHolder) {
        holder.setAttached(false, isBannerVisible)
        if (currentBannerHolder === holder) currentBannerHolder = null
        super.onViewRecycled(holder)
    }

    override fun onViewDetachedFromWindow(holder: BannerViewHolder) {
        holder.setAttached(false, isBannerVisible)
        if (currentBannerHolder === holder) currentBannerHolder = null
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewAttachedToWindow(holder: BannerViewHolder) {
        currentBannerHolder = holder
        holder.setAttached(true, isBannerVisible)
        super.onViewAttachedToWindow(holder)
    }

    class BannerViewHolder(
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
            val dotSize = HomePosterSizePolicy.pxFromDp(INDICATOR_DOT_SIZE_DP, density)
            val selectedWidth = HomePosterSizePolicy.pxFromDp(INDICATOR_SELECTED_WIDTH_DP, density)
            val dotMargin = HomePosterSizePolicy.pxFromDp(INDICATOR_DOT_MARGIN_DP, density)
            binding.bannerPageIndicator.removeAllViews()
            videos.indices.forEach { index ->
                val isSelected = index == currentPage
                val params = LinearLayout.LayoutParams(
                    if (isSelected) selectedWidth else dotSize,
                    dotSize,
                ).apply {
                    if (index < videos.lastIndex) marginEnd = dotMargin
                }
                binding.bannerPageIndicator.addView(View(binding.root.context).apply {
                    layoutParams = params
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dotSize / 2f
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
            bindBannerPoster(binding.bannerPagePoster, video.coverUrl)
            binding.bannerPageTitle.text = video.title
            binding.root.setOnClickListener { onClick(video) }
        }
    }
    private companion object {
        const val AUTO_SCROLL_DELAY_MS = 5_000L

        fun bindBannerPoster(
            view: android.widget.ImageView,
            coverUrl: String,
        ) = bindPoster(view, coverUrl, isHighPriority = true, HomePosterSizePolicy::banner)

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
