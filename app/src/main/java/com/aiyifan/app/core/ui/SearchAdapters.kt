package com.aiyifan.app.core.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.core.model.SearchSuggestion
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.databinding.ItemSearchResultBinding
import com.aiyifan.app.databinding.ItemSearchSuggestionBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

class SearchSuggestionAdapter(
    private val onClick: (SearchSuggestion) -> Unit,
) : RecyclerView.Adapter<SearchSuggestionAdapter.SuggestionViewHolder>() {
    private val items = mutableListOf<SearchSuggestion>()

    fun submitList(suggestions: List<SearchSuggestion>) {
        items.clear()
        items.addAll(suggestions)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder =
        SuggestionViewHolder(ItemSearchSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class SuggestionViewHolder(
        private val binding: ItemSearchSuggestionBinding,
        private val onClick: (SearchSuggestion) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchSuggestion) {
            binding.suggestionText.text = item.keyword
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}

class SearchResultAdapter(
    private val onClick: (VideoSummary) -> Unit,
) : RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder>() {
    private val items = mutableListOf<VideoSummary>()

    fun submitList(results: List<VideoSummary>) {
        items.clear()
        items.addAll(results)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder =
        ResultViewHolder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class ResultViewHolder(
        private val binding: ItemSearchResultBinding,
        private val onClick: (VideoSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VideoSummary) {
            val presentation = SearchResultPresentation.from(item)

            binding.title.text = item.title
            binding.primaryMeta.setOptionalText(presentation.primaryMeta)
            binding.secondaryMeta.setOptionalText(presentation.secondaryMeta)
            binding.credits.setOptionalText(presentation.credits)
            binding.episodePreviewRow.isVisible = presentation.showEpisodePreviews
            binding.episodePreviewContainer.removeAllViews()
            presentation.episodeLabels.forEach { label ->
                binding.episodePreviewContainer.addView(
                    TextView(binding.root.context).apply {
                        text = label
                        gravity = Gravity.CENTER
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        textSize = 12f
                        setBackgroundResource(R.drawable.bg_episode_preview)
                        setOnClickListener { onClick(item) }
                    },
                    LinearLayout.LayoutParams(dpToPx(40), dpToPx(32)).apply {
                        marginEnd = dpToPx(6)
                    },
                )
            }
            if (item.coverUrl.isBlank()) {
                binding.poster.setImageDrawable(null)
            } else {
                Glide.with(binding.poster)
                    .load(item.coverUrl)
                    .centerCrop()
                    .transform(RoundedCorners(binding.poster.resources.displayMetrics.density.times(8).toInt()))
                    .into(binding.poster)
            }
            binding.root.setOnClickListener { onClick(item) }
            binding.playButton.setOnClickListener { onClick(item) }
            binding.viewAllButton.setOnClickListener { onClick(item) }
        }

        private fun TextView.setOptionalText(value: String) {
            isVisible = value.isNotBlank()
            text = value
        }

        private fun dpToPx(value: Int): Int =
            (value * binding.root.resources.displayMetrics.density).toInt()
    }
}
