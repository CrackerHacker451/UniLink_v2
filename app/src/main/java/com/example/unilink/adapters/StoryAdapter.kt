package com.example.unilink.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.activities.CreatePostActivity
import com.example.unilink.activities.StoryViewActivity
import com.example.unilink.databinding.ItemStoryBinding
import com.example.unilink.models.Story
import com.example.unilink.utils.ImageLoaderUtils

class StoryAdapter : ListAdapter<Story, StoryAdapter.StoryViewHolder>(StoryDiffCallback()) {

    class StoryViewHolder(val binding: ItemStoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val binding = ItemStoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        val story = getItem(position)
        holder.binding.tvStoryName.text = if (story.userId == "1") "My Story" else story.userName
        
        if (story.userId == "1") {
            holder.binding.ivAddStory.visibility = View.VISIBLE
            ImageLoaderUtils.loadSmartImage(
                holder.binding.ivStoryAvatar,
                story.userAvatarUrl,
                R.drawable.ic_profile_placeholder,
                isCircle = true
            )
            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, CreatePostActivity::class.java).apply {
                    putExtra("type", "story")
                }
                holder.itemView.context.startActivity(intent)
            }
        } else {
            holder.binding.ivAddStory.visibility = View.GONE
            ImageLoaderUtils.loadSmartImage(
                holder.binding.ivStoryAvatar,
                story.userAvatarUrl,
                R.drawable.ic_profile_placeholder,
                isCircle = true
            )
            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, StoryViewActivity::class.java).apply {
                    putExtra("userId", story.userId)
                    putExtra("userName", story.userName)
                    putExtra("imageUrl", story.imageUrl)
                }
                holder.itemView.context.startActivity(intent)
            }
        }
    }

    class StoryDiffCallback : DiffUtil.ItemCallback<Story>() {
        override fun areItemsTheSame(oldItem: Story, newItem: Story): Boolean = oldItem.storyId == newItem.storyId
        override fun areContentsTheSame(oldItem: Story, newItem: Story): Boolean = oldItem == newItem
    }
}
