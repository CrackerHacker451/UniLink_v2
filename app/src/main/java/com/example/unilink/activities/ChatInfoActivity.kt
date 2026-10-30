package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.unilink.R
import com.example.unilink.databinding.ActivityChatInfoBinding
import com.example.unilink.repository.UserRepository
import java.text.SimpleDateFormat
import java.util.Locale

class ChatInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatInfoBinding
    private val userRepository = UserRepository()
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

        binding.collapsingToolbar.title = chatName ?: getString(R.string.user_info)

        userId?.let { fetchUserDetails(it) }
        setupActions()
    }

    private fun fetchUserDetails(uid: String) {
        userRepository.getUserById(uid) { user ->
            if (isFinishing || isDestroyed) return@getUserById
            user?.let {
                binding.tvBio.text = it.bio.ifEmpty { getString(R.string.bio_default) }
                
                it.createdAt?.let { date ->
                    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    binding.tvBioDate.text = getString(R.string.joined_date_format, sdf.format(date))
                }

                // Check block status to update UI
                userRepository.getCurrentUser { currentUser ->
                    if (isFinishing || isDestroyed) return@getCurrentUser
                    val isBlocked = currentUser?.blockedUsers?.contains(uid) == true
                    binding.btnBlock.text = if (isBlocked) getString(R.string.unblock_user) else getString(R.string.block_user)
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
                                Toast.makeText(this, getString(R.string.user_unblocked), Toast.LENGTH_SHORT).show()
                                binding.btnBlock.text = getString(R.string.block_user)
                            }
                        }
                    } else {
                        userRepository.blockUser(uid) { success ->
                            if (success && !isFinishing && !isDestroyed) {
                                Toast.makeText(this, getString(R.string.user_blocked), Toast.LENGTH_SHORT).show()
                                binding.btnBlock.text = getString(R.string.unblock_user)
                            }
                        }
                    }
                }
            }
        }

        binding.btnReport.setOnClickListener {
            Toast.makeText(this, getString(R.string.user_reported), Toast.LENGTH_SHORT).show()
        }
    }
}
