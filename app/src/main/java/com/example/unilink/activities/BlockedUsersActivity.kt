package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.adapters.BlockedUserAdapter
import com.example.unilink.databinding.ActivityBlockedUsersBinding
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.ThemeUtils

class BlockedUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedUsersBinding
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvBlockedUsers.layoutManager = LinearLayoutManager(this)
        loadBlockedUsers()
    }

    private fun loadBlockedUsers() {
        userRepository.getCurrentUser { currentUser ->
            val blockedIds = currentUser?.blockedUsers ?: emptyList()
            if (blockedIds.isEmpty()) {
                binding.rvBlockedUsers.adapter = BlockedUserAdapter(emptyList()) {}
                return@getCurrentUser
            }

            val blockedUsers = mutableListOf<com.example.unilink.models.User>()
            var count = 0
            blockedIds.forEach { id ->
                userRepository.getUserById(id) { user ->
                    user?.let { blockedUsers.add(it) }
                    count++
                    if (count == blockedIds.size) {
                        binding.rvBlockedUsers.adapter = BlockedUserAdapter(blockedUsers) { targetUser ->
                            userRepository.unblockUser(targetUser.uid) { success ->
                                if (success) {
                                    Toast.makeText(this, "Unblocked ${targetUser.name}", Toast.LENGTH_SHORT).show()
                                    loadBlockedUsers()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
