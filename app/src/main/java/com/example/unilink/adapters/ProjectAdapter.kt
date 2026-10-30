package com.example.unilink.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R
import com.example.unilink.databinding.ItemProjectBinding
import com.example.unilink.models.Project
import com.google.firebase.auth.FirebaseAuth

class ProjectAdapter(
    private val onActionClick: (Project, String) -> Unit
) : ListAdapter<Project, ProjectAdapter.ProjectViewHolder>(ProjectDiffCallback()) {

    class ProjectViewHolder(val binding: ItemProjectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val binding = ItemProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = getItem(position)
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        
        holder.binding.tvProjectTitle.text = project.displayTitle
        holder.binding.tvProjectDescription.text = project.description ?: ""
        holder.binding.tvProjectStatus.text = project.status ?: "Open"
        holder.binding.tvProgressPercent.text = "Progress: ${project.progress}%"
        holder.binding.projectProgress.progress = project.progress
        holder.binding.tvTeamCount.text = "${project.members.size}/${project.effectiveMaxMembers}"
        
        // Website Source Badge
        if (project.source == "newtonslobby") {
            holder.binding.tvSourceBadge.visibility = View.VISIBLE
        } else {
            holder.binding.tvSourceBadge.visibility = View.GONE
        }
        
        // Show Skills
        val skills = project.skillsRequired
        if (!skills.isNullOrEmpty()) {
            holder.binding.tvProjectSkills.visibility = android.view.View.VISIBLE
            holder.binding.tvProjectSkills.text = "Skills: ${skills.joinToString(", ")}"
        } else {
            holder.binding.tvProjectSkills.visibility = android.view.View.GONE
        }
        
        val context = holder.itemView.context
        
        // Boosted Badge logic
        val isBoosted = project.boostUntil != null && project.boostUntil!!.after(java.util.Date())
        if (isBoosted) {
            holder.binding.tvProjectStatus.setBackgroundResource(com.example.unilink.R.drawable.bg_tag)
            holder.binding.tvProjectStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFD700"))
            holder.binding.tvProjectStatus.text = "BOOSTED"
            holder.binding.tvProjectStatus.setTextColor(android.graphics.Color.BLACK)
        } else {
            holder.binding.tvProjectStatus.setBackgroundResource(com.example.unilink.R.drawable.bg_tag)
            holder.binding.tvProjectStatus.backgroundTintList = null
            holder.binding.tvProjectStatus.text = project.status ?: "Open"
            holder.binding.tvProjectStatus.setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.holo_blue_bright))
        }

        val isOwner = project.ownerId == currentUid
        val isMember = project.members.contains(currentUid)
        val memberCount = project.members.size

        when {
            project.status == "Completed" -> {
                holder.binding.btnOpen.text = "View Archive"
                holder.binding.btnOpen.isEnabled = true
                holder.binding.btnOpen.alpha = 0.8f
            }
            isOwner -> {
                holder.binding.btnOpen.text = "Manage / Complete"
                holder.binding.btnOpen.isEnabled = true
            }
            isMember -> {
                holder.binding.btnOpen.text = "Open Workspace"
                holder.binding.btnOpen.isEnabled = true
            }
            memberCount >= project.effectiveMaxMembers -> {
                holder.binding.btnOpen.text = "Team Full"
                holder.binding.btnOpen.isEnabled = false
                holder.binding.btnOpen.alpha = 0.5f
            }
            else -> {
                holder.binding.btnOpen.text = "Join Project"
                holder.binding.btnOpen.isEnabled = true
            }
        }

        holder.binding.btnOpen.setOnClickListener { 
            val action = when {
                project.status == "Completed" -> "view"
                isOwner -> "manage"
                isMember -> "open"
                else -> "join"
            }
            onActionClick(project, action) 
        }

        // Subtle entry animation
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 50f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(position.toLong() * 50)
            .start()
    }

    class ProjectDiffCallback : DiffUtil.ItemCallback<Project>() {
        override fun areItemsTheSame(oldItem: Project, newItem: Project): Boolean {
            return oldItem.projectId == newItem.projectId
        }

        override fun areContentsTheSame(oldItem: Project, newItem: Project): Boolean {
            return oldItem == newItem
        }
    }
}
