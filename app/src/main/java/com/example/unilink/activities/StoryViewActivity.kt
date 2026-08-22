package com.example.unilink.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.unilink.R
import com.example.unilink.databinding.ActivityStoryViewBinding

class StoryViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoryViewBinding
    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0
    
    private val progressRunnable = object : Runnable {
        override fun run() {
            progress += 1
            binding.storyProgress.progress = progress
            if (progress < 100) {
                handler.postDelayed(this, 50)
            } else {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoryViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userName = intent.getStringExtra("userName") ?: "Student"
        val imageUrl = intent.getStringExtra("imageUrl")
        
        binding.tvUserName.text = userName
        
        if (!imageUrl.isNullOrEmpty()) {
            binding.ivStory.load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_mesh_gradient)
            }
        } else {
            binding.ivStory.setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
        }

        binding.btnClose.setOnClickListener { finish() }
        
        handler.postDelayed(progressRunnable, 50)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
    }
}
