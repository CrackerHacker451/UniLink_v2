package com.example.unilink.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.databinding.ItemLeaderboardBinding
import com.example.unilink.models.User
import com.example.unilink.utils.ImageLoaderUtils

class LeaderboardAdapter(
    private val users: List<User>,
    private val startRankOffset: Int = 4,
    private val filterType: String = "XP",
    private val currentUserId: String? = null,
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
        val rankNumber = startRankOffset + position

        holder.binding.tvRank.text = "#$rankNumber"
        holder.binding.tvRank.setTextColor(
            when (rankNumber) {
                1 -> Color.parseColor("#FFD700")
                2 -> Color.parseColor("#C0C0C0")
                3 -> Color.parseColor("#CD7F32")
                else -> Color.parseColor("#22D3EE")
            }
        )

        val isSelf = (currentUserId != null && user.uid == currentUserId)
        if (isSelf) {
            holder.binding.tvName.text = "${user.name.ifEmpty { "Student" }} (You) 🌟"
            holder.binding.tvName.setTypeface(null, Typeface.BOLD)
            holder.itemView.setBackgroundResource(R.drawable.bg_tag)
        } else {
            holder.binding.tvName.text = user.name.ifEmpty { "Student" }
            holder.binding.tvName.setTypeface(null, Typeface.NORMAL)
        }

        holder.binding.tvMajor.text = user.branch?.ifEmpty { user.userType } ?: user.userType

        holder.binding.tvXp.text = when (filterType) {
            "Trust" -> "⭐ ${user.vouchCount}"
            "Coins" -> "💰 ${user.uniCoins}"
            else -> "⚡ ${user.totalXp} XP"
        }

        ImageLoaderUtils.loadSmartImage(
            holder.binding.ivAvatar,
            user.profileImageUrl,
            R.drawable.ic_profile_placeholder,
            isCircle = true,
            fallbackName = user.name
        )

        if (user.isPremium) {
            holder.binding.tvName.setTextColor(Color.parseColor("#FFD700"))
        } else {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(R.attr.textPrimaryColor, typedValue, true)
            holder.binding.tvName.setTextColor(typedValue.data)
        }

        holder.itemView.setOnClickListener { onItemClick(user) }
    }

    override fun getItemCount(): Int = users.size
}
