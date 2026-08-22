package com.example.unilink.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.activities.ProfileActivity
import com.example.unilink.databinding.ItemPostBinding
import com.example.unilink.models.Post
import com.example.unilink.repository.PostRepository
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.HapticUtils
import java.text.SimpleDateFormat
import java.util.Locale

class PostAdapter : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    private val repository = PostRepository()
    private val userRepository = UserRepository()
    
    private val premiumCache = mutableMapOf<String, Boolean>()

    class PostViewHolder(val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = getItem(position)
        val context = holder.itemView.context
        
        // Add Entry Animation
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 50f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay((position % 10) * 50L)
            .start()

        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(com.example.unilink.R.attr.textPrimaryColor, typedValue, true)

        // Time formatting
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        holder.binding.tvPostTime.text = post.timestampDate?.let { sdf.format(it) } ?: "Just now"

        // Distinguish System Pulse
        if (post.authorId == "system") {
            holder.binding.tvPostAuthor.text = "Campus Live+ 📡"
            holder.binding.llPostMain.setBackgroundResource(com.example.unilink.R.drawable.bg_glass)
            holder.binding.ivPostAvatar.setImageResource(android.R.drawable.ic_popup_reminder)
            holder.binding.ivPostAvatar.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#22D3EE"))
            holder.binding.tvPostContent.setTextColor(android.graphics.Color.parseColor("#22D3EE"))
            holder.binding.tvPostContent.textScaleX = 1.05f
            holder.binding.ivPostMenu.visibility = android.view.View.GONE
            holder.binding.llActions.visibility = android.view.View.GONE
        } else {
            holder.binding.tvPostAuthor.text = post.authorName
            holder.binding.llPostMain.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            holder.binding.tvPostContent.setTextColor(typedValue.data)
            holder.binding.tvPostContent.textScaleX = 1.0f
            holder.binding.ivPostMenu.visibility = android.view.View.VISIBLE
            holder.binding.llActions.visibility = android.view.View.VISIBLE
            
            // Load Avatar using Coil
            holder.binding.ivPostAvatar.load(com.example.unilink.R.drawable.ic_profile_placeholder) {
                transformations(CircleCropTransformation())
            }
        }

        holder.binding.tvPostContent.text = post.content
        
        renderReactions(holder.binding.llReactionsDisplay, post.reactions)
        val totalLikes = post.likes + (post.reactions.values.sumOf { it.size })
        holder.binding.tvPostLikes.text = "+$totalLikes likes"
        holder.binding.tvCommentCount.text = post.commentCount.toString()

        // Handle Like State UI
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        val isLiked = post.likedBy.contains(currentUid)
        holder.binding.btnLike.setImageResource(if (isLiked) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)

        // VIP badge with caching logic
        if (premiumCache.containsKey(post.authorId)) {
            val isPremium = premiumCache[post.authorId] == true
            holder.binding.ivPostVipBadge.visibility = if (isPremium) android.view.View.VISIBLE else android.view.View.GONE
            if (isPremium) {
                holder.binding.tvPostAuthor.setTextColor(android.graphics.Color.parseColor("#FACC15"))
                holder.binding.ivPostAvatar.setBackgroundResource(com.example.unilink.R.drawable.bg_pro_pulse)
                holder.binding.ivPostAvatar.setPadding(4, 4, 4, 4)
            } else {
                holder.binding.tvPostAuthor.setTextColor(typedValue.data)
                holder.binding.ivPostAvatar.setBackgroundResource(com.example.unilink.R.drawable.bg_tag)
                holder.binding.ivPostAvatar.setPadding(2, 2, 2, 2)
            }
        } else {
            holder.binding.ivPostVipBadge.visibility = android.view.View.GONE
            userRepository.getUserById(post.authorId) { author ->
                val isPremium = author?.isPremium == true
                premiumCache[post.authorId] = isPremium
                if (holder.bindingAdapterPosition == position) {
                    holder.binding.ivPostVipBadge.visibility = if (isPremium) android.view.View.VISIBLE else android.view.View.GONE
                    if (isPremium) {
                        holder.binding.tvPostAuthor.setTextColor(android.graphics.Color.parseColor("#FACC15"))
                        holder.binding.ivPostAvatar.setBackgroundResource(com.example.unilink.R.drawable.bg_pro_pulse)
                        holder.binding.ivPostAvatar.setPadding(4, 4, 4, 4)
                    }
                }
            }
        }

        if (!post.imageUrl.isNullOrEmpty()) {
            holder.binding.cvPostImage.visibility = android.view.View.VISIBLE
            holder.binding.ivPostImage.load(post.imageUrl) {
                crossfade(true)
                placeholder(com.example.unilink.R.drawable.bg_mesh_gradient)
                error(com.example.unilink.R.drawable.bg_tag)
            }
            
            holder.binding.ivPostImage.setOnClickListener {
                val intent = Intent(context, com.example.unilink.activities.MediaViewerActivity::class.java).apply {
                    putExtra("mediaUrl", post.imageUrl)
                    putExtra("title", post.authorName)
                    putExtra("subtitle", holder.binding.tvPostTime.text.toString())
                }
                context.startActivity(intent)
            }
        } else {
            holder.binding.cvPostImage.visibility = android.view.View.GONE
        }

        holder.binding.ivPostAvatar.setOnClickListener {
            startProfileActivity(context, post.authorId)
        }
        
        holder.binding.tvPostAuthor.setOnClickListener {
            startProfileActivity(context, post.authorId)
        }

        holder.binding.btnLikeContainer.setOnClickListener {
            HapticUtils.playSelection(it)
            repository.reactToPost(post.postId, "❤️") { }
            holder.binding.btnLike.animate().scaleX(1.4f).scaleY(1.4f).setDuration(150).withEndAction {
                holder.binding.btnLike.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }.start()
        }

        holder.binding.btnLikeContainer.setOnLongClickListener {
            HapticUtils.playHeavy(it)
            showReactionsPopup(it, post.postId)
            true
        }

        holder.binding.ivPostMenu.setOnClickListener {
            if (post.authorId == currentUid) {
                showDeleteMenu(it, post.postId)
            }
        }

        holder.binding.btnComment.setOnClickListener {
            showCommentsBottomSheet(context, post.postId)
        }
    }

    private fun showCommentsBottomSheet(context: android.content.Context, postId: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val binding = com.example.unilink.databinding.BottomSheetCommentsBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        binding.rvComments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)

        repository.getComments(postId) { comments ->
            binding.rvComments.adapter = CommentAdapter(comments)
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

    private fun renderReactions(container: android.widget.LinearLayout, reactions: Map<String, List<String>>) {
        container.removeAllViews()
        val topReactions = reactions.filter { it.value.isNotEmpty() }
            .toList()
            .sortedByDescending { it.second.size }
            .take(3)

        topReactions.forEach { (emoji, uids) ->
            val tv = android.widget.TextView(container.context).apply {
                text = emoji
                textSize = 12f
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
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        val listener = android.view.View.OnClickListener { v ->
            val emoji = (v as android.widget.TextView).text.toString()
            if (emoji == "🔥" || emoji == "💎") {
                HapticUtils.playHeavy(v)
            } else {
                HapticUtils.playSuccess(v)
            }
            repository.reactToPost(postId, emoji) {
                android.widget.Toast.makeText(view.context, "Reacted with $emoji", android.widget.Toast.LENGTH_SHORT).show()
            }
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
        val intent = Intent(context, ProfileActivity::class.java)
        intent.putExtra("userId", userId)
        context.startActivity(intent)
    }

    private fun showDeleteMenu(view: android.view.View, postId: String) {
        val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
        popup.menu.add("Delete Post")
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Delete Post") {
                repository.deletePost(postId) { success ->
                    if (success) android.widget.Toast.makeText(view.context, "Post deleted", android.widget.Toast.LENGTH_SHORT).show()
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
