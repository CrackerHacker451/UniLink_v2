package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.databinding.ItemMessageReceivedBinding
import com.example.unilink.databinding.ItemMessageSentBinding

data class AiMessage(val text: String, val isUser: Boolean)

class AiChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<AiMessage>()

    fun addMessage(msg: AiMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int = if (messages[position].isUser) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
        } else {
            ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is SentViewHolder) {
            holder.binding.tvMessage.text = msg.text
            holder.binding.tvTimestamp.visibility = android.view.View.GONE
            holder.binding.ivStatus.visibility = android.view.View.GONE
        } else if (holder is ReceivedViewHolder) {
            holder.binding.tvMessage.text = msg.text
            holder.binding.tvTimestamp.visibility = android.view.View.GONE
            holder.binding.tvSenderUsername.text = "UniAI"
            holder.binding.tvSenderUsername.visibility = android.view.View.VISIBLE
        }
    }

    override fun getItemCount(): Int = messages.size

    class SentViewHolder(val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root)
    class ReceivedViewHolder(val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root)
}
