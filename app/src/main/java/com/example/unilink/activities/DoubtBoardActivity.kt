package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.R
import com.example.unilink.adapters.DoubtAdapter
import com.example.unilink.databinding.ActivityDoubtBoardBinding
import com.example.unilink.models.Doubt
import com.example.unilink.repository.ChatRepository
import com.example.unilink.repository.DoubtRepository
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth

class DoubtBoardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoubtBoardBinding
    private val doubtRepository = DoubtRepository()
    private val chatRepository = ChatRepository()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var doubtAdapter: DoubtAdapter
    private var doubtsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var allDoubtsList = listOf<Doubt>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDoubtBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        setupSearch()
        loadDoubts()

        binding.swipeRefreshDoubts.setOnRefreshListener {
            loadDoubts()
            binding.swipeRefreshDoubts.postDelayed({
                binding.swipeRefreshDoubts.isRefreshing = false
            }, 1200)
        }

        binding.tabLayout.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                com.example.unilink.utils.HapticUtils.playSelection(binding.tabLayout)
                applyFilters()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupSearch() {
        binding.etSearchDoubt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        intent.getStringExtra("search_query")?.let { query ->
            binding.etSearchDoubt.setText(query)
            applyFilters()
        }
    }

    private fun applyFilters() {
        val query = binding.etSearchDoubt.text.toString().lowercase().trim()
        val onlyMine = binding.tabLayout.selectedTabPosition == 1
        
        val filtered = allDoubtsList.filter { doubt ->
            val matchesTab = if (onlyMine) doubt.userId == auth.currentUser?.uid else true
            val matchesQuery = doubt.question.lowercase().contains(query) || 
                              doubt.tags.any { it.lowercase().contains(query) } ||
                              doubt.userName.lowercase().contains(query)
            
            matchesTab && matchesQuery
        }
        
        doubtAdapter.submitList(filtered)
    }

    private fun setupRecyclerView() {
        doubtAdapter = DoubtAdapter { doubt -> answerDoubt(doubt) }
        binding.rvDoubts.layoutManager = LinearLayoutManager(this)
        binding.rvDoubts.adapter = doubtAdapter
    }

    private fun loadDoubts() {
        showSkeleton(true)
        doubtsListener?.remove()
        doubtsListener = doubtRepository.observeDoubts { allDoubts ->
            if (isFinishing || isDestroyed) return@observeDoubts
            showSkeleton(false)
            allDoubtsList = allDoubts
            applyFilters()
        }
    }

    private fun showSkeleton(show: Boolean) {
        if (show) {
            binding.rvDoubts.visibility = android.view.View.GONE
            binding.llSkeletonContainer.visibility = android.view.View.VISIBLE
            val shimmer = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shimmer_alpha)
            binding.llSkeletonContainer.startAnimation(shimmer)
        } else {
            binding.llSkeletonContainer.clearAnimation()
            binding.llSkeletonContainer.visibility = android.view.View.GONE
            binding.rvDoubts.visibility = android.view.View.VISIBLE
        }
    }

    private fun answerDoubt(doubt: Doubt) {
        val currentUid = auth.currentUser?.uid ?: return
        if (doubt.userId == currentUid) {
            Toast.makeText(this, "You cannot answer your own doubt", Toast.LENGTH_SHORT).show()
            return
        }
        
        val chatId = chatRepository.getChatId(currentUid, doubt.userId)
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chatId", chatId)
            putExtra("receiverId", doubt.userId)
            putExtra("chatName", doubt.userName)
            putExtra("isGroup", false)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        doubtsListener?.remove()
    }
}
