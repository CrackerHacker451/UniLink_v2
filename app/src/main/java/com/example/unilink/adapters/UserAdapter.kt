package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.databinding.ItemUserBinding
import com.example.unilink.models.User
import com.example.unilink.utils.ImageLoaderUtils

class UserAdapter(
    private var users: List<User>,
    private val onItemClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.binding.tvUserName.text = user.name
        holder.binding.tvUserSubtitle.text = if (user.userType == "College Student") {
            "${user.collegeName} | ${user.branch}"
        } else {
            "${user.schoolName} | ${user.branch}"
        }
        holder.binding.tvUserTypeTag.text = user.userType.split(" ")[0]
        
        ImageLoaderUtils.loadSmartImage(
            imageView = holder.binding.ivUserImage,
            url = user.profileImageUrl,
            placeholderRes = R.drawable.ic_profile_placeholder,
            isCircle = true,
            fallbackName = user.name
        )

        if (user.isPremium) {
            holder.binding.ivUserImage.setBackgroundResource(R.drawable.bg_pro_pulse)
        } else {
            holder.binding.ivUserImage.setBackgroundResource(R.drawable.bg_tag)
        }
        
        holder.itemView.setOnClickListener { onItemClick(user) }
    }

    override fun getItemCount(): Int = users.size

    fun updateList(newList: List<User>) {
        users = newList
        notifyDataSetChanged()
    }
}
