package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.unilink.databinding.ActivityChatInfoBinding
import com.example.unilink.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

class ChatInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatInfoBinding
    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra("userId")
        val chatName = intent.getStringExtra("chatName")

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.collapsingToolbar.title = chatName ?: "User Info"

        userId?.let { fetchUserDetails(it) }
        setupActions()
    }

    private fun fetchUserDetails(uid: String) {
        userRepository.getUserById(uid) { user ->
            if (isFinishing || isDestroyed) return@getUserById
            user?.let {
                binding.tvBio.text = if (it.bio.isNotEmpty()) it.bio else "Hey there! I am using UniLink."
                
                it.createdAt?.let { date ->
                    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    binding.tvBioDate.text = "Joined ${sdf.format(date)}"
                }

                // Check block status to update UI
                userRepository.getCurrentUser { currentUser ->
                    if (isFinishing || isDestroyed) return@getCurrentUser
                    val isBlocked = currentUser?.blockedUsers?.contains(uid) == true
                    binding.btnBlock.text = if (isBlocked) "Unblock User" else "Block User"
                }
            }
        }
    }

    private fun setupActions() {
        binding.btnBlock.setOnClickListener {
            userId?.let { uid ->
                userRepository.getCurrentUser { currentUser ->
                    if (isFinishing || isDestroyed) return@getCurrentUser
                    val isBlocked = currentUser?.blockedUsers?.contains(uid) == true
                    if (isBlocked) {
                        userRepository.unblockUser(uid) { success ->
                            if (success && !isFinishing && !isDestroyed) {
                                Toast.makeText(this, "User unblocked", Toast.LENGTH_SHORT).show()
                                binding.btnBlock.text = "Block User"
                            }
                        }
                    } else {
                        userRepository.blockUser(uid) { success ->
                            if (success && !isFinishing && !isDestroyed) {
                                Toast.makeText(this, "User blocked", Toast.LENGTH_SHORT).show()
                                binding.btnBlock.text = "Unblock User"
                            }
                        }
                    }
                }
            }
        }

        binding.btnReport.setOnClickListener {
            Toast.makeText(this, "User reported", Toast.LENGTH_SHORT).show()
        }
    }
}
