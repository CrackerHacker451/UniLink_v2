package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
import com.example.unilink.databinding.ItemCommentBinding
import com.example.unilink.models.Comment
import java.text.SimpleDateFormat
import java.util.Locale

class CommentAdapter(private val comments: List<Comment>) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.binding.tvCommentAuthor.text = comment.authorName
        holder.binding.tvCommentContent.text = comment.content
        
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        holder.binding.tvCommentTime.text = comment.timestampDate?.let { sdf.format(it) } ?: "Just now"

        holder.binding.ivCommentAvatar.load(comment.authorAvatarUrl ?: R.drawable.ic_profile_placeholder) {
            transformations(CircleCropTransformation())
        }
    }

    override fun getItemCount(): Int = comments.size
}
