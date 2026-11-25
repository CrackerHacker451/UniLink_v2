package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.adapters.ProjectAdapter
import com.example.unilink.databinding.ActivityTeamSquadsHubBinding
import com.example.unilink.models.Project
import com.example.unilink.repository.ProjectRepository
import com.example.unilink.utils.ThemeUtils

class TeamSquadsHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamSquadsHubBinding
    private val projectRepository = ProjectRepository()
    private lateinit var projectAdapter: ProjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityTeamSquadsHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnCreateTeamSmall.setOnClickListener {
            startActivity(Intent(this, CreateTeamActivity::class.java))
        }

        setupRecyclerView()
        fetchTeams()
    }

    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter { project, action ->
            if (action == "join") {
                Toast.makeText(this, "Joining ${project.displayTitle}... 🚀", Toast.LENGTH_SHORT).show()
                projectRepository.joinProject(project.projectId) {
                    val intent = Intent(this, ProjectDetailsActivity::class.java).apply {
                        putExtra("projectId", project.projectId)
                    }
                    startActivity(intent)
                }
            } else {
                val intent = Intent(this, ProjectDetailsActivity::class.java).apply {
                    putExtra("projectId", project.projectId)
                }
                startActivity(intent)
            }
        }

        binding.rvTeamsHub.apply {
            layoutManager = LinearLayoutManager(this@TeamSquadsHubActivity)
            adapter = projectAdapter
            setHasFixedSize(true)
        }
    }

    private fun fetchTeams() {
        projectRepository.getAllProjects { projects ->
            val openTeams = projects.filter { p -> p.status != "Completed" }
            projectAdapter.submitList(if (openTeams.isNotEmpty()) openTeams else getPresetTeams())
        }
    }

    private fun getPresetTeams(): List<Project> {
        return listOf(
            Project(
                projectId = "t1",
                title = "🚀 ETHGlobal Mumbai DeFi & Arkiv Vault Team",
                description = "Building a high-performance Web3 vault app! Looking for 1 Frontend & 1 Smart Contract dev.",
                ownerName = "Amit S. (CSE)",
                skillsRequired = listOf("Frontend", "Blockchain", "Solidity"),
                tags = listOf("hackathon", "web3"),
                maxTeamSize = 4,
                source = "unilink_app"
            ),
            Project(
                projectId = "t2",
                title = "🤖 UniAI Autonomous Study Copilot Squad",
                description = "Building zero-knowledge 0G Private Computer AI agents for student exam prep and note summarization!",
                ownerName = "Sneha R. (AI/ML)",
                skillsRequired = listOf("AI/ML", "Python", "React"),
                tags = listOf("ai", "dev"),
                maxTeamSize = 4,
                source = "unilink_app"
            ),
            Project(
                projectId = "t3",
                title = "🎨 UI/UX Design & SpeedLink Call Squad",
                description = "Redesigning 120Hz dark glass campus mobile screens & WebRTC 1080p video matching UI!",
                ownerName = "Aarav K. (Design)",
                skillsRequired = listOf("UI/UX", "Figma", "Android"),
                tags = listOf("design", "mobile"),
                maxTeamSize = 3,
                source = "unilink_app"
            )
        )
    }
}
