package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.unilink.R
import com.example.unilink.databinding.ActivityCreateTeamBinding
import com.example.unilink.repository.GeminiRepository
import com.example.unilink.repository.ProjectRepository
import com.example.unilink.utils.ThemeUtils
import kotlinx.coroutines.launch

class CreateTeamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTeamBinding
    private val projectRepository = ProjectRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTeamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnAiSuggest.setOnClickListener {
            binding.btnAiSuggest.isEnabled = false
            Toast.makeText(this, "✨ UniAI generating project idea...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val prompt = "Generate a high-impact hackathon project title, description, and required skills for college students."
                    val aiText = GeminiRepository().getAiHint(prompt, isPremium = true)

                    binding.etTeamTitle.setText("AI Smart Campus Assistant")
                    binding.etTeamDescription.setText(aiText)
                    binding.etRequiredSkills.setText("Kotlin, Python, Firebase, Machine Learning")
                    binding.etTags.setText("AI, Mobile, OpenSource, Hackathon")
                    Toast.makeText(this@CreateTeamActivity, "✨ AI Project Idea generated!", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
                binding.btnAiSuggest.isEnabled = true
            }
        }

        binding.btnCreateTeam.setOnClickListener {
            createTeam()
        }
    }

    private fun createTeam() {
        val title = binding.etTeamTitle.text.toString().trim()
        val description = binding.etTeamDescription.text.toString().trim()
        val skillsString = binding.etRequiredSkills.text.toString().trim()
        val tagsString = binding.etTags.text.toString().trim()

        if (title.isEmpty()) {
            binding.etTeamTitle.error = getString(R.string.title_required)
            return
        }
        if (description.isEmpty()) {
            binding.etTeamDescription.error = getString(R.string.description_required)
            return
        }

        val skills = skillsString.split(",").asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val tags = tagsString.split(",").asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val teamSize = binding.etTeamSize.text.toString().trim().toIntOrNull() ?: 4

        binding.btnCreateTeam.isEnabled = false
        projectRepository.createProject(title, description, skills, tags, maxTeamSize = teamSize) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.team_created_success), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                binding.btnCreateTeam.isEnabled = true
                Toast.makeText(this, getString(R.string.team_created_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
