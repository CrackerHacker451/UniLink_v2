package com.example.unilink.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.databinding.ItemFocusRoomCardBinding
import com.example.unilink.models.FocusRoom

import android.content.Intent
import com.example.unilink.activities.FocusRoomActivity

class FocusRoomAdapter(private val rooms: List<FocusRoom>) : RecyclerView.Adapter<FocusRoomAdapter.FocusViewHolder>() {

    class FocusViewHolder(val binding: ItemFocusRoomCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FocusViewHolder {
        val binding = ItemFocusRoomCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FocusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FocusViewHolder, position: Int) {
        val room = rooms[position]
        holder.binding.tvRoomName.text = room.name
        holder.binding.tvRoomName.setTextColor(Color.parseColor(room.color))
        
        // Mock studying count for visual improvement
        val count = room.currentParticipants.takeIf { it > 0 } ?: (5..45).random()
        holder.binding.tvStudyingCount.text = holder.itemView.context.getString(com.example.unilink.R.string.studying_count_format, count)
        
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, FocusRoomActivity::class.java).apply {
                putExtra("roomId", room.id)
                putExtra("roomName", room.name)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = rooms.size
}
