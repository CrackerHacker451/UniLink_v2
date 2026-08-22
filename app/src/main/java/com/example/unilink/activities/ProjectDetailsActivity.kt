package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.R
import com.example.unilink.adapters.LeaderboardAdapter
import com.example.unilink.databinding.ActivityProjectDetailsBinding
import com.example.unilink.models.Project
import com.example.unilink.repository.ProjectRepository
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.ThemeUtils
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth

class ProjectDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectDetailsBinding
    private val projectRepository = ProjectRepository()
    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()
    
    private var projectId: String? = null
    private var project: Project? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityProjectDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        projectId = intent.getStringExtra("projectId")
        
        loadProjectDetails()
    }

    private fun loadProjectDetails() {
        val id = projectId ?: return
        
        projectRepository.getProjectById(id) { foundProject ->
            if (isFinishing || isDestroyed) return@getProjectById
            if (foundProject != null) {
                project = foundProject
                updateUI(foundProject)
            } else {
                Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun updateUI(project: Project) {
        binding.tvTitle.text = project.title
        binding.tvDescription.text = project.description
        binding.tvStatus.text = project.status
        binding.tvTeamSize.text = "${project.members.size} / ${project.maxTeamSize} members"

        // Skills
        binding.cgSkills.removeAllViews()
        project.skillsRequired.forEach { skill ->
            val chip = Chip(this).apply {
                text = skill
                isClickable = false
                setChipBackgroundColorResource(R.color.bg_card)
                setTextColor(ThemeUtils.getTextPrimaryColor(this@ProjectDetailsActivity)) // Assuming helper exists or using theme primary
            }
            binding.cgSkills.addView(chip)
        }

        // Members List
        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        val memberList = mutableListOf<com.example.unilink.models.User>()
        var loadedCount = 0
        project.members.forEach { memberId ->
            userRepository.getUserById(memberId) { user ->
                user?.let { memberList.add(it) }
                loadedCount++
                if (loadedCount == project.members.size) {
                    binding.rvMembers.adapter = LeaderboardAdapter(memberList) { clickedMember ->
                        val intent = Intent(this, ProfileActivity::class.java).apply {
                            putExtra("userId", clickedMember.uid)
                        }
                        startActivity(intent)
                    }
                }
            }
        }

        // Main Action Button
        val currentUid = auth.currentUser?.uid ?: return
        val isMember = project.members.contains(currentUid)
        val isOwner = project.ownerId == currentUid

        binding.btnMainAction.isEnabled = true
        if (isOwner) {
            binding.btnMainAction.text = "Project Dashboard (Owner)"
            binding.btnMainAction.setOnClickListener {
                // Show owner options: Mark as complete, Edit description, Delete
                val options = arrayOf("Mark as Completed", "Edit Details", "Delete Project")
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Owner Controls")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> projectRepository.completeProject(project.projectId) { loadProjectDetails() }
                            else -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            }
        } else if (isMember) {
            binding.btnMainAction.text = "Open Workspace Chat"
            binding.btnMainAction.setOnClickListener {
                project.chatGroupId?.let { groupId ->
                    val intent = Intent(this, ChatActivity::class.java).apply {
                        putExtra("chatId", groupId)
                        putExtra("chatName", project.title)
                        putExtra("isGroup", true)
                    }
                    startActivity(intent)
                }
            }
        } else if (project.members.size >= project.maxTeamSize) {
            binding.btnMainAction.text = "Team Full"
            binding.btnMainAction.isEnabled = false
        } else {
            binding.btnMainAction.text = "Request to Join Team"
            binding.btnMainAction.setOnClickListener {
                projectRepository.joinProject(project.projectId) { success ->
                    if (success) {
                        Toast.makeText(this, "Joined successfully!", Toast.LENGTH_SHORT).show()
                        loadProjectDetails()
                    }
                }
            }
        }
    }
}
