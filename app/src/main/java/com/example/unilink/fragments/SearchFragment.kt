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
import com.example.unilink.adapters.StudentGridAdapter
import com.example.unilink.databinding.FragmentSearchBinding
import com.example.unilink.models.User

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val userRepository = com.example.unilink.repository.UserRepository()
    
    private var allUsers = listOf<User>()
    private var filteredUsers = listOf<User>()
    private lateinit var studentAdapter: StudentGridAdapter

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
        setupSearchListener()
        setupCategoryChips()
        fetchUsers()

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
            R.id.chipEngineering -> "engineering"
            R.id.chipDesign -> "design"
            R.id.chipMedical -> "medical"
            R.id.chipManagement -> "management"
            else -> ""
        }

        filteredUsers = allUsers.filter { user ->
            val matchesQuery = if (query.isEmpty()) true 
                              else user.name.lowercase().contains(query) || 
                                   user.username.lowercase().contains(query) ||
                                   user.branch?.lowercase()?.contains(query) == true ||
                                   user.skills?.any { it.lowercase().contains(query) } == true
            
            val matchesCategory = if (category.isEmpty()) true 
                                 else user.userType.lowercase().contains(category) || 
                                      user.branch?.lowercase()?.contains(category) == true ||
                                      user.skills?.any { it.lowercase().contains(category) } == true
            
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
        _binding = null
    }
}
