package com.example.unilink.adapters

import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.databinding.ItemStudentCardBinding
import com.example.unilink.models.User
import com.example.unilink.utils.ImageLoaderUtils

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

        holder.binding.tvStudentCardName.text = if (user.name.isNotEmpty()) user.name else if (user.username.isNotEmpty()) "@${user.username}" else "Campus Student"

        if (user.isPremium) {
            holder.binding.tvStudentCardName.setTextColor(Color.parseColor("#FACC15"))
            holder.binding.tvProBadge.visibility = View.VISIBLE
            holder.binding.ivStudentCardAvatar.setBackgroundResource(R.drawable.bg_pro_pulse)
        } else {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.textPrimaryColor, typedValue, true)
            holder.binding.tvStudentCardName.setTextColor(typedValue.data)
            holder.binding.tvProBadge.visibility = View.GONE
            holder.binding.ivStudentCardAvatar.setBackgroundResource(R.drawable.bg_tag)
        }

        val distText = if (user.distanceKm != null && user.distanceKm!! > 0.0) {
            String.format(java.util.Locale.US, "📍 %.1f km", user.distanceKm)
        } else if (!user.city.isNullOrEmpty()) {
            "📍 ${user.city}"
        } else {
            "📍 Campus"
        }
        holder.binding.tvDistanceTag.text = distText

        val majorText = when {
            !user.branch.isNullOrEmpty() -> user.branch
            !user.collegeName.isNullOrEmpty() -> user.collegeName
            user.email.contains("nl-lobby") -> "Newton's Lobby"
            user.email.contains("gmail") -> "Web Sync"
            else -> "Campus Innovator"
        }
        holder.binding.tvStudentCardMajor.text = majorText

        holder.binding.vStudentOnlineGlow.visibility = if (user.isOnline) View.VISIBLE else View.GONE

        ImageLoaderUtils.loadSmartImage(
            imageView = holder.binding.ivStudentCardAvatar,
            url = user.profileImageUrl,
            placeholderRes = R.drawable.ic_profile_placeholder,
            isCircle = true,
            fallbackName = user.name
        )

        val statusBadge = when {
            user.status.isNotEmpty() -> user.status
            user.email.contains("nl-lobby") -> "⚡ Newton Member"
            user.email.contains("gmail") -> "🌐 Web Profile"
            user.impactScore > 0 -> "⭐ Impact: ${user.impactScore}"
            else -> "Open to Link"
        }
        holder.binding.tvStudentStatus.text = statusBadge

        val skills = user.skills
        val skillSummary = when {
            !skills.isNullOrEmpty() -> skills.take(2).joinToString(" • ")
            user.bio.isNotEmpty() -> user.bio
            else -> "Networking • Synergy"
        }
        holder.binding.tvStudentSkills.text = skillSummary

        holder.itemView.setOnClickListener { onItemClick(user) }
        holder.binding.btnConnect.setOnClickListener { onItemClick(user) }

        holder.itemView.alpha = 0f
        holder.itemView.translationY = 30f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay((position % 6) * 50L)
            .setInterpolator(DecelerateInterpolator())
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
