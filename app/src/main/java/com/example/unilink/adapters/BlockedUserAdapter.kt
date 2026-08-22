package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.databinding.ItemLeaderboardBinding
import com.example.unilink.models.User
import com.example.unilink.repository.UserRepository

class BlockedUserAdapter(
    private val users: List<User>,
    private val onUnblockClick: (User) -> Unit
) : RecyclerView.Adapter<BlockedUserAdapter.BlockedViewHolder>() {

    class BlockedViewHolder(val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedViewHolder {
        val binding = ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BlockedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BlockedViewHolder, position: Int) {
        val user = users[position]
        holder.binding.tvRank.text = "🚫"
        holder.binding.tvName.text = user.name
        holder.binding.tvMajor.text = user.username
        holder.binding.tvXp.text = "Unblock"
        holder.binding.tvXp.setTextColor(android.graphics.Color.RED)
        
        holder.binding.tvXp.setOnClickListener { onUnblockClick(user) }
    }

    override fun getItemCount(): Int = users.size
}
