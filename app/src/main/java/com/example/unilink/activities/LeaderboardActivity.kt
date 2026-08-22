package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.adapters.LeaderboardAdapter
import com.example.unilink.databinding.ActivityLeaderboardBinding
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.ThemeUtils
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private val userRepository = UserRepository()

    private var currentFilter = "XP"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)
        
        loadLeaderboard(false)

        binding.toolbar.menu.add("Most Trusted").setOnMenuItemClickListener {
            val isXp = currentFilter == "XP"
            currentFilter = if (isXp) "Trust" else "XP"
            loadLeaderboard(it.title == "Switch to Global") // Keep premium filter if applicable
            it.title = if (isXp) "Sort by XP" else "Most Trusted"
            true
        }

        // VIP Toggle for Premium Users
        userRepository.getCurrentUser { user ->
            if (user?.isPremium == true) {
                binding.toolbar.menu.add("Switch to VIP").setOnMenuItemClickListener {
                    val isShowingVip = it.title == "Switch to Global"
                    loadLeaderboard(!isShowingVip)
                    it.title = if (!isShowingVip) "Switch to Global" else "Switch to VIP"
                    true
                }
            }
        }
    }

    private fun loadLeaderboard(onlyPremium: Boolean) {
        userRepository.getAllUsers { users ->
            if (isFinishing || isDestroyed) return@getAllUsers
            val filtered = if (onlyPremium) users.filter { it.isPremium } else users
            
            val sortedUsers = if (currentFilter == "Trust") {
                filtered.sortedByDescending { it.vouchCount }
            } else {
                filtered.sortedByDescending { it.totalXp }
            }
            
            binding.tvEmptyLeaderboard.visibility = if (sortedUsers.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

            // Populate Podium
            if (sortedUsers.size >= 3) {
                binding.podiumLayout.visibility = android.view.View.VISIBLE
                
                // Winner
                val user1 = sortedUsers[0]
                binding.tvName1.text = user1.name
                binding.ivRank1.load(user1.profileImageUrl ?: R.drawable.ic_profile_placeholder) { transformations(CircleCropTransformation()) }
                
                // Rank 2
                val user2 = sortedUsers[1]
                binding.tvName2.text = user2.name
                binding.ivRank2.load(user2.profileImageUrl ?: R.drawable.ic_profile_placeholder) { transformations(CircleCropTransformation()) }

                // Rank 3
                val user3 = sortedUsers[2]
                binding.tvName3.text = user3.name
                binding.ivRank3.load(user3.profileImageUrl ?: R.drawable.ic_profile_placeholder) { transformations(CircleCropTransformation()) }
                
                // Remove top 3 from list to avoid duplication
                binding.rvLeaderboard.adapter = LeaderboardAdapter(sortedUsers.drop(3), currentFilter == "Trust") { user ->
                    val intent = Intent(this, ProfileActivity::class.java).apply {
                        putExtra("userId", user.uid)
                    }
                    startActivity(intent)
                }
            } else {
                binding.podiumLayout.visibility = android.view.View.GONE
                binding.rvLeaderboard.adapter = LeaderboardAdapter(sortedUsers, currentFilter == "Trust") { user ->
                    val intent = Intent(this, ProfileActivity::class.java).apply {
                        putExtra("userId", user.uid)
                    }
                    startActivity(intent)
                }
            }
        }
    }
}
