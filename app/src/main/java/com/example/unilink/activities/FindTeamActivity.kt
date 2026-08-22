package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.unilink.R
import com.example.unilink.adapters.ProjectAdapter
import com.example.unilink.databinding.ActivityFindTeamBinding
import com.example.unilink.models.Project
import com.example.unilink.repository.ProjectRepository
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.ads.AdRequest
import kotlinx.coroutines.launch

class FindTeamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFindTeamBinding
    private val projectRepository = ProjectRepository()
    private val auth = FirebaseAuth.getInstance()
    private var allProjects = listOf<Project>()
    private lateinit var projectAdapter: ProjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityFindTeamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        setupSearch()
        setupCategories()
        fetchTeams()
        loadBannerAd()

        binding.fabCreateTeam.setOnClickListener {
            startActivity(Intent(this, CreateTeamActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter { project, action ->
            handleProjectAction(project, action)
        }
        binding.rvTeams.adapter = projectAdapter
    }

    private fun setupSearch() {
        binding.etSearchTeam.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterProjects(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        intent.getStringExtra("search_query")?.let { query ->
            binding.etSearchTeam.setText(query)
            filterProjects(query)
        }
    }

    private fun setupCategories() {
        binding.catAiMatch.setOnClickListener { runSmartAiMatch() }
        binding.catAll.setOnClickListener { filterByCategory(null) }
        binding.catHackathon.setOnClickListener { filterByCategory("Hackathon") }
        binding.catStartup.setOnClickListener { filterByCategory("Startup") }
        binding.catStudy.setOnClickListener { filterByCategory("Study") }
    }

    private fun runSmartAiMatch() {
        val uid = auth.currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "UniAI is analyzing your profile...", Toast.LENGTH_SHORT).show()

        com.example.unilink.repository.UserRepository().getUserById(uid) { user ->
            if (user == null) return@getUserById
            
            val projectsContext = allProjects.take(15).joinToString(" | ") { "${it.title}: ${it.skillsRequired.joinToString()}" }
            val userContext = "Name: ${user.name}, Skills: ${user.skills.joinToString()}, Goals: ${user.goals}"
            
            val prompt = "Based on this user context: $userContext, which of these projects best fit them? Projects: $projectsContext. Just give me the names of top 2 projects separated by a comma."

            lifecycleScope.launch {
                val response = com.example.unilink.repository.GeminiRepository().getAiHint(prompt, true)
                binding.progressBar.visibility = View.GONE
                
                if (response != null) {
                    val recommendedNames = response.split(",").map { it.trim() }
                    val recommendedProjects = allProjects.filter { p -> 
                        recommendedNames.any { p.title.contains(it, ignoreCase = true) }
                    }
                    
                    if (recommendedProjects.isNotEmpty()) {
                        updateRecyclerView(recommendedProjects)
                        Toast.makeText(this@FindTeamActivity, "UniAI found matches for you! ✨", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@FindTeamActivity, "No perfect matches found. Keep exploring!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun fetchTeams() {
        binding.progressBar.visibility = View.VISIBLE
        projectRepository.getAllProjects { projects ->
            if (isFinishing || isDestroyed) return@getAllProjects
            binding.progressBar.visibility = View.GONE
            allProjects = projects
            updateRecyclerView(projects)
        }
    }

    private fun filterProjects(query: String) {
        val filtered = allProjects.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(query, ignoreCase = true) } ||
            it.skillsRequired.any { skill -> skill.contains(query, ignoreCase = true) }
        }
        updateRecyclerView(filtered)
    }

    private fun filterByCategory(category: String?) {
        val inactiveBg = R.drawable.bg_quick_action
        val activeBg = R.drawable.bg_neon_ring
        
        binding.catAll.setBackgroundResource(if (category == null) activeBg else inactiveBg)
        binding.catHackathon.setBackgroundResource(if (category == "Hackathon") activeBg else inactiveBg)
        binding.catStartup.setBackgroundResource(if (category == "Startup") activeBg else inactiveBg)
        binding.catStudy.setBackgroundResource(if (category == "Study") activeBg else inactiveBg)

        if (category == null) {
            updateRecyclerView(allProjects)
        } else {
            val filtered = allProjects.filter { project ->
                project.tags.any { it.contains(category, ignoreCase = true) }
            }
            updateRecyclerView(filtered)
        }
    }

    private fun updateRecyclerView(projects: List<Project>) {
        projectAdapter.submitList(projects)
    }

    private fun handleProjectAction(project: Project, action: String) {
        val intent = Intent(this, ProjectDetailsActivity::class.java).apply {
            putExtra("projectId", project.projectId)
        }
        startActivity(intent)
    }

    private fun loadBannerAd() {
        val uid = auth.currentUser?.uid ?: return
        com.example.unilink.repository.UserRepository().getUserById(uid) { user ->
            if ((user?.isPremium == true) || (user?.isVip == true)) {
                binding.adView.visibility = View.GONE
            } else {
                val adRequest = AdRequest.Builder().build()
                binding.adView.loadAd(adRequest)
            }
        }
    }

    override fun onPause() {
        binding.adView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.adView.resume()
    }

    override fun onDestroy() {
        binding.adView.destroy()
        super.onDestroy()
    }
}
