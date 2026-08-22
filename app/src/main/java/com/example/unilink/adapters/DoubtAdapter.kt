package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.databinding.ItemDoubtBinding
import com.example.unilink.models.Doubt

class DoubtAdapter(
    private val onAnswerClick: (Doubt) -> Unit
) : ListAdapter<Doubt, DoubtAdapter.DoubtViewHolder>(DoubtDiffCallback()) {

    class DoubtViewHolder(val binding: ItemDoubtBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoubtViewHolder {
        val binding = ItemDoubtBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DoubtViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DoubtViewHolder, position: Int) {
        val doubt = getItem(position)
        
        holder.binding.tvUserName.text = doubt.userName
        holder.binding.tvQuestion.text = doubt.question
        holder.binding.tvRewardCoins.text = doubt.rewardCoins.toString()
        holder.binding.tvAnswerCount.text = holder.itemView.context.getString(android.R.string.ok) + " ${doubt.answerCount} answers"
        
        // Load User Avatar
        holder.binding.ivUserAvatar.load(doubt.userAvatarUrl ?: com.example.unilink.R.drawable.ic_profile_placeholder) {
            transformations(CircleCropTransformation())
            placeholder(com.example.unilink.R.drawable.ic_profile_placeholder)
            error(com.example.unilink.R.drawable.ic_profile_placeholder)
        }

        holder.binding.viewOnlineStatus.visibility = if (doubt.isOnline) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.viewOnlineStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (doubt.isOnline) "#22C55E" else "#94A3B8")
        )

        // Tags
        if (doubt.tags.isNotEmpty()) {
            holder.binding.tvTag1.text = doubt.tags[0]
            holder.binding.tvTag1.visibility = android.view.View.VISIBLE
            if (doubt.tags.size > 1) {
                holder.binding.tvTag2.text = doubt.tags[1]
                holder.binding.tvTag2.visibility = android.view.View.VISIBLE
            } else {
                holder.binding.tvTag2.visibility = android.view.View.GONE
            }
        } else {
            holder.binding.tvTag1.visibility = android.view.View.GONE
            holder.binding.tvTag2.visibility = android.view.View.GONE
        }

        holder.binding.btnAnswer.setOnClickListener {
            onAnswerClick(doubt)
        }

        // Subtle entry animation
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 40f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setStartDelay(position.toLong() * 40)
            .start()
    }

    class DoubtDiffCallback : DiffUtil.ItemCallback<Doubt>() {
        override fun areItemsTheSame(oldItem: Doubt, newItem: Doubt): Boolean = oldItem.doubtId == newItem.doubtId
        override fun areContentsTheSame(oldItem: Doubt, newItem: Doubt): Boolean = oldItem == newItem
    }
}
