package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.databinding.ItemCommentBinding
import com.example.unilink.models.Comment
import com.example.unilink.utils.ImageLoaderUtils
import java.text.SimpleDateFormat
import java.util.Locale

class CommentAdapter : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    class CommentViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = getItem(position)
        holder.binding.tvCommentAuthor.text = comment.authorName
        holder.binding.tvCommentContent.text = comment.content
        
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        holder.binding.tvCommentTime.text = comment.timestampDate?.let { sdf.format(it) } ?: "Just now"

        ImageLoaderUtils.loadSmartImage(
            imageView = holder.binding.ivCommentAvatar,
            url = comment.authorAvatarUrl,
            placeholderRes = R.drawable.ic_profile_placeholder,
            isCircle = true,
            fallbackName = comment.authorName
        )
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean = oldItem.commentId == newItem.commentId
        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean = oldItem == newItem
    }
}
