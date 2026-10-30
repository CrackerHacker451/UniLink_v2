package com.example.unilink.adapters

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
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
import com.example.unilink.activities.ProfileActivity
import com.example.unilink.databinding.ItemPostBinding
import com.example.unilink.models.Post
import com.example.unilink.repository.PostRepository
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.HapticUtils
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

class PostAdapter : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    private val repository = PostRepository()
    private val userRepository = UserRepository()
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    
    private val premiumCache = mutableMapOf<String, Boolean>()

    class PostViewHolder(val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = getItem(position)
        val context = holder.itemView.context
        
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
        holder.itemView.visibility = View.VISIBLE

        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.textPrimaryColor, typedValue, true)
        val primaryTextColor = typedValue.data

        // Time formatting - optimized
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        holder.binding.tvPostTime.text = post.timestampDate?.let { sdf.format(it) } ?: "Just now"

        // UI Reset for recycling
        holder.binding.ivPostAvatar.imageTintList = null
        holder.binding.tvPostAuthor.setTextColor(primaryTextColor)
        holder.binding.tvPostContent.textScaleX = 1.0f

        if (post.authorId == "system") {
            holder.binding.tvPostAuthor.text = post.authorName
            holder.binding.llPostMain.setBackgroundResource(R.drawable.bg_tag)
            holder.binding.ivPostAvatar.setImageResource(android.R.drawable.ic_popup_reminder)
            holder.binding.ivPostAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#22D3EE"))
            holder.binding.tvPostContent.setTextColor(Color.parseColor("#22D3EE"))
            holder.binding.tvPostContent.textScaleX = 1.02f
            holder.binding.ivPostMenu.visibility = View.GONE
            holder.binding.llActions.visibility = View.GONE
            holder.binding.ivPostVipBadge.visibility = View.GONE
        } else {
            holder.binding.tvPostAuthor.text = post.authorName
            holder.binding.llPostMain.setBackgroundColor(Color.TRANSPARENT)
            holder.binding.tvPostContent.setTextColor(primaryTextColor)
            holder.binding.ivPostMenu.visibility = if (post.authorId == currentUid) View.VISIBLE else View.GONE
            holder.binding.llActions.visibility = View.VISIBLE
            
            // Load Avatar efficiently
            com.example.unilink.utils.ImageLoaderUtils.loadSmartImage(
                imageView = holder.binding.ivPostAvatar,
                url = post.authorAvatarUrl,
                placeholderRes = R.drawable.ic_profile_placeholder,
                isCircle = true,
                fallbackName = post.authorName
            )

            // Optimized Premium Badge handling with caching
            updatePremiumUI(holder, post, primaryTextColor)
        }

        holder.binding.tvPostContent.text = post.content
        
        renderReactions(holder.binding.llReactionsDisplay, post.reactions)
        val totalLikes = post.likes + (post.reactions.values.sumOf { it.size })
        holder.binding.tvPostLikes.text = if (totalLikes > 0) totalLikes.toString() else "0"
        holder.binding.tvCommentCount.text = post.commentCount.toString()

        val isLiked = post.likedBy.contains(currentUid)
        holder.binding.btnLike.setImageResource(if (isLiked) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)

        // Media handling
        if (!post.imageUrl.isNullOrEmpty()) {
            holder.binding.cvPostImage.visibility = View.VISIBLE
            com.example.unilink.utils.ImageLoaderUtils.loadSmartImage(
                holder.binding.ivPostImage,
                post.imageUrl,
                R.drawable.bg_shimmer
            )
            holder.binding.ivPostImage.setOnClickListener {
                val intent = Intent(context, com.example.unilink.activities.MediaViewerActivity::class.java).apply {
                    putExtra("mediaUrl", post.imageUrl)
                    putExtra("title", post.authorName)
                    putExtra("subtitle", holder.binding.tvPostTime.text.toString())
                }
                context.startActivity(intent)
            }
        } else {
            holder.binding.cvPostImage.visibility = View.GONE
        }

        // Click Listeners
        val profileClick = View.OnClickListener { 
            if (post.authorId != "system") startProfileActivity(context, post.authorId) 
        }
        holder.binding.ivPostAvatar.setOnClickListener(profileClick)
        holder.binding.tvPostAuthor.setOnClickListener(profileClick)

        holder.binding.btnLikeContainer.setOnClickListener {
            HapticUtils.playSelection(it)
            val emoji = if (isLiked) "REMOVE" else "❤️"
            repository.reactToPost(post.postId, emoji) { }
            holder.binding.btnLike.animate().scaleX(1.4f).scaleY(1.4f).setDuration(150).withEndAction {
                holder.binding.btnLike.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }.start()
        }

        holder.binding.btnLikeContainer.setOnLongClickListener {
            if (post.authorId != "system") {
                HapticUtils.playHeavy(it)
                showReactionsPopup(it, post.postId)
                true
            } else false
        }

        holder.binding.ivPostMenu.setOnClickListener {
            showDeleteMenu(it, post.postId)
        }

        holder.binding.btnComment.setOnClickListener {
            showCommentsBottomSheet(context, post.postId)
        }
    }

    private fun updatePremiumUI(holder: PostViewHolder, post: Post, defaultColor: Int) {
        val isPremium = premiumCache[post.authorId]
        if (isPremium != null) {
            applyPremiumStyles(holder, isPremium, defaultColor)
        } else {
            holder.binding.ivPostVipBadge.visibility = View.GONE
            userRepository.getUserById(post.authorId) { author ->
                val premium = author?.isPremium == true
                premiumCache[post.authorId] = premium
                // Re-check position to ensure view wasn't recycled during async fetch
                if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val currentPost = getItem(holder.bindingAdapterPosition)
                    if (currentPost.authorId == post.authorId) {
                        applyPremiumStyles(holder, premium, defaultColor)
                    }
                }
            }
        }
    }

    private fun applyPremiumStyles(holder: PostViewHolder, isPremium: Boolean, defaultColor: Int) {
        holder.binding.ivPostVipBadge.visibility = if (isPremium) View.VISIBLE else View.GONE
        if (isPremium) {
            holder.binding.tvPostAuthor.setTextColor(Color.parseColor("#FACC15")) // Gold
            holder.binding.ivPostAvatar.strokeColor = ColorStateList.valueOf(Color.parseColor("#FFD700"))
            holder.binding.ivPostAvatar.strokeWidth = 4f
        } else {
            holder.binding.tvPostAuthor.setTextColor(defaultColor)
            holder.binding.ivPostAvatar.strokeWidth = 0f
        }
    }

    private fun showCommentsBottomSheet(context: android.content.Context, postId: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val binding = com.example.unilink.databinding.BottomSheetCommentsBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        binding.rvComments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        val commentAdapter = CommentAdapter()
        binding.rvComments.adapter = commentAdapter

        repository.getComments(postId) { comments ->
            commentAdapter.submitList(comments)
        }
        binding.btnSendComment.setOnClickListener {
            val content = binding.etCommentInput.text.toString().trim()
            if (content.isNotEmpty()) {
                repository.addComment(postId, content) { success ->
                    if (success) {
                        binding.etCommentInput.setText("")
                        HapticUtils.playSuccess(binding.btnSendComment)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun renderReactions(container: LinearLayout, reactions: Map<String, List<String>>) {
        container.removeAllViews()
        val topReactions = reactions.filter { it.value.isNotEmpty() }
            .toList()
            .sortedByDescending { it.second.size }
            .take(3)

        topReactions.forEach { (emoji, _) ->
            val tv = TextView(container.context).apply {
                text = emoji
                textSize = 11f
                setPadding(4, 0, 4, 0)
            }
            container.addView(tv)
        }
    }

    private fun showReactionsPopup(view: android.view.View, postId: String) {
        val popup = android.widget.PopupWindow(view.context)
        val binding = com.example.unilink.databinding.DialogReactionsBinding.inflate(LayoutInflater.from(view.context))
        popup.contentView = binding.root
        popup.isOutsideTouchable = true
        popup.isFocusable = true
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        val listener = View.OnClickListener { v ->
            val emoji = (v as TextView).text.toString()
            HapticUtils.playSuccess(v)
            repository.reactToPost(postId, emoji) { }
            popup.dismiss()
        }
        binding.reactFire.setOnClickListener(listener)
        binding.reactRocket.setOnClickListener(listener)
        binding.reactBulb.setOnClickListener(listener)
        binding.reactGem.setOnClickListener(listener)
        binding.reactHeart.setOnClickListener(listener)
        popup.showAsDropDown(view, 0, -view.height * 4)
    }

    private fun startProfileActivity(context: android.content.Context, userId: String) {
        val intent = Intent(context, ProfileActivity::class.java).apply {
            putExtra("userId", userId)
        }
        context.startActivity(intent)
    }

    private fun showDeleteMenu(view: View, postId: String) {
        val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
        popup.menu.add("Delete Post")
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Delete Post") {
                repository.deletePost(postId) { success ->
                    if (success) android.widget.Toast.makeText(view.context, "Post removed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        popup.show()
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem.postId == newItem.postId
        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem == newItem
    }
}
