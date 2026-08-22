package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.databinding.ItemHighlightCardBinding
import com.example.unilink.R

data class CampusHighlight(val userId: String? = null, val name: String, val action: String, val isPro: Boolean = false)

class HighlightAdapter(
    private val highlights: List<CampusHighlight>,
    private val onItemClick: (CampusHighlight) -> Unit
) : RecyclerView.Adapter<HighlightAdapter.HighlightViewHolder>() {

    class HighlightViewHolder(val binding: ItemHighlightCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HighlightViewHolder {
        val binding = ItemHighlightCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HighlightViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HighlightViewHolder, position: Int) {
        val item = highlights[position]
        holder.binding.tvHighlightName.text = item.name
        holder.binding.tvHighlightAction.text = item.action
        
        if (item.isPro) {
            holder.binding.ivHighlightAvatar.setBackgroundResource(R.drawable.bg_pro_pulse)
        } else {
            holder.binding.ivHighlightAvatar.setBackgroundResource(R.drawable.bg_tag)
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = highlights.size
}
