package com.aiyifan.app.core.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.core.model.FavoriteVideo
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.core.model.WatchHistory
import com.aiyifan.app.databinding.ItemVideoCardBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

class VideoListAdapter(
    private val onClick: (VideoSummary) -> Unit,
) : RecyclerView.Adapter<VideoListAdapter.VideoViewHolder>() {
    private val items = mutableListOf<VideoSummary>()

    fun submitList(videos: List<VideoSummary>) {
        items.clear()
        items.addAll(videos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VideoViewHolder(
        private val binding: ItemVideoCardBinding,
        private val onClick: (VideoSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoSummary) {
            if (video.coverUrl.isBlank()) {
                Glide.with(binding.poster).clear(binding.poster)
            } else {
                Glide.with(binding.poster)
                    .load(video.coverUrl)
                    .centerCrop()
                    .transform(RoundedCorners(binding.poster.resources.displayMetrics.density.times(8).toInt()))
                    .into(binding.poster)
            }
            binding.title.text = video.title
            binding.meta.text = listOfNotNull(video.year, video.area, video.updateStatus).joinToString(" / ")
            binding.score.text = "评分 ${video.score ?: "-"}  播放 ${video.playCount}"
            binding.root.setOnClickListener { onClick(video) }
            binding.playButton.setOnClickListener { onClick(video) }
        }
    }
}

fun WatchHistory.toVideoSummary(): VideoSummary =
    VideoSummary(
        mediaKey = mediaKey,
        title = title,
        coverUrl = coverUrl,
        videoType = videoType,
        updateStatus = "观看至 ${progressMs / 1000}s",
    )

fun FavoriteVideo.toVideoSummary(): VideoSummary =
    VideoSummary(
        mediaKey = mediaKey,
        title = title,
        coverUrl = coverUrl,
        videoType = videoType,
        updateStatus = "已收藏",
    )
