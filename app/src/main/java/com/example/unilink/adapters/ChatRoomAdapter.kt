package com.example.unilink.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.databinding.ItemChatRoomBinding
import com.example.unilink.models.ChatRoom
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.ThemeUtils
import coil.load
import coil.transform.CircleCropTransformation
import java.text.SimpleDateFormat
import java.util.Locale

class ChatRoomAdapter(
    private var chatRooms: List<ChatRoom>,
    private val currentUserId: String,
    private val onItemClick: (ChatRoom) -> Unit
) : RecyclerView.Adapter<ChatRoomAdapter.ChatRoomViewHolder>() {

    class ChatRoomViewHolder(val binding: ItemChatRoomBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        val binding = ItemChatRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatRoomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        val room = chatRooms[position]
        val context = holder.itemView.context
        val currentTheme = ThemeUtils.getSelectedTheme(context)
        
        if (room.isGroup) {
            holder.binding.tvChatName.text = room.groupName
            holder.binding.onlineStatusIndicator.visibility = android.view.View.GONE
            holder.binding.ivGroupIcon.load(R.drawable.ic_profile_placeholder) {
                transformations(CircleCropTransformation())
            }
        } else {
            val otherUserId = room.participants.firstOrNull { it != currentUserId }
            holder.binding.tvChatName.text = "Chat" 
            if (otherUserId != null) {
                UserRepository().getUserById(otherUserId) { user ->
                    if (holder.bindingAdapterPosition == position) {
                        holder.binding.tvChatName.text = user?.name ?: "User"
                        val isOnline = user?.isOnline == true
                        holder.binding.onlineStatusIndicator.visibility = if (isOnline) android.view.View.VISIBLE else android.view.View.GONE
                        
                        holder.binding.ivGroupIcon.load(user?.profileImageUrl ?: R.drawable.ic_profile_placeholder) {
                            transformations(CircleCropTransformation())
                        }

                        if (user?.isPremium == true) {
                            holder.binding.ivGroupIcon.setBackgroundResource(R.drawable.bg_pro_pulse)
                            holder.binding.tvChatName.setTextColor(Color.parseColor("#FACC15"))
                        } else {
                            holder.binding.ivGroupIcon.setBackgroundResource(R.drawable.bg_tag)
                            holder.binding.tvChatName.setTextColor(ThemeUtils.getTextPrimaryColor(context))
                        }

                        if (isOnline) {
                            val statusColor = when (user?.status) {
                                "Open to Pair" -> "#22C55E"
                                "Studying" -> "#22D3EE"
                                "Busy" -> "#EC4899"
                                "Open to Hire" -> "#8B5CF6"
                                else -> "#22C55E"
                            }
                            try {
                                holder.binding.onlineStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(statusColor))
                            } catch (e: Exception) {
                                holder.binding.onlineStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
                            }
                        }
                    }
                }
            }
        }
        
        // Decrypt last message if encrypted
        val decryptedLastMsg = com.example.unilink.utils.EncryptionUtils.decrypt(room.lastMessage, room.chatId)
        holder.binding.tvLastMessage.text = decryptedLastMsg
        
        // Bold last message if unread
        val unreadCount = room.unreadCounts[currentUserId] ?: 0
        if (unreadCount > 0) {
            holder.binding.tvLastMessage.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.binding.tvLastMessage.setTextColor(Color.parseColor(if (currentTheme.contains("White") || currentTheme.contains("Light")) "#111B21" else "#FFFFFF"))
        } else {
            holder.binding.tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        holder.binding.tvChatTime.text = room.timestampDate?.let { sdf.format(it) } ?: ""
        
        if (unreadCount > 0) {
            holder.binding.cardUnread.visibility = android.view.View.VISIBLE
            holder.binding.tvUnreadCount.text = unreadCount.toString()
        } else {
            holder.binding.cardUnread.visibility = android.view.View.GONE
        }
        
        holder.itemView.setOnClickListener { onItemClick(room) }
    }

    override fun getItemCount(): Int = chatRooms.size

    fun updateList(newList: List<ChatRoom>) {
        chatRooms = newList
        notifyDataSetChanged()
    }
}
