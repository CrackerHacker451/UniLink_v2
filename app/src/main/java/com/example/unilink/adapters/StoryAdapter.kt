package com.example.unilink.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
import com.example.unilink.activities.ProfileActivity
import com.example.unilink.databinding.ItemStoryBinding
import com.example.unilink.models.Story

class StoryAdapter(private val stories: List<Story>) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    class StoryViewHolder(val binding: ItemStoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val binding = ItemStoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        val story = stories[position]
        holder.binding.tvStoryName.text = if (story.userId == "1") "My Story" else story.userName
        
        if (story.userId == "1") {
            holder.binding.ivAddStory.visibility = android.view.View.VISIBLE
            holder.binding.ivStoryAvatar.load(R.drawable.ic_profile_placeholder) {
                transformations(CircleCropTransformation())
            }
            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, com.example.unilink.activities.CreatePostActivity::class.java)
                intent.putExtra("type", "story")
                holder.itemView.context.startActivity(intent)
            }
        } else {
            holder.binding.ivAddStory.visibility = android.view.View.GONE
            
            // Load user avatar or latest story image as thumbnail
            holder.binding.ivStoryAvatar.load(story.userAvatarUrl ?: R.drawable.ic_profile_placeholder) {
                transformations(CircleCropTransformation())
                crossfade(true)
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, com.example.unilink.activities.StoryViewActivity::class.java).apply {
                    putExtra("userId", story.userId)
                    putExtra("userName", story.userName)
                    putExtra("imageUrl", story.imageUrl)
                }
                holder.itemView.context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = stories.size
}
