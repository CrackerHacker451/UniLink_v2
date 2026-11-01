package com.example.unilink.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
import com.example.unilink.databinding.*
import com.example.unilink.models.ChatMessage
import com.example.unilink.utils.EncryptionUtils
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class ChatItem {
    data class DateHeader(val date: String) : ChatItem()
    data class Message(val message: ChatMessage) : ChatItem()
}

class ChatAdapter(
    private val currentUserId: String,
    private val chatId: String,
    private val onMessageClick: (ChatMessage, View) -> Unit,
    private val onMessageDoubleTap: (ChatMessage) -> Unit,
    private val onPollVote: (ChatMessage, String) -> Unit,
    private val onDuelAccept: (ChatMessage) -> Unit
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        const val TYPE_SENT = 0
        const val TYPE_RECEIVED = 1
        const val TYPE_POLL = 2
        const val TYPE_DOUBT = 3
        const val TYPE_TOKEN = 4
        const val TYPE_DUEL = 5
        const val TYPE_DATE_HEADER = 6
        const val TYPE_IMAGE = 7

        private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    }

    private val decryptionCache = mutableMapOf<String, String>()
    var dynamicThemeColor: String? = null

    fun updateMessages(newMessages: List<ChatMessage>, onCommitted: (() -> Unit)? = null) {
        val displayList = mutableListOf<ChatItem>()
        if (newMessages.isEmpty()) {
            submitList(displayList, onCommitted)
            return
        }

        var lastDate = ""

        newMessages.forEach { msg ->
            val msgDate = msg.timestampDate?.let { dateFormat.format(it) } ?: "Today"
            if (msgDate != lastDate) {
                displayList.add(ChatItem.DateHeader(msgDate))
                lastDate = msgDate
            }
            displayList.add(ChatItem.Message(msg))
        }
        submitList(displayList, onCommitted)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.DateHeader -> TYPE_DATE_HEADER
            is ChatItem.Message -> {
                val msg = item.message
                when (msg.type) {
                    "poll" -> TYPE_POLL
                    "doubt" -> TYPE_DOUBT
                    "token" -> TYPE_TOKEN
                    "duel" -> TYPE_DUEL
                    "image" -> TYPE_IMAGE
                    else -> if (msg.senderId == currentUserId) TYPE_SENT else TYPE_RECEIVED
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SENT -> SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
            TYPE_RECEIVED -> ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
            TYPE_POLL -> PollViewHolder(ItemMessagePollBinding.inflate(inflater, parent, false))
            TYPE_DOUBT -> DoubtViewHolder(ItemMessageDoubtBinding.inflate(inflater, parent, false))
            TYPE_TOKEN -> TokenViewHolder(ItemMessageTokenBinding.inflate(inflater, parent, false))
            TYPE_DUEL -> DuelViewHolder(ItemMessageDuelBinding.inflate(inflater, parent, false))
            TYPE_DATE_HEADER -> DateHeaderViewHolder(ItemChatDateHeaderBinding.inflate(inflater, parent, false))
            TYPE_IMAGE -> ImageViewHolder(ItemMessageImageBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        
        if (holder is DateHeaderViewHolder && item is ChatItem.DateHeader) {
            holder.binding.tvDateHeader.text = formatDateHeader(item.date)
            return
        }

        if (item !is ChatItem.Message) return
        val msg = item.message
        
        val cacheKey = "${msg.messageId}_${msg.messageText.hashCode()}"
        val decryptedText = decryptionCache.getOrPut(cacheKey) {
            if (msg.webText != null) msg.webText!! 
            else EncryptionUtils.decrypt(msg.messageText, chatId)
        }
        
        val decryptedReply = msg.replyToText?.let { reply ->
            val replyCacheKey = "${msg.messageId}_reply_${reply.hashCode()}"
            decryptionCache.getOrPut(replyCacheKey) {
                if (msg.webText != null) reply 
                else EncryptionUtils.decrypt(reply, chatId)
            }
        }

        val time = msg.timestampDate?.let { timeFormat.format(it) } ?: "Just now"

        if (holder !is DuelViewHolder) {
            setupCommonListeners(holder.itemView, msg)
        }

        val context = holder.itemView.context

        when (holder) {
            is SentViewHolder -> {
                holder.binding.tvMessage.text = decryptedText
                holder.binding.tvTimestamp.text = time

                if (!dynamicThemeColor.isNullOrEmpty()) {
                    try {
                        val tintColor = Color.parseColor(dynamicThemeColor)
                        holder.binding.llMessageBubble.backgroundTintList = ColorStateList.valueOf(tintColor)
                    } catch (_: Exception) {
                        holder.binding.llMessageBubble.backgroundTintList = null
                    }
                } else {
                    holder.binding.llMessageBubble.backgroundTintList = null
                }
                
                if (decryptedReply != null) {
                    holder.binding.llReplyQuote.visibility = View.VISIBLE
                    holder.binding.tvReplyText.text = decryptedReply
                } else {
                    holder.binding.llReplyQuote.visibility = View.GONE
                }

                holder.binding.tvEdited.visibility = if (msg.isEdited) View.VISIBLE else View.GONE

                if (msg.status == "seen") {
                    holder.binding.tvReadStatus.text = "• Seen"
                    holder.binding.tvReadStatus.setTextColor(Color.parseColor("#22D3EE"))
                } else {
                    holder.binding.tvReadStatus.text = "• Sent"
                    holder.binding.tvReadStatus.setTextColor(Color.parseColor("#94A3B8"))
                }

                renderReactions(holder.binding.llReactions, msg.reactions)
            }
            is ReceivedViewHolder -> {
                holder.binding.tvMessage.text = decryptedText
                holder.binding.tvTimestamp.text = time
                
                val prevItem = if (position > 0) getItem(position - 1) else null
                val isFirstInGroup = prevItem !is ChatItem.Message || prevItem.message.senderId != msg.senderId

                holder.binding.tvSenderUsername.text = msg.displaySenderName
                holder.binding.tvSenderUsername.visibility = if (isFirstInGroup && msg.displaySenderName.isNotEmpty()) View.VISIBLE else View.GONE
                
                holder.binding.ivSenderAvatar.visibility = if (isFirstInGroup) View.VISIBLE else View.INVISIBLE
                holder.binding.ivSenderAvatar.load(msg.senderAvatarUrl ?: R.drawable.ic_profile_placeholder) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }

                if (decryptedReply != null) {
                    holder.binding.llReplyQuote.visibility = View.VISIBLE
                    holder.binding.tvReplyText.text = decryptedReply
                } else {
                    holder.binding.llReplyQuote.visibility = View.GONE
                }

                holder.binding.tvEdited.visibility = if (msg.isEdited) View.VISIBLE else View.GONE
                renderReactions(holder.binding.llReactions, msg.reactions)
            }
            is PollViewHolder -> {
                holder.binding.tvPollQuestion.text = msg.pollQuestion
                renderPollOptions(holder.binding.pollOptionsContainer, msg)
            }
            is DoubtViewHolder -> {
                holder.binding.tvDoubtText.text = msg.displayMessageText
                holder.binding.tvTokenReward.text = context.getString(R.string.token_reward_format, msg.doubtReward ?: 0)
                holder.binding.btnAnswer.setOnClickListener { }
            }
            is TokenViewHolder -> {
                holder.binding.tvTokenAmount.text = "${msg.uniCoinAmount ?: 0} UNICOINS"
                holder.binding.tvTokenNote.text = msg.tokenReason ?: "Gift"
            }
            is DuelViewHolder -> {
                holder.binding.tvDuelSubject.text = msg.duelSubject ?: "General Knowledge"
                holder.binding.tvDuelWager.text = context.getString(R.string.duel_wager_format, msg.duelWager ?: 0)
                
                when (msg.duelStatus) {
                    "finished" -> {
                        holder.binding.btnAcceptDuel.text = "Challenge Finished"
                        holder.binding.btnAcceptDuel.isEnabled = false
                    }
                    "active" -> {
                        holder.binding.btnAcceptDuel.text = "Duel in Progress"
                        holder.binding.btnAcceptDuel.isEnabled = false
                    }
                    else -> {
                        if (msg.senderId == currentUserId) {
                            holder.binding.btnAcceptDuel.text = context.getString(R.string.waiting_for_partner)
                            holder.binding.btnAcceptDuel.isEnabled = false
                        } else {
                            holder.binding.btnAcceptDuel.text = context.getString(R.string.accept_challenge)
                            holder.binding.btnAcceptDuel.isEnabled = true
                            holder.binding.btnAcceptDuel.setOnClickListener { onDuelAccept(msg) }
                        }
                    }
                }
            }
            is ImageViewHolder -> {
                holder.binding.tvTimestamp.text = time
                val gravity = if (msg.senderId == currentUserId) android.view.Gravity.END else android.view.Gravity.START
                holder.binding.llImageRoot.gravity = gravity
                
                if (!msg.mediaUrl.isNullOrEmpty()) {
                    com.example.unilink.utils.ImageLoaderUtils.loadSmartImage(
                        holder.binding.ivMessageImage,
                        msg.mediaUrl,
                        R.drawable.bg_chat_img_placeholder
                    )

                    holder.binding.ivMessageImage.setOnClickListener {
                        val intent = android.content.Intent(context, com.example.unilink.activities.MediaViewerActivity::class.java).apply {
                            putExtra("mediaUrl", msg.mediaUrl)
                            putExtra("title", "Image from ${msg.senderUsername}")
                            putExtra("subtitle", time)
                        }
                        context.startActivity(intent)
                    }
                }
            }
        }
    }

    private fun formatDateHeader(dateStr: String): String {
        val today = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
        val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }.time
        val yesterdayStr = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(yesterday)

        return when (dateStr) {
            today -> "Today"
            yesterdayStr -> "Yesterday"
            else -> dateStr
        }
    }

    private fun setupCommonListeners(view: View, msg: ChatMessage) {
        val detector = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onMessageClick(msg, view)
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                onMessageDoubleTap(msg)
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                HapticUtils.playHeavy(view)
                onMessageClick(msg, view)
            }
        })

        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                v.performClick()
            }
            detector.onTouchEvent(event)
            true
        }
    }

    private fun renderReactions(container: LinearLayout, reactions: Map<String, List<String>>) {
        if (reactions.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        container.removeAllViews()
        
        val context = container.context
        val isLightTheme = ThemeUtils.getSelectedTheme(context) == "Light"

        reactions.forEach { (emoji, userIds) ->
            if (userIds.isNotEmpty()) {
                val tv = TextView(context)
                tv.text = "$emoji ${userIds.size}"
                tv.textSize = 10f
                tv.setPadding(12, 4, 12, 4)
                tv.setTextColor(if (isLightTheme) Color.parseColor("#2D3436") else Color.WHITE)
                tv.background = context.getDrawable(R.drawable.bg_tag)
                
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 8, 0)
                tv.layoutParams = params
                container.addView(tv)
            }
        }
    }

    private fun renderPollOptions(container: LinearLayout, msg: ChatMessage) {
        container.removeAllViews()
        val context = container.context
        val totalVotes = msg.pollOptions.values.sum()
        
        msg.pollOptions.forEach { (option, count) ->
            val binding = ItemPollOptionBinding.inflate(LayoutInflater.from(context), container, false)
            binding.tvOptionText.text = option
            val percent = if (totalVotes > 0) (count.toFloat() / totalVotes * 100).toInt() else 0
            binding.tvOptionPercent.text = "$percent%"
            
            binding.optionProgress.post {
                val params = binding.optionProgress.layoutParams
                params.width = (binding.root.width * percent) / 100
                binding.optionProgress.layoutParams = params
            }
            
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            binding.root.setBackgroundResource(if (msg.userVotes[currentUid] == option) R.drawable.bg_poll_option_selected else R.drawable.bg_poll_option)
            binding.root.setOnClickListener { onPollVote(msg, option) }
            container.addView(binding.root)
        }
    }

    fun getMessageAt(position: Int): ChatMessage? {
        return (getItem(position) as? ChatItem.Message)?.message
    }

    class SentViewHolder(val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root)
    class ReceivedViewHolder(val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root)
    class PollViewHolder(val binding: ItemMessagePollBinding) : RecyclerView.ViewHolder(binding.root)
    class DoubtViewHolder(val binding: ItemMessageDoubtBinding) : RecyclerView.ViewHolder(binding.root)
    class TokenViewHolder(val binding: ItemMessageTokenBinding) : RecyclerView.ViewHolder(binding.root)
    class DuelViewHolder(val binding: ItemMessageDuelBinding) : RecyclerView.ViewHolder(binding.root)
    class DateHeaderViewHolder(val binding: ItemChatDateHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class ImageViewHolder(val binding: ItemMessageImageBinding) : RecyclerView.ViewHolder(binding.root)

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return when {
                oldItem is ChatItem.DateHeader && newItem is ChatItem.DateHeader -> oldItem.date == newItem.date
                oldItem is ChatItem.Message && newItem is ChatItem.Message -> oldItem.message.messageId == newItem.message.messageId
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return oldItem == newItem
        }
    }
}
