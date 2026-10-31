package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import coil.load
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
    private val storageRepository = com.example.unilink.repository.StorageRepository()
    private val auth = FirebaseAuth.getInstance()
    
    private var projectId: String? = null
    private var project: Project? = null

    private val pickBannerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val id = projectId ?: return@let
            Toast.makeText(this, "Uploading banner...", Toast.LENGTH_SHORT).show()
            storageRepository.uploadTeamBanner(id, it) { url ->
                if (url != null) {
                    binding.ivProjectBanner.load(url)
                    Toast.makeText(this, "Banner updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityProjectDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        projectId = intent.getStringExtra("projectId")
        
        // Initialize RecyclerView layoutManager and empty adapter immediately to prevent "No adapter attached" warning
        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = LeaderboardAdapter(emptyList()) {}

        loadProjectDetails()

        binding.btnShare.setOnClickListener {
            val id = projectId ?: return@setOnClickListener
            val shareUrl = "https://uni-link-b7f2c.web.app/#team/$id"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Join my project team on Newton's Lobby: $shareUrl")
            }
            startActivity(Intent.createChooser(intent, "Share Team"))
        }

        binding.btnWorldIdBoost.setOnClickListener {
            val id = projectId ?: return@setOnClickListener
            val title = project?.displayTitle ?: "Student Prototype"
            showWorldIdTrustSheet(id, title)
        }

        binding.btnChainlinkCre.setOnClickListener {
            val id = projectId ?: return@setOnClickListener
            val title = project?.displayTitle ?: "Student Prototype"
            val author = project?.ownerId ?: "author_uid"

            Toast.makeText(this, "⬡ Initiating Chainlink CRE Runtime Environment...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val creService = com.example.unilink.chainlink.ChainlinkCreService()
                val response = creService.triggerCreWorkflow(
                    com.example.unilink.chainlink.CreWorkflowTriggerRequest(id, title, author)
                )
                val msg = "⬡ CRE Workflow Success!\nCode Score: ${response.aiCodeScore}/100\nGrant: \$${response.grantAmountUsdc} USDC (${response.grantWeight})\nTx: ${response.evmTxHash.take(18)}..."
                androidx.appcompat.app.AlertDialog.Builder(this@ProjectDetailsActivity)
                    .setTitle("Chainlink CRE Workflow Executed ⬡")
                    .setMessage(msg)
                    .setPositiveButton("OK") { d, _ -> d.dismiss() }
                    .show()
            }
        }
    }

    private fun showComposeBottomSheet(content: @androidx.compose.runtime.Composable (com.google.android.material.bottomsheet.BottomSheetDialog) -> Unit) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ProjectDetailsActivity)
            setViewTreeViewModelStoreOwner(this@ProjectDetailsActivity)
            setViewTreeSavedStateRegistryOwner(this@ProjectDetailsActivity)
            setContent { content(dialog) }
        }
        dialog.setContentView(composeView)
        dialog.findViewById<android.view.View>(com.google.android.material.R.id.container)?.let { container ->
            container.setViewTreeLifecycleOwner(this)
            container.setViewTreeViewModelStoreOwner(this)
            container.setViewTreeSavedStateRegistryOwner(this)
        }
        dialog.show()
    }

    private fun showWorldIdTrustSheet(id: String, title: String) {
        showComposeBottomSheet { dialog ->
            val viewModel = com.example.unilink.worldid.NewtonsLobbyTrustViewModel(application)
            com.example.unilink.worldid.NewtonsLobbyTrustSheet(
                projectId = id,
                projectTitle = title,
                viewModel = viewModel,
                onClose = { dialog.dismiss() }
            )
        }
    }

    private fun loadProjectDetails() {
        val id = projectId ?: return
        
        // Load Banner from Storage (Newton's Lobby Path)
        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("teams/$id/banner/cover.jpg")
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            if (!isFinishing && !isDestroyed) {
                binding.ivProjectBanner.load(uri)
            }
        }.addOnFailureListener {
            /* Ignore 404 when project has no custom cover.jpg */
        }

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
        binding.tvTitle.text = project.displayTitle
        binding.tvDescription.text = project.description ?: ""
        binding.tvStatus.text = project.status ?: "Open"
        binding.tvTeamSize.text = "${project.members.size} / ${project.effectiveMaxMembers} members"

        // Skills
        binding.cgSkills.removeAllViews()
        project.skillsRequired?.forEach { skill ->
            val chip = Chip(this).apply {
                text = skill
                isClickable = false
                setChipBackgroundColorResource(R.color.bg_card)
                setTextColor(ThemeUtils.getTextPrimaryColor(this@ProjectDetailsActivity))
            }
            binding.cgSkills.addView(chip)
        }

        // Members List
        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        val memberList = mutableListOf<com.example.unilink.models.User>()
        val members = project.members
        if (members.isEmpty()) {
            binding.rvMembers.adapter = null
        } else {
            var loadedCount = 0
            members.forEach { memberId ->
                userRepository.getUserById(memberId) { user ->
                    user?.let { memberList.add(it) }
                    loadedCount++
                    if (loadedCount == members.size) {
                        binding.rvMembers.adapter = LeaderboardAdapter(memberList) { clickedMember ->
                            val intent = Intent(this, ProfileActivity::class.java).apply {
                                putExtra("userId", clickedMember.uid)
                            }
                            startActivity(intent)
                        }
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
                // Show owner options
                val options = arrayOf("Mark as Completed", "Edit Details", "Change Team Banner", "Delete Project")
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Owner Controls")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> projectRepository.completeProject(project.projectId) { loadProjectDetails() }
                            2 -> pickBannerLauncher.launch("image/*")
                            else -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            }
        } else if (isMember) {
            binding.btnMainAction.text = "Open Workspace Chat"
            binding.btnMainAction.setOnClickListener {
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("chatId", project.projectId)
                    putExtra("chatName", project.displayTitle)
                    putExtra("isGroup", true)
                    putExtra("isProjectSync", true)
                }
                startActivity(intent)
            }
        } else if (project.members.size >= project.effectiveMaxMembers) {
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
