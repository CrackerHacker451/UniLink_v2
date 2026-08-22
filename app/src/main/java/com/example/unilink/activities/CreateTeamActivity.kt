package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.unilink.databinding.ActivityCreateTeamBinding
import com.example.unilink.repository.ProjectRepository
import com.example.unilink.utils.ThemeUtils

class CreateTeamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTeamBinding
    private val projectRepository = ProjectRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTeamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

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
            binding.etTeamTitle.error = "Title required"
            return
        }
        if (description.isEmpty()) {
            binding.etTeamDescription.error = "Description required"
            return
        }

        val skills = skillsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val tags = tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        binding.btnCreateTeam.isEnabled = false
        projectRepository.createProject(title, description, skills, tags) { success ->
            if (success) {
                Toast.makeText(this, "Team created successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                binding.btnCreateTeam.isEnabled = true
                Toast.makeText(this, "Failed to create team", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
