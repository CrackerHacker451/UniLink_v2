package com.example.unilink.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.databinding.ItemSkillBinding

data class SkillModel(
    val name: String,
    val vouchCount: Int,
    val hasVouched: Boolean,
    val isOwnProfile: Boolean
)

class SkillAdapter(
    private val onVouchClick: (String) -> Unit
) : ListAdapter<SkillModel, SkillAdapter.SkillViewHolder>(SkillDiffCallback()) {

    class SkillViewHolder(val binding: ItemSkillBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkillViewHolder {
        val binding = ItemSkillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SkillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SkillViewHolder, position: Int) {
        val skill = getItem(position)
        holder.binding.tvSkillName.text = skill.name
        
        val isGold = skill.vouchCount >= 10
        holder.binding.skillProgress.progress = (skill.vouchCount * 10).coerceAtMost(100)
        holder.binding.skillProgress.setIndicatorColor(Color.parseColor(if (isGold) "#FACC15" else "#22D3EE"))

        if (skill.isOwnProfile) {
            holder.binding.btnVouch.visibility = View.GONE
            holder.binding.tvVouchCount.visibility = View.VISIBLE
            holder.binding.tvVouchCount.text = "⭐ ${skill.vouchCount}"
            holder.binding.tvVouchCount.setTextColor(Color.parseColor(if (isGold) "#FACC15" else "#94A3B8"))
        } else {
            holder.binding.btnVouch.visibility = View.VISIBLE
            holder.binding.tvVouchCount.visibility = View.GONE
            holder.binding.btnVouch.text = if (skill.hasVouched) "Vouched" else "Vouch"
            holder.binding.btnVouch.isEnabled = !skill.hasVouched
            holder.binding.btnVouch.setTextColor(Color.parseColor(if (skill.hasVouched) "#22C55E" else "#22D3EE"))
            
            holder.binding.btnVouch.setOnClickListener {
                onVouchClick(skill.name)
            }
        }
    }

    class SkillDiffCallback : DiffUtil.ItemCallback<SkillModel>() {
        override fun areItemsTheSame(oldItem: SkillModel, newItem: SkillModel): Boolean = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: SkillModel, newItem: SkillModel): Boolean = oldItem == newItem
    }
}
