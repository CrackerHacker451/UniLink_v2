package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.R
import com.example.unilink.adapters.LeaderboardAdapter
import com.example.unilink.databinding.ActivityLeaderboardBinding
import com.example.unilink.models.User
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.ImageLoaderUtils
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()

    private var currentFilter = "XP" // XP, Trust, Coins

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvLeaderboard.apply {
            layoutManager = LinearLayoutManager(this@LeaderboardActivity)
            setHasFixedSize(true)
            setItemViewCacheSize(25)
        }

        setupChipFilters()
        loadLeaderboard()
    }

    private fun setupChipFilters() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                HapticUtils.playSelection(binding.chipGroupFilter)
            }
            currentFilter = when {
                binding.chipTrust.isChecked -> "Trust"
                binding.chipCoins.isChecked -> "Coins"
                else -> "XP"
            }
            loadLeaderboard()
        }
    }

    private fun loadLeaderboard() {
        val currentUid = auth.currentUser?.uid ?: ""
        userRepository.getAllUsers { fetchedUsers ->
            if (isFinishing || isDestroyed) return@getAllUsers

            val mutableUsers = fetchedUsers.toMutableList()
            if (currentUid.isNotEmpty() && mutableUsers.none { it.uid == currentUid }) {
                userRepository.getCurrentUser { myUser ->
                    if (myUser != null && mutableUsers.none { it.uid == currentUid }) {
                        mutableUsers.add(myUser)
                    }
                    renderLeaderboardUI(mutableUsers, currentUid)
                }
            } else {
                renderLeaderboardUI(mutableUsers, currentUid)
            }
        }
    }

    private fun renderLeaderboardUI(users: List<User>, currentUid: String) {
        val sortedUsers = when (currentFilter) {
            "Trust" -> users.sortedByDescending { it.vouchCount }
            "Coins" -> users.sortedByDescending { it.uniCoins }
            else -> users.sortedByDescending { it.totalXp }
        }

        binding.tvEmptyLeaderboard.visibility = if (sortedUsers.isEmpty()) View.VISIBLE else View.GONE

        if (sortedUsers.isNotEmpty()) {
            populatePersonalRankCard(sortedUsers)
        } else {
            binding.cardMyRank.visibility = View.GONE
        }

        // Populate Podium
        if (sortedUsers.size >= 3) {
            binding.podiumLayout.visibility = View.VISIBLE

            // Winner Rank 1
            val user1 = sortedUsers[0]
            binding.tvName1.text = user1.name.ifEmpty { "Winner" }
            binding.tvScore1.text = getFormattedScore(user1)
            ImageLoaderUtils.loadSmartImage(binding.ivRank1, user1.profileImageUrl, R.drawable.ic_profile_placeholder, isCircle = true, fallbackName = user1.name)
            binding.podiumRank1.setOnClickListener { openUserProfile(user1.uid) }

            // Rank 2
            val user2 = sortedUsers[1]
            binding.tvName2.text = user2.name.ifEmpty { "Rank 2" }
            binding.tvScore2.text = getFormattedScore(user2)
            ImageLoaderUtils.loadSmartImage(binding.ivRank2, user2.profileImageUrl, R.drawable.ic_profile_placeholder, isCircle = true, fallbackName = user2.name)
            binding.podiumRank2.setOnClickListener { openUserProfile(user2.uid) }

            // Rank 3
            val user3 = sortedUsers[2]
            binding.tvName3.text = user3.name.ifEmpty { "Rank 3" }
            binding.tvScore3.text = getFormattedScore(user3)
            ImageLoaderUtils.loadSmartImage(binding.ivRank3, user3.profileImageUrl, R.drawable.ic_profile_placeholder, isCircle = true, fallbackName = user3.name)
            binding.podiumRank3.setOnClickListener { openUserProfile(user3.uid) }

            // Remaining ranks (4+)
            binding.rvLeaderboard.adapter = LeaderboardAdapter(
                users = sortedUsers.drop(3),
                startRankOffset = 4,
                filterType = currentFilter,
                currentUserId = currentUid
            ) { user -> openUserProfile(user.uid) }
        } else {
            binding.podiumLayout.visibility = View.GONE
            binding.rvLeaderboard.adapter = LeaderboardAdapter(
                users = sortedUsers,
                startRankOffset = 1,
                filterType = currentFilter,
                currentUserId = currentUid
            ) { user -> openUserProfile(user.uid) }
        }
    }

    private fun getFormattedScore(user: User): String {
        return when (currentFilter) {
            "Trust" -> "⭐ ${user.vouchCount}"
            "Coins" -> "💰 ${user.uniCoins}"
            else -> "⚡ ${user.totalXp} XP"
        }
    }

    private fun populatePersonalRankCard(allSortedUsers: List<User>) {
        val currentUid = auth.currentUser?.uid ?: return
        val myRankIndex = allSortedUsers.indexOfFirst { it.uid == currentUid }

        if (myRankIndex != -1) {
            val myUser = allSortedUsers[myRankIndex]
            val myRankNumber = myRankIndex + 1

            binding.cardMyRank.visibility = View.VISIBLE
            binding.tvMyRankNumber.text = "#$myRankNumber"
            binding.tvMyName.text = "${myUser.name} (You)"
            binding.tvMyScore.text = getFormattedScore(myUser)
            ImageLoaderUtils.loadSmartImage(binding.ivMyAvatar, myUser.profileImageUrl, R.drawable.ic_profile_placeholder, isCircle = true, fallbackName = myUser.name)

            binding.tvMyRankSubtitle.text = when (myRankNumber) {
                1 -> "🏆 You are #1 on Campus! Legend!"
                2, 3 -> "🥇 Top 3 Leader! Keep shining!"
                else -> "Rank #$myRankNumber • Keep grinding to reach Top 3! 🚀"
            }

            binding.cardMyRank.setOnClickListener {
                openUserProfile(currentUid)
            }
        } else {
            binding.cardMyRank.visibility = View.GONE
        }
    }

    private fun openUserProfile(uid: String) {
        val intent = Intent(this, ProfileActivity::class.java).apply {
            putExtra("userId", uid)
        }
        startActivity(intent)
    }
}
