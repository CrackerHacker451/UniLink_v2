package com.example.unilink.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.unilink.databinding.ActivityMediaViewerBinding
import com.example.unilink.repository.GeminiRepository
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

        url?.let { mediaUrl ->
            binding.ivFullscreenMedia.load(mediaUrl) {
                crossfade(true)
            }

            binding.btnAiAnalyze.setOnClickListener {
                analyzeWithAi(mediaUrl)
            }
        }
        
        // Block screenshots in media viewer too
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun analyzeWithAi(url: String) {
        binding.btnAiAnalyze.isEnabled = false
        binding.btnAiAnalyze.text = "UniAI is thinking..."
        
        lifecycleScope.launch {
            try {
                val request = ImageRequest.Builder(this@MediaViewerActivity)
                    .data(url)
                    .allowHardware(false) 
                    .build()
                
                val result = (imageLoader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap

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
