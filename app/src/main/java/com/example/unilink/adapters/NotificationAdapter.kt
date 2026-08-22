package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.databinding.ItemNotificationBinding
import com.example.unilink.models.Notification
import java.text.SimpleDateFormat
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
        
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        holder.binding.tvTime.text = notification.timestamp?.let { sdf.format(it) } ?: ""

        // Set icon based on type
        val iconRes = when (notification.type) {
            "vouch" -> android.R.drawable.btn_star_big_on
            "project_join" -> android.R.drawable.ic_menu_add
            "mention" -> android.R.drawable.ic_menu_info_details
            else -> android.R.drawable.ic_popup_reminder
        }
        holder.binding.ivIcon.setImageResource(iconRes)

        holder.itemView.setOnClickListener { onItemClick(notification) }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean = oldItem.notificationId == newItem.notificationId
        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean = oldItem == newItem
    }
}
