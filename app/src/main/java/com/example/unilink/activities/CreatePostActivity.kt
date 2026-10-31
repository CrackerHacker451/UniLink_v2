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
    private val storageRepository = com.example.unilink.repository.StorageRepository()
    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            binding.cardPremiumPhoto.visibility = android.view.View.VISIBLE
            binding.ivPostImagePreview.load(it)
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            selectedImageUri = cameraImageUri
            binding.cardPremiumPhoto.visibility = android.view.View.VISIBLE
            binding.ivPostImagePreview.load(cameraImageUri)
            Toast.makeText(this, "Photo captured! Tap Edit & Filter to enhance. ✨", Toast.LENGTH_SHORT).show()
        }
    }

    private val editImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val editedUriString = result.data?.getStringExtra("editedImageUri")
            if (!editedUriString.isNullOrEmpty()) {
                val editedUri = Uri.parse(editedUriString)
                selectedImageUri = editedUri
                binding.ivPostImagePreview.load(editedUri)
                Toast.makeText(this, "Enhanced photo applied! ✨", Toast.LENGTH_SHORT).show()
            }
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
            pickImageLauncher.launch("image/*")
        }

        binding.btnCameraPhoto.setOnClickListener {
            try {
                val cacheDir = java.io.File(cacheDir, "camera_photos")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val tempFile = java.io.File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
                cameraImageUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEditPhoto.setOnClickListener {
            selectedImageUri?.let { uri ->
                val intent = android.content.Intent(this, ImageEditorActivity::class.java).apply {
                    putExtra("imageUri", uri.toString())
                }
                editImageLauncher.launch(intent)
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
            val storyContent = imageUrl ?: content
            if (storyContent.isNotEmpty()) {
                storyRepository.uploadStory(storyContent) { success ->
                    if (isFinishing || isDestroyed) return@uploadStory
                    binding.btnPost.isEnabled = true
                    binding.btnPost.text = "Post Status"
                    if (success) {
                        Toast.makeText(this, "Status Posted!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to post status", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.btnPost.isEnabled = true
                binding.btnPost.text = "Post Status"
                Toast.makeText(this, "Please enter status text or attach an image", Toast.LENGTH_SHORT).show()
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
