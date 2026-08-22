package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
import com.example.unilink.databinding.ItemUserAvatarBinding
import com.example.unilink.models.User

class VisitorAvatarAdapter(
    private val visitors: List<User>,
    private val onClick: (User) -> Unit
) : RecyclerView.Adapter<VisitorAvatarAdapter.VisitorViewHolder>() {

    class VisitorViewHolder(val binding: ItemUserAvatarBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VisitorViewHolder {
        val binding = ItemUserAvatarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VisitorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VisitorViewHolder, position: Int) {
        val user = visitors[position]
        holder.binding.ivAvatar.load(user.profileImageUrl ?: R.drawable.ic_profile_placeholder) {
            transformations(CircleCropTransformation())
            placeholder(R.drawable.ic_profile_placeholder)
        }
        
        if (user.isPremium) {
            holder.binding.ivAvatar.setBackgroundResource(R.drawable.bg_pro_pulse)
        } else {
            holder.binding.ivAvatar.setBackgroundResource(R.drawable.bg_tag)
        }

        holder.itemView.setOnClickListener { onClick(user) }
    }

    override fun getItemCount(): Int = visitors.size
}
