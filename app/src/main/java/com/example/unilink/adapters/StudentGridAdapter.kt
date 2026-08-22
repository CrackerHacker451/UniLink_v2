package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.databinding.ItemStudentCardBinding
import com.example.unilink.models.User

import coil.load
import coil.transform.CircleCropTransformation

class StudentGridAdapter(
    private val onItemClick: (User) -> Unit
) : ListAdapter<User, StudentGridAdapter.GridViewHolder>(UserDiffCallback()) {

    class GridViewHolder(val binding: ItemStudentCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val binding = ItemStudentCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val user = getItem(position)
        val context = holder.itemView.context
        holder.binding.tvStudentCardName.text = user.name
        holder.binding.tvStudentCardMajor.text = user.branch ?: "Student"
        
        // Show online status glow
        holder.binding.vStudentOnlineGlow.visibility = if (user.isOnline) View.VISIBLE else View.GONE
        
        // Indicate Pro users with a different name color
        if (user.isPremium) {
            holder.binding.tvStudentCardName.setTextColor(android.graphics.Color.parseColor("#FACC15"))
        } else {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(R.attr.textPrimaryColor, typedValue, true)
            holder.binding.tvStudentCardName.setTextColor(typedValue.data)
        }

        // Load Avatar with Coil
        holder.binding.ivStudentCardAvatar.load(user.profileImageUrl ?: R.drawable.ic_profile_placeholder) {
            transformations(CircleCropTransformation())
            placeholder(R.drawable.ic_profile_placeholder)
            error(R.drawable.ic_profile_placeholder)
        }
        
        if (user.isPremium) {
            holder.binding.ivStudentCardAvatar.setBackgroundResource(R.drawable.bg_pro_pulse)
        } else {
            holder.binding.ivStudentCardAvatar.setBackgroundResource(R.drawable.bg_tag)
        }
        
        holder.binding.tvStudentStatus.text = user.status
        val skills = user.skills
        holder.binding.tvStudentSkills.text = if (!skills.isNullOrEmpty()) skills.take(2).joinToString(" • ") else "Networking"
        
        holder.itemView.setOnClickListener { onItemClick(user) }
        holder.binding.btnConnect.setOnClickListener { onItemClick(user) }

        // Subtle floating entry animation
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 40f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450)
            .setStartDelay((position % 6) * 60L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}
