package com.example.unilink.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.unilink.R
import com.example.unilink.activities.ProfileActivity
import com.example.unilink.adapters.PostAdapter
import com.example.unilink.adapters.StudentGridAdapter
import com.example.unilink.databinding.FragmentSearchBinding
import com.example.unilink.models.User
import com.example.unilink.repository.LiveFeedRepository
import com.example.unilink.repository.UserRepository
import com.google.firebase.firestore.ListenerRegistration

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository()
    private val liveFeedRepository = LiveFeedRepository()

    private var allUsers = listOf<User>()
    private var filteredUsers = listOf<User>()
    private lateinit var studentAdapter: StudentGridAdapter
    private lateinit var pulseAdapter: PostAdapter
    private var pulseListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updatePadding(top = systemBars.top)
            insets
        }

        setupRecyclerView()
        setupPulseRecyclerView()
        setupSearchListener()
        setupCategoryChips()
        fetchUsers()
        fetchRecentPulse()

        arguments?.getString("search_query")?.let { query ->
            binding.etSearchStudents.setText(query)
            applyFilters()
        }
    }

    private fun setupRecyclerView() {
        studentAdapter = StudentGridAdapter { user ->
            val intent = Intent(context, ProfileActivity::class.java).apply {
                putExtra("userId", user.uid)
            }
            startActivity(intent)
        }
        binding.rvStudentGrid.adapter = studentAdapter
    }

    private fun setupPulseRecyclerView() {
        pulseAdapter = PostAdapter()
        binding.rvRecentPulse.adapter = pulseAdapter
    }

    private fun fetchRecentPulse() {
        pulseListener?.remove()
        pulseListener = liveFeedRepository.observeCampusPulse { posts ->
            if (_binding == null) return@observeCampusPulse
            val top2 = posts.take(2)
            binding.llRecentPulseContainer.visibility = if (top2.isNotEmpty()) View.VISIBLE else View.GONE
            pulseAdapter.submitList(top2)
        }
    }

    private fun setupSearchListener() {
        binding.etSearchStudents.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupCategoryChips() {
        binding.cgCategories.setOnCheckedStateChangeListener { _, _ ->
            applyFilters()
        }
    }

    private fun fetchUsers() {
        showSkeleton(true)
        userRepository.getAllUsers { users ->
            if (_binding == null) return@getAllUsers
            showSkeleton(false)
            allUsers = users.sortedByDescending { it.impactScore }
            applyFilters()
        }
    }

    private fun showSkeleton(show: Boolean) {
        if (_binding == null) return
        if (show) {
            binding.rvStudentGrid.visibility = View.GONE
            binding.llSkeletonContainer.visibility = View.VISIBLE
            val shimmer = android.view.animation.AnimationUtils.loadAnimation(context, com.example.unilink.R.anim.shimmer_alpha)
            binding.llSkeletonContainer.startAnimation(shimmer)
        } else {
            binding.llSkeletonContainer.clearAnimation()
            binding.llSkeletonContainer.visibility = View.GONE
            binding.rvStudentGrid.visibility = View.VISIBLE
        }
    }

    private fun applyFilters() {
        val query = binding.etSearchStudents.text.toString().trim().lowercase()
        val checkedChipId = binding.cgCategories.checkedChipId

        val category = when (checkedChipId) {
            R.id.chipEngineering -> "tech"
            R.id.chipDesign -> "design"
            R.id.chipMedical -> "health"
            R.id.chipManagement -> "business"
            else -> ""
        }

        filteredUsers = allUsers.filter { user ->
            val matchesQuery = if (query.isEmpty()) true
                              else user.name.lowercase().contains(query) ||
                                   user.username.lowercase().contains(query) ||
                                   user.email.lowercase().contains(query) ||
                                   user.branch?.lowercase()?.contains(query) == true ||
                                   user.collegeName?.lowercase()?.contains(query) == true ||
                                   user.skills?.any { it.lowercase().contains(query) } == true

            val matchesCategory = when (category) {
                "tech" -> user.branch?.lowercase()?.contains("tech") == true ||
                          user.branch?.lowercase()?.contains("cs") == true ||
                          user.branch?.lowercase()?.contains("computer") == true ||
                          user.branch?.lowercase()?.contains("engineering") == true ||
                          user.branch?.lowercase()?.contains("ai") == true ||
                          user.branch?.lowercase()?.contains("data") == true ||
                          user.branch?.lowercase()?.contains("newton") == true ||
                          user.skills?.any { s -> listOf("tech", "cs", "code", "dev", "ai", "web", "kotlin", "java", "python", "react").any { s.lowercase().contains(it) } } == true ||
                          user.userType.lowercase().contains("tech") || user.userType.lowercase().contains("engineering")
                "design" -> user.branch?.lowercase()?.contains("design") == true ||
                            user.branch?.lowercase()?.contains("creative") == true ||
                            user.branch?.lowercase()?.contains("ui") == true ||
                            user.skills?.any { s -> listOf("design", "ui", "ux", "art", "creative", "figma").any { s.lowercase().contains(it) } } == true ||
                            user.userType.lowercase().contains("design")
                "health" -> user.branch?.lowercase()?.contains("health") == true ||
                            user.branch?.lowercase()?.contains("medical") == true ||
                            user.branch?.lowercase()?.contains("bio") == true ||
                            user.userType.lowercase().contains("medical")
                "business" -> user.branch?.lowercase()?.contains("business") == true ||
                              user.branch?.lowercase()?.contains("management") == true ||
                              user.branch?.lowercase()?.contains("mba") == true ||
                              user.branch?.lowercase()?.contains("finance") == true ||
                              user.userType.lowercase().contains("management")
                else -> true
            }

            matchesQuery && matchesCategory
        }

        updateGrid()
    }

    private fun updateGrid() {
        if (_binding == null) return
        binding.tvEmptySearch.visibility = if (filteredUsers.isEmpty()) View.VISIBLE else View.GONE
        studentAdapter.submitList(filteredUsers)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pulseListener?.remove()
        _binding = null
    }
}
