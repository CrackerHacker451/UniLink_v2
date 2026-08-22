package com.example.unilink.activities

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.unilink.databinding.ActivityCreatePostBinding
import com.example.unilink.repository.PostRepository
import com.example.unilink.repository.StoryRepository
import com.example.unilink.utils.ThemeUtils

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private val postRepository = PostRepository()
    private val storyRepository = StoryRepository()
    private val userRepository = com.example.unilink.repository.UserRepository()
    private val storageRepository = com.example.unilink.repository.StorageRepository()
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            binding.cardPremiumPhoto.visibility = android.view.View.VISIBLE
            binding.ivPostImagePreview.load(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val type = intent.getStringExtra("type") ?: "post"
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = if (type == "story") "Post Status" else "Create Post"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnPost.text = if (type == "story") "Post Status" else "Post"
        binding.etPostContent.hint = if (type == "story") "Type your status..." else "What's on your mind?"

        binding.btnAddPhoto.setOnClickListener {
            userRepository.getCurrentUser { user ->
                if (isFinishing || isDestroyed) return@getCurrentUser
                if (user?.isPremium == true) {
                    pickImageLauncher.launch("image/*")
                } else {
                    Toast.makeText(this, "Adding photos is a Premium feature! ✨", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            binding.cardPremiumPhoto.visibility = android.view.View.GONE
        }

        binding.btnPost.setOnClickListener {
            val content = binding.etPostContent.text.toString().trim()
            if (content.isNotEmpty() || selectedImageUri != null) {
                binding.btnPost.isEnabled = false
                binding.btnPost.text = "Publishing..."
                
                if (selectedImageUri != null) {
                    storageRepository.uploadPostImage(selectedImageUri!!) { url ->
                        if (url != null) {
                            performPost(type, content, url)
                        } else {
                            binding.btnPost.isEnabled = true
                            binding.btnPost.text = if (type == "story") "Post Status" else "Post"
                            Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    performPost(type, content, null)
                }
            }
        }
    }

    private fun performPost(type: String, content: String, imageUrl: String?) {
        if (type == "story") {
            // For stories we use content as title/text and imageUrl if provided
            storyRepository.uploadStory(imageUrl ?: content) { success ->
                if (isFinishing || isDestroyed) return@uploadStory
                binding.btnPost.isEnabled = true
                binding.btnPost.text = "Post Status"
                if (success) {
                    Toast.makeText(this, "Status Posted!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } else {
            postRepository.createPost(content, imageUrl) { success ->
                if (isFinishing || isDestroyed) return@createPost
                binding.btnPost.isEnabled = true
                binding.btnPost.text = "Post"
                if (success) {
                    Toast.makeText(this, "Posted!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Failed to post", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
