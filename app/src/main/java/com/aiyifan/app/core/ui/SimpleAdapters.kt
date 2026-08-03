package com.aiyifan.app.core.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.core.model.Comment
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.databinding.ItemCommentBinding
import com.aiyifan.app.databinding.ItemEpisodeBinding

class EpisodeAdapter(
    private val onClick: (Episode) -> Unit,
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {
    private val items = mutableListOf<Episode>()
    private var selectedKey: String? = null

    fun submitList(episodes: List<Episode>, selected: Episode?) {
        items.clear()
        items.addAll(episodes)
        selectedKey = selected?.episodeKey
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(items[position], items[position].episodeKey == selectedKey)
    }

    override fun getItemCount(): Int = items.size

    class EpisodeViewHolder(
        private val binding: ItemEpisodeBinding,
        private val onClick: (Episode) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(episode: Episode, selected: Boolean) {
            val appearance = ChipAppearanceResolver.forSelection(selected)
            binding.episodeTitle.text = episode.episodeTitle
            binding.episodeTitle.isSelected = selected
            binding.episodeTitle.setBackgroundResource(appearance.backgroundRes)
            binding.episodeTitle.setTextColor(
                binding.root.resources.getColor(appearance.textColorRes, null),
            )
            binding.root.alpha = 1.0f
            binding.root.setOnClickListener { onClick(episode) }
        }
    }
}

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {
    private val items = mutableListOf<Comment>()

    fun submitList(comments: List<Comment>) {
        items.clear()
        items.addAll(comments)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class CommentViewHolder(
        private val binding: ItemCommentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: Comment) {
            binding.user.text = "${comment.userName} · ${comment.postTime}"
            binding.content.text = "${comment.content}  赞 ${comment.likeCount}"
        }
    }
}
