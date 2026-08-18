package com.aiyifan.app.feature.localmedia

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.databinding.ItemLocalVideoBinding
import com.aiyifan.app.feature.localmedia.model.LocalVideo

class LocalMediaAdapter(
    private val onClick: (LocalVideo) -> Unit,
) : RecyclerView.Adapter<LocalMediaAdapter.VideoViewHolder>() {
    private val items = mutableListOf<LocalVideo>()

    fun submitList(videos: List<LocalVideo>) {
        items.clear()
        items.addAll(videos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemLocalVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VideoViewHolder(
        private val binding: ItemLocalVideoBinding,
        private val onClick: (LocalVideo) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: LocalVideo) {
            binding.localTitle.text = video.displayName
            binding.localMeta.text = buildMeta(video)
            binding.localPoster.setImageDrawable(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
                    binding.root.context.contentResolver.loadThumbnail(contentUri, android.util.Size(320, 320), null)
                }.onSuccess(binding.localPoster::setImageBitmap)
            }
            binding.root.setOnClickListener { onClick(video) }
            binding.localPlayButton.setOnClickListener { onClick(video) }
        }

        private fun buildMeta(video: LocalVideo): String {
            val duration = LocalMediaPresentation.formatDuration(video.durationMs)
            val size = LocalMediaPresentation.formatSize(video.sizeBytes)
            return listOf(duration, size).filter { it.isNotBlank() }.joinToString(" / ")
        }
    }
}