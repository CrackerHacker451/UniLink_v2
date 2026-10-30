package com.example.unilink.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.adapters.ProjectAdapter
import com.example.unilink.databinding.FragmentProjectsBinding
import com.example.unilink.models.Project
import com.example.unilink.repository.ProjectRepository
import com.example.unilink.repository.LiveFeedRepository

class ProjectsFragment : Fragment() {

    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!
    private val projectRepository = ProjectRepository()

    private var allProjects = listOf<Project>()
    private var filteredProjects = listOf<Project>()
    private var projectsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private lateinit var projectAdapter: ProjectAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProjectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val topInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
            val params = binding.tvProjectsTitle.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.topMargin = topInset + (12 * resources.displayMetrics.density).toInt()
                binding.tvProjectsTitle.layoutParams = params
            }
            insets
        }

        setupRecyclerView()
        setupFilters()
        fetchProjects()

        binding.btnLaunchTeam.setOnClickListener {
            startActivity(Intent(context, com.example.unilink.activities.CreateTeamActivity::class.java))
        }

        binding.btnMyTeams.setOnClickListener {
            val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val myProjects = allProjects.filter { p -> p.ownerId == myUid || p.members.contains(myUid) }
            projectAdapter.submitList(myProjects)
            binding.tvEmptyState.visibility = if (myProjects.isEmpty()) View.VISIBLE else View.GONE
        }

        arguments?.getString("search_query")?.let { query ->
            binding.etSearchProjects.setText(query)
            applyFilters()
        }
    }

    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter { project, action ->
            handleProjectAction(project, action)
        }
        binding.rvProjects.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = projectAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupFilters() {
        binding.etSearchProjects.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, _ ->
            applyFilters()
        }
    }

    private fun applyFilters() {
        if (_binding == null) return
        
        val query = binding.etSearchProjects.text.toString().lowercase().trim()
        val selectedChipId = binding.chipGroupCategories.checkedChipId
        
        val category = when (selectedChipId) {
            binding.chipAI.id -> "ai"
            binding.chipDev.id -> "dev"
            binding.chipDesign.id -> "design"
            binding.chipResearch.id -> "research"
            else -> ""
        }

        filteredProjects = allProjects.filter { project ->
            val title = project.title?.lowercase() ?: ""
            val description = project.description?.lowercase() ?: ""
            val skills = project.skillsRequired ?: emptyList()
            
            val matchesQuery = title.contains(query) || 
                              description.contains(query) ||
                              skills.any { it.lowercase().contains(query) }
            
            val matchesCategory = if (category.isEmpty()) true 
                                 else project.tags?.any { it.lowercase().contains(category) } == true || 
                                      title.contains(category)
            
            matchesQuery && matchesCategory
        }

        updateAdapter()
    }

    private fun fetchProjects() {
        showSkeleton(true)
        projectsListener?.remove()
        projectsListener = projectRepository.getAllProjects { projects ->
            if (_binding == null) return@getAllProjects
            showSkeleton(false)
            allProjects = projects
            applyFilters()
        }
    }

    private fun showSkeleton(show: Boolean) {
        val currentContext = context ?: return
        if (_binding == null) return
        
        if (show) {
            binding.rvProjects.visibility = View.GONE
            binding.llSkeletonContainer.visibility = View.VISIBLE
            try {
                val shimmer = android.view.animation.AnimationUtils.loadAnimation(currentContext, com.example.unilink.R.anim.shimmer_alpha)
                binding.llSkeletonContainer.startAnimation(shimmer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            binding.llSkeletonContainer.clearAnimation()
            binding.llSkeletonContainer.visibility = View.GONE
            binding.rvProjects.visibility = View.VISIBLE
        }
    }

    private fun updateAdapter() {
        if (_binding == null) return
        binding.tvEmptyState.visibility = if (filteredProjects.isEmpty()) View.VISIBLE else View.GONE
        projectAdapter.submitList(filteredProjects)
    }

    private fun handleProjectAction(project: Project, action: String) {
        val currentContext = context ?: return
        when (action) {
            "manage" -> showManageProjectDialog(project)
            else -> {
                val intent = Intent(currentContext, com.example.unilink.activities.ProjectDetailsActivity::class.java).apply {
                    putExtra("projectId", project.projectId)
                }
                startActivity(intent)
            }
        }
    }

    private fun showManageProjectDialog(project: Project) {
        val currentContext = context ?: return
        if (!isAdded) return
        AlertDialog.Builder(currentContext)
            .setTitle(project.displayTitle)
            .setMessage("Project Management Console")
            .setPositiveButton("Mark Completed") { _, _ ->
                projectRepository.completeProject(project.projectId) { success ->
                    if (success && isAdded) {
                        Toast.makeText(requireContext(), "Project completed!", Toast.LENGTH_LONG).show()
                        LiveFeedRepository().publishPulse("🎓 ${project.ownerName}'s team just completed their project: ${project.title}!")
                    }
                }
            }
            .setNeutralButton("🚀 Boost (50 Coins)") { _, _ ->
                projectRepository.boostProject(project.projectId) { success ->
                    if (!isAdded) return@boostProject
                    if (success) Toast.makeText(requireContext(), "Project Boosted to Top!", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(requireContext(), "Failed to boost. Check UniCoins.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        projectsListener?.remove()
        _binding = null
    }
}
