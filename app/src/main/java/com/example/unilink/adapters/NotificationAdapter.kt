package com.example.unilink.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.databinding.ItemNotificationBinding
import com.example.unilink.models.Notification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val onItemClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    class NotificationViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = getItem(position)
        holder.binding.tvTitle.text = notification.title
        holder.binding.tvMessage.text = notification.message
        
        holder.binding.vUnreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE
        holder.binding.tvTime.text = getRelativeTimeString(notification.timestamp)

        // Type-specific icons & colors
        val (iconRes, tintColor) = when (notification.type) {
            "vouch" -> Pair(android.R.drawable.btn_star_big_on, Color.parseColor("#FFD700"))
            "chat" -> Pair(android.R.drawable.ic_menu_send, Color.parseColor("#06B6D4"))
            "video_call" -> Pair(android.R.drawable.ic_menu_camera, Color.parseColor("#10B981"))
            "duel" -> Pair(android.R.drawable.ic_menu_today, Color.parseColor("#EC4899"))
            "token" -> Pair(android.R.drawable.ic_input_add, Color.parseColor("#F59E0B"))
            "system_update" -> Pair(android.R.drawable.ic_menu_upload, Color.parseColor("#A855F7"))
            else -> Pair(android.R.drawable.ic_popup_reminder, Color.parseColor("#06B6D4"))
        }

        holder.binding.ivIcon.setImageResource(iconRes)
        holder.binding.ivIcon.setColorFilter(tintColor)

        holder.itemView.setOnClickListener { onItemClick(notification) }
    }

    private fun getRelativeTimeString(date: Date?): String {
        if (date == null) return "Just now"
        val now = System.currentTimeMillis()
        val diff = (now - date.time).coerceAtLeast(0L)

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean = oldItem.notificationId == newItem.notificationId
        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean = oldItem == newItem
    }
}
