package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.example.unilink.R
import com.example.unilink.adapters.StudentGridAdapter
import com.example.unilink.databinding.ActivityNearbyStudentsBinding
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.ThemeUtils

class NearbyStudentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNearbyStudentsBinding
    private val userRepository = UserRepository()
    private lateinit var adapter: StudentGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyStudentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = systemBars.top)
            binding.rvNearby.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        startScanning()

        binding.btnGlobalSearch.setOnClickListener {
            binding.btnGlobalSearch.visibility = View.GONE
            binding.tvEmpty.visibility = View.GONE
            binding.viewRadarPulse.visibility = View.VISIBLE
            val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.viewRadarPulse.startAnimation(pulse)
            
            userRepository.getAllUsers { users ->
                binding.viewRadarPulse.clearAnimation()
                binding.viewRadarPulse.visibility = View.GONE
                binding.tvStatus.text = "Discovering top students globally..."
                adapter.submitList(users.take(15))
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = StudentGridAdapter { user ->
            val intent = Intent(this, ProfileActivity::class.java).apply {
                putExtra("userId", user.uid)
            }
            startActivity(intent)
        }
        binding.rvNearby.layoutManager = GridLayoutManager(this, 2)
        binding.rvNearby.adapter = adapter
    }

    private fun startScanning() {
        binding.viewRadarPulse.visibility = View.VISIBLE
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.viewRadarPulse.startAnimation(pulse)

        userRepository.getNearbyUsers { nearbyList ->
            if (isFinishing || isDestroyed) return@getNearbyUsers
            
            binding.viewRadarPulse.clearAnimation()
            binding.viewRadarPulse.visibility = View.GONE
            
            if (nearbyList.isEmpty()) {
                binding.tvStatus.text = "No students found nearby."
                binding.tvEmpty.visibility = View.VISIBLE
                binding.btnGlobalSearch.visibility = View.VISIBLE
            } else {
                binding.tvStatus.text = "Found ${nearbyList.size} students within 10km"
                adapter.submitList(nearbyList.map { it.first })
            }
        }
    }
}
