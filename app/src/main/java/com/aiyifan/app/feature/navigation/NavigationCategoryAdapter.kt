package com.aiyifan.app.feature.navigation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiyifan.app.R
import com.aiyifan.app.databinding.ItemNavigationCategoryBinding

class NavigationCategoryAdapter(
    private val rail: Boolean = false,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<NavigationCategoryAdapter.ViewHolder>() {
    private var names: List<String> = emptyList()
    private var selected = -1
    fun submitList(names: List<String>, selected: Int = -1) {
        this.names = names
        this.selected = selected
        notifyDataSetChanged()
    }
    override fun getItemCount() = names.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemNavigationCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.root.apply {
            text = names[position]
            isSelected = position == selected
            layoutParams.height = resources.getDimensionPixelSize(if (rail) R.dimen.dp_44 else R.dimen.dp_52)
            setBackgroundResource(if (rail) R.drawable.bg_navigation_category else android.R.color.transparent)
            setTextColor(context.getColor(if (isSelected) R.color.navigation_accent else if (rail) R.color.text_secondary else R.color.text_primary))
            setOnClickListener { onClick(position) }
        }
    }
    class ViewHolder(val binding: ItemNavigationCategoryBinding) : RecyclerView.ViewHolder(binding.root)
}
