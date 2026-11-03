package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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

    private fun showComposeBottomSheet(content: @androidx.compose.runtime.Composable (com.google.android.material.bottomsheet.BottomSheetDialog) -> Unit) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                content(dialog)
            }
        }
        dialog.setContentView(composeView)

        dialog.findViewById<android.view.View>(com.google.android.material.R.id.container)?.let { container ->
            container.setViewTreeLifecycleOwner(this)
            container.setViewTreeViewModelStoreOwner(this)
            container.setViewTreeSavedStateRegistryOwner(this)
        }

        dialog.show()
    }

    private fun showSelfieCheckTeamGate(onVerified: () -> Unit) {
        showComposeBottomSheet { dialog ->
            val viewModel = com.example.unilink.worldid.NewtonsLobbyTrustViewModel(application)
            viewModel.resetState()
            com.example.unilink.worldid.NewtonsLobbyTrustSheet(
                projectId = "team_gate_${System.currentTimeMillis()}",
                projectTitle = "Team Creation Gate",
                tier = com.example.unilink.worldid.CredentialTier.SELFIE_CHECK,
                viewModel = viewModel,
                onVerifiedSuccess = {
                    dialog.dismiss()
                    onVerified()
                },
                onClose = { dialog.dismiss() }
            )
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

        showSelfieCheckTeamGate {
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
}
