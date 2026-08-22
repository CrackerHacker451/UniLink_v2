package com.example.unilink.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
import com.example.unilink.databinding.*
import com.example.unilink.models.ChatMessage
import com.example.unilink.utils.ThemeUtils
import com.example.unilink.utils.EncryptionUtils
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private var messages: List<ChatMessage>,
    private val currentUserId: String,
    private val chatId: String,
    private val onMessageClick: (ChatMessage, View) -> Unit,
    private val onMessageDoubleTap: (ChatMessage) -> Unit,
    private val onPollVote: (ChatMessage, String) -> Unit,
    private val onDuelAccept: (ChatMessage) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_SENT = 0
        const val TYPE_RECEIVED = 1
        const val TYPE_POLL = 2
        const val TYPE_DOUBT = 3
        const val TYPE_TOKEN = 4
        const val TYPE_DUEL = 5
        const val TYPE_DATE_HEADER = 6
        const val TYPE_IMAGE = 7
    }

    private val displayList = mutableListOf<Any>()

    init {
        processMessages()
    }

    fun updateMessages(newMessages: List<ChatMessage>) {
        this.messages = newMessages
        processMessages()
        notifyDataSetChanged()
    }

    private fun processMessages() {
        displayList.clear()
        if (messages.isEmpty()) return

        var lastDate = ""
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

        messages.forEach { msg ->
            val msgDate = msg.timestampDate?.let { dateFormat.format(it) } ?: ""
            if (msgDate != lastDate) {
                displayList.add(msgDate)
                lastDate = msgDate
            }
            displayList.add(msg)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = displayList[position]
        if (item is String) return TYPE_DATE_HEADER
        
        val msg = item as ChatMessage
        return when (msg.type) {
            "poll" -> TYPE_POLL
            "doubt" -> TYPE_DOUBT
            "token" -> TYPE_TOKEN
            "duel" -> TYPE_DUEL
            "image" -> TYPE_IMAGE
            else -> if (msg.senderId == currentUserId) TYPE_SENT else TYPE_RECEIVED
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
        val item = displayList[position]
        
        if (holder is DateHeaderViewHolder) {
            val dateStr = item as String
            holder.binding.tvDateHeader.text = formatDateHeader(dateStr)
            return
        }

        val msg = item as ChatMessage
        
        // Decrypt message (E2EE)
        val decryptedText = EncryptionUtils.decrypt(msg.messageText, chatId)
        val decryptedReply = msg.replyToText?.let { EncryptionUtils.decrypt(it, chatId) }

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val time = msg.timestampDate?.let { timeFormat.format(it) } ?: ""

        if (holder !is DuelViewHolder) {
            setupCommonListeners(holder.itemView, msg)
        }

        val context = holder.itemView.context
        val currentTheme = ThemeUtils.getSelectedTheme(context)
        val isLightTheme = currentTheme == "Ars White" || currentTheme == "Classic theme"
        val isArsWhite = currentTheme == "Ars White"

        // Check if next message is from same sender (for grouping)
        val isLastInGroup = position + 1 == displayList.size || 
                           displayList[position + 1] is String || 
                           (displayList[position + 1] as ChatMessage).senderId != msg.senderId

        when (holder) {
            is SentViewHolder -> {
                holder.binding.tvMessage.text = decryptedText
                holder.binding.tvTimestamp.text = time
                
                if (!isLastInGroup) {
                    holder.binding.llMessageBubble.setBackgroundResource(R.drawable.bg_chat_bubble_sent_premium)
                }

                if (decryptedReply != null) {
                    holder.binding.llReplyQuote.visibility = View.VISIBLE
                    holder.binding.tvReplyText.text = decryptedReply
                    if (isLightTheme) {
                        holder.binding.llReplyQuote.setBackgroundColor(Color.parseColor(if (isArsWhite) "#F1F2F6" else "#E1F5FE"))
                        holder.binding.tvReplyText.setTextColor(Color.parseColor("#2D3436"))
                    } else {
                        holder.binding.llReplyQuote.setBackgroundColor(Color.parseColor("#1A000000"))
                        holder.binding.tvReplyText.setTextColor(Color.parseColor("#B0B0B0"))
                    }
                } else {
                    holder.binding.llReplyQuote.visibility = View.GONE
                }

                holder.binding.tvEdited.visibility = if (msg.isEdited) View.VISIBLE else View.GONE

                if (currentTheme == "Classic theme") {
                    holder.binding.llMessageBubble.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D9FDD3"))
                    holder.binding.tvMessage.setTextColor(Color.parseColor("#111B21"))
                    holder.binding.tvTimestamp.setTextColor(Color.parseColor("#667781"))
                    
                    // Standard check icons tinted for Classic theme
                    holder.binding.ivStatus.setImageResource(android.R.drawable.checkbox_on_background)
                    holder.binding.ivStatus.imageTintList = ColorStateList.valueOf(Color.parseColor(if (msg.status == "seen") "#34B7F1" else "#8696A0"))
                } else if (isArsWhite) {
                    holder.binding.llMessageBubble.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#007AFF"))
                    holder.binding.tvMessage.setTextColor(Color.WHITE)
                    holder.binding.tvTimestamp.setTextColor(Color.parseColor("#B3FFFFFF"))
                    holder.binding.ivStatus.imageTintList = ColorStateList.valueOf(Color.WHITE)
                } else {
                    // Transparent glass for Aura and other dark themes
                    holder.binding.llMessageBubble.backgroundTintList = null
                    holder.binding.tvMessage.setTextColor(Color.WHITE)
                    holder.binding.tvTimestamp.setTextColor(Color.parseColor("#B0FFFFFF"))
                    holder.binding.ivStatus.imageTintList = ColorStateList.valueOf(Color.WHITE)
                }

                if (currentTheme != "Classic theme") {
                    if (msg.status == "seen") {
                        holder.binding.ivStatus.setImageResource(android.R.drawable.presence_online)
                        holder.binding.ivStatus.imageTintList = ColorStateList.valueOf(Color.parseColor("#22D3EE"))
                    } else {
                        holder.binding.ivStatus.setImageResource(android.R.drawable.checkbox_on_background)
                        holder.binding.ivStatus.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                    }
                }

                renderReactions(holder.binding.llReactions, msg.reactions)
            }
            is ReceivedViewHolder -> {
                holder.binding.tvMessage.text = decryptedText
                holder.binding.tvTimestamp.text = time
                
                val isFirstInGroup = position == 0 || 
                                   displayList[position - 1] is String || 
                                   (displayList[position - 1] as ChatMessage).senderId != msg.senderId

                holder.binding.tvSenderUsername.text = msg.senderUsername
                holder.binding.tvSenderUsername.visibility = if (isFirstInGroup && msg.senderUsername.isNotEmpty()) View.VISIBLE else View.GONE
                
                // Show avatar for the first message in a group or always for received
                holder.binding.ivSenderAvatar.visibility = if (isFirstInGroup) View.VISIBLE else View.INVISIBLE
                
                // Load Avatar
                holder.binding.ivSenderAvatar.load(msg.senderAvatarUrl ?: R.drawable.ic_profile_placeholder) {
                    transformations(CircleCropTransformation())
                }

                if (decryptedReply != null) {
                    holder.binding.llReplyQuote.visibility = View.VISIBLE
                    holder.binding.tvReplyText.text = decryptedReply
                } else {
                    holder.binding.llReplyQuote.visibility = View.GONE
                }

                if (currentTheme == "Classic theme") {
                    holder.binding.llMessageBubble.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                    holder.binding.tvMessage.setTextColor(Color.parseColor("#111B21"))
                    holder.binding.tvSenderUsername.setTextColor(Color.parseColor("#075E54"))
                    holder.binding.tvTimestamp.setTextColor(Color.parseColor("#667781"))
                } else if (isArsWhite) {
                    holder.binding.llMessageBubble.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F2F6"))
                    holder.binding.tvMessage.setTextColor(Color.BLACK)
                    holder.binding.tvSenderUsername.setTextColor(Color.parseColor("#007AFF"))
                    holder.binding.tvTimestamp.setTextColor(Color.parseColor("#667781"))
                } else {
                    holder.binding.llMessageBubble.backgroundTintList = null
                    holder.binding.tvMessage.setTextColor(Color.WHITE)
                    holder.binding.tvSenderUsername.setTextColor(Color.parseColor("#22D3EE"))
                    holder.binding.tvTimestamp.setTextColor(Color.parseColor("#B0FFFFFF"))
                }

                holder.binding.tvEdited.visibility = if (msg.isEdited) View.VISIBLE else View.GONE
                renderReactions(holder.binding.llReactions, msg.reactions)
            }
            is PollViewHolder -> {
                holder.binding.tvPollQuestion.text = msg.pollQuestion
                renderPollOptions(holder.binding.pollOptionsContainer, msg)
            }
            is DoubtViewHolder -> {
                holder.binding.tvDoubtText.text = msg.messageText
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
                    holder.binding.ivMessageImage.load(msg.mediaUrl) {
                        crossfade(enable = true)
                        placeholder(R.drawable.bg_chat_img_placeholder)
                        error(R.drawable.bg_chat_img_placeholder)
                    }

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
                // Tactical Heartbeat
                com.example.unilink.utils.HapticUtils.playHeavy(view)
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
        val currentTheme = ThemeUtils.getSelectedTheme(context)
        val isLightTheme = currentTheme == "Ars White" || currentTheme == "Classic theme"

        reactions.forEach { (emoji, userIds) ->
            if (userIds.isNotEmpty()) {
                val tv = android.widget.TextView(context)
                tv.text = "$emoji ${userIds.size}"
                tv.textSize = 10f
                tv.setPadding(12, 4, 12, 4)
                tv.setTextColor(if (isLightTheme) Color.parseColor("#2D3436") else Color.WHITE)
                tv.background = context.getDrawable(R.drawable.bg_tag)
                
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
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
            
            // Adjust progress background width
            binding.optionProgress.post {
                val totalWidth = binding.root.width
                val progressWidth = (totalWidth * percent) / 100
                val params = binding.optionProgress.layoutParams
                params.width = progressWidth
                binding.optionProgress.layoutParams = params
            }
            
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            if (msg.userVotes[currentUid] == option) {
                binding.root.setBackgroundResource(R.drawable.bg_poll_option_selected)
            } else {
                binding.root.setBackgroundResource(R.drawable.bg_poll_option)
            }
            
            binding.root.setOnClickListener { onPollVote(msg, option) }
            container.addView(binding.root)
        }
    }

    override fun getItemCount(): Int = displayList.size

    fun getMessageAt(position: Int): ChatMessage? {
        return displayList.getOrNull(position) as? ChatMessage
    }

    class SentViewHolder(val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root)
    class ReceivedViewHolder(val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root)
    class PollViewHolder(val binding: ItemMessagePollBinding) : RecyclerView.ViewHolder(binding.root)
    class DoubtViewHolder(val binding: ItemMessageDoubtBinding) : RecyclerView.ViewHolder(binding.root)
    class TokenViewHolder(val binding: ItemMessageTokenBinding) : RecyclerView.ViewHolder(binding.root)
    class DuelViewHolder(val binding: ItemMessageDuelBinding) : RecyclerView.ViewHolder(binding.root)
    class DateHeaderViewHolder(val binding: ItemChatDateHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class ImageViewHolder(val binding: ItemMessageImageBinding) : RecyclerView.ViewHolder(binding.root)
}
