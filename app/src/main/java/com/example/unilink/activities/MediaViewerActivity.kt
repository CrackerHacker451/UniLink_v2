package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.unilink.R
import com.example.unilink.databinding.ActivityMediaViewerBinding
import com.example.unilink.repository.GeminiRepository
import com.example.unilink.utils.ImageLoaderUtils
import kotlinx.coroutines.launch

class MediaViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaViewerBinding
    private val geminiRepository = GeminiRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val url = intent.getStringExtra("mediaUrl")
        val title = intent.getStringExtra("title") ?: "Shared Media"
        val subtitle = intent.getStringExtra("subtitle")

        binding.tvMediaTitle.text = title
        binding.tvMediaSub.text = subtitle

        if (!url.isNullOrEmpty()) {
            ImageLoaderUtils.loadSmartImage(
                binding.ivFullscreenMedia,
                url,
                R.drawable.bg_chat_img_placeholder
            )

            binding.btnAiAnalyze.setOnClickListener {
                analyzeWithAi(url)
            }
        } else {
            Toast.makeText(this, "No media content available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun analyzeWithAi(url: String) {
        binding.btnAiAnalyze.isEnabled = false
        binding.btnAiAnalyze.text = "UniAI is thinking..."
        
        lifecycleScope.launch {
            try {
                val bitmap: android.graphics.Bitmap? = if (url.startsWith("data:image/") || url.startsWith("data:")) {
                    val base64Data = if (url.contains("base64,")) url.substringAfter("base64,") else url
                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    val request = ImageRequest.Builder(this@MediaViewerActivity)
                        .data(url)
                        .allowHardware(false) 
                        .build()
                    
                    val result = (imageLoader.execute(request) as? SuccessResult)?.drawable
                    (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                }

                if (bitmap != null) {
                    val response = geminiRepository.analyzeImage(bitmap)
                    androidx.appcompat.app.AlertDialog.Builder(this@MediaViewerActivity)
                        .setTitle("UniAI Vision 🤖")
                        .setMessage(response ?: "I couldn't analyze this image.")
                        .setPositiveButton("Got it", null)
                        .show()
                } else {
                    Toast.makeText(this@MediaViewerActivity, "Failed to process image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MediaViewerActivity, "AI Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnAiAnalyze.isEnabled = true
                binding.btnAiAnalyze.text = "AI: Analyze this image"
            }
        }
    }
}
