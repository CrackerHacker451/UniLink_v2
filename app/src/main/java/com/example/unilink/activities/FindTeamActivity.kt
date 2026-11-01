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
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_teammate_matchmaker, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).create()

        val btnClose = dialogView.findViewById<android.widget.TextView>(R.id.btnCloseDialog)
        val etSkill = dialogView.findViewById<android.widget.EditText>(R.id.etTargetSkill)
        val btnRun = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRunAiMatch)
        val progress = dialogView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.matchProgress)
        val resultsContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.llMatchResultsContainer)

        val chipHackathon = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chipHackathon)
        val chipStartup = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chipStartup)

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnRun?.setOnClickListener { v ->
            com.example.unilink.utils.HapticUtils.playSelection(v)
            val skillQuery = etSkill?.text?.toString()?.trim()?.ifEmpty { "Figma UI/UX & Machine Learning" } ?: "Figma UI/UX & Machine Learning"
            val scope = if (chipHackathon?.isChecked == true) "Hackathon 🏆" else if (chipStartup?.isChecked == true) "Startup MVP 🚀" else "Group Assignment 📚"

            progress?.visibility = View.VISIBLE
            btnRun.isEnabled = false
            btnRun.text = "Matching Skills..."

            com.example.unilink.repository.UserRepository().getUserById(uid) { user ->
                val mySkills = user?.skills?.joinToString() ?: "Kotlin, Android, AI"
                val prompt = "Act as an AI Hackathon Matchmaker. Project Scope: $scope. Skill Needed: $skillQuery. User Skills: $mySkills. Recommend top 2 teammate profiles with match score % and key reasons."

                lifecycleScope.launch {
                    val response = com.example.unilink.repository.GeminiRepository().getAiHint(prompt, true)
                    progress?.visibility = View.GONE
                    btnRun.isEnabled = true
                    btnRun.text = "Find Best Teammates ✨"

                    resultsContainer?.visibility = View.VISIBLE
                    resultsContainer?.removeAllViews()

                    val resultTv = android.widget.TextView(this@FindTeamActivity).apply {
                        text = response
                        setTextColor(getColor(R.color.white))
                        textSize = 13f
                        setPadding(12, 12, 12, 12)
                    }
                    resultsContainer?.addView(resultTv)
                }
            }
        }

        dialog.show()
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
            val title = it.title ?: ""
            val desc = it.description ?: ""
            val tags = it.tags ?: emptyList()
            val skills = it.skillsRequired ?: emptyList()
            
            title.contains(query, ignoreCase = true) ||
            desc.contains(query, ignoreCase = true) ||
            tags.any { tag -> tag.contains(query, ignoreCase = true) } ||
            skills.any { skill -> skill.contains(query, ignoreCase = true) }
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
                project.tags?.any { it.contains(category, ignoreCase = true) } == true
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
