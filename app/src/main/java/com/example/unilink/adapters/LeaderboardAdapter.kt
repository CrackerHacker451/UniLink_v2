package com.example.unilink.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.databinding.ItemLeaderboardBinding
import com.example.unilink.models.User

import coil.load
import coil.transform.CircleCropTransformation

class LeaderboardAdapter(
    private val users: List<User>,
    private val isTrustMode: Boolean = false,
    private val onItemClick: (User) -> Unit
) : RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {

    class LeaderboardViewHolder(val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val binding = ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val user = users[position]
        val context = holder.itemView.context
        
        holder.binding.tvRank.text = (position + 1).toString()
        holder.binding.tvName.text = user.name
        holder.binding.tvMajor.text = user.branch ?: user.userType
        
        if (isTrustMode) {
            holder.binding.tvXp.text = "⭐ ${user.vouchCount}"
        } else {
            holder.binding.tvXp.text = "${user.totalXp} XP"
        }
        
        holder.binding.ivAvatar.load(user.profileImageUrl ?: R.drawable.ic_profile_placeholder) {
            transformations(CircleCropTransformation())
            placeholder(R.drawable.ic_profile_placeholder)
            error(R.drawable.ic_profile_placeholder)
        }

        if (user.isPremium) {
            holder.binding.ivAvatar.setBackgroundResource(R.drawable.bg_pro_pulse)
            holder.binding.tvName.setTextColor(Color.parseColor("#FFD700"))
        } else {
            holder.binding.ivAvatar.setBackgroundResource(R.drawable.bg_tag)
            // Reset text color to default
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(R.attr.textPrimaryColor, typedValue, true)
            holder.binding.tvName.setTextColor(typedValue.data)
        }
        
        holder.itemView.setOnClickListener { onItemClick(user) }
    }

    override fun getItemCount(): Int = users.size
}
