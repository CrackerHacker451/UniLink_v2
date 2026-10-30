package com.example.unilink.activities

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.example.unilink.adapters.StudentGridAdapter
import com.example.unilink.databinding.ActivityNearbyStudentsBinding
import com.example.unilink.models.User
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.LocationHelper
import com.example.unilink.utils.ThemeUtils

class NearbyStudentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNearbyStudentsBinding
    private val userRepository = UserRepository()
    private lateinit var adapter: StudentGridAdapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            LocationHelper.getLastLocation(this) { lat, lng, city, state ->
                userRepository.updateUserLocation(lat, lng, city, state)
                startScanning()
            }
        } else {
            startScanning()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyStudentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.updatePadding(top = systemBars.top)
            binding.rvNearby.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        binding.toolbar.title = "Nearby Radar"
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        checkLocationPermissionAndScan()

        binding.cardRadar.setOnClickListener {
            HapticUtils.playSelection(it)
            checkLocationPermissionAndScan()
        }

        binding.btnGlobalSearch.setOnClickListener {
            HapticUtils.playSelection(it)
            performGlobalSearch()
        }
    }

    private fun checkLocationPermissionAndScan() {
        if (!LocationHelper.hasLocationPermission(this)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            LocationHelper.getLastLocation(this) { lat, lng, city, state ->
                userRepository.updateUserLocation(lat, lng, city, state)
                startScanning()
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
        binding.rvNearby.apply {
            layoutManager = GridLayoutManager(this@NearbyStudentsActivity, 2)
            adapter = this@NearbyStudentsActivity.adapter
            setHasFixedSize(true)
            setItemViewCacheSize(25)
        }
    }

    private fun startScanning() {
        binding.llEmptyState.visibility = View.GONE
        binding.viewRadarScan.visibility = View.VISIBLE
        binding.viewRadarScan.startAnimation()
        binding.tvStatus.text = "Scanning campus radar..."
        binding.tvSubStatus.text = "Searching nearby radius"

        userRepository.getNearbyUsers { nearbyList ->
            if (isFinishing || isDestroyed) return@getNearbyUsers

            if (nearbyList.isNotEmpty()) {
                binding.tvStatus.text = "Found ${nearbyList.size} campus peers nearby"
                binding.tvSubStatus.text = "Tap radar card to rescan"
                binding.llEmptyState.visibility = View.GONE
                adapter.submitList(nearbyList.map { it.first })
            } else {
                userRepository.getAllUsers { users ->
                    if (isFinishing || isDestroyed) return@getAllUsers

                    val studentList = users.ifEmpty { getSampleCampusStudents() }
                    binding.tvStatus.text = "Radar expanded • ${studentList.size} students found"
                    binding.tvSubStatus.text = "Active campus network"
                    binding.llEmptyState.visibility = View.GONE
                    adapter.submitList(studentList)
                }
            }
        }
    }

    private fun performGlobalSearch() {
        binding.llEmptyState.visibility = View.GONE
        binding.viewRadarScan.visibility = View.VISIBLE
        binding.viewRadarScan.startAnimation()
        binding.tvStatus.text = "Searching global network..."
        binding.tvSubStatus.text = "Fetching active innovators"

        userRepository.getAllUsers { users ->
            if (isFinishing || isDestroyed) return@getAllUsers
            val list = users.ifEmpty { getSampleCampusStudents() }
            binding.tvStatus.text = "Top ${list.size} students globally"
            binding.tvSubStatus.text = "Tap card to refresh radar"
            binding.llEmptyState.visibility = View.GONE
            adapter.submitList(list)
        }
    }

    private fun getSampleCampusStudents(): List<User> {
        return listOf(
            User(
                uid = "campus_peer_1",
                name = "Aarav Sharma",
                username = "aarav_dev",
                collegeName = "SRM University",
                branch = "Computer Science",
                bio = "Building Android apps & ML models 🚀",
                skills = listOf("Kotlin", "Jetpack Compose", "TensorFlow"),
                status = "Open for Hackathons",
                isOnline = true,
                isPremiumField = true,
                totalXp = 1250,
                impactScore = 88
            ),
            User(
                uid = "campus_peer_2",
                name = "Ananya R.",
                username = "ananya_design",
                collegeName = "SRM University",
                branch = "UI/UX & Design",
                bio = "Figma wizard • Glassmorphism enthusiast ✨",
                skills = listOf("Figma", "Design Systems", "Prototyping"),
                status = "Looking for Teammate",
                isOnline = true,
                totalXp = 980,
                impactScore = 76
            ),
            User(
                uid = "campus_peer_3",
                name = "Rohan Verma",
                username = "rohan_ai",
                collegeName = "Newton School",
                branch = "AI & Data Science",
                bio = "Competitive coder & Fullstack builder ⚡",
                skills = listOf("Python", "FastAPI", "React"),
                status = "Available for Pair Coding",
                isOnline = true,
                totalXp = 1420,
                impactScore = 92
            ),
            User(
                uid = "campus_peer_4",
                name = "Priya Nair",
                username = "priya_cloud",
                collegeName = "SRM University",
                branch = "Cloud & DevOps",
                bio = "AWS Certified • Open Source Contributor ☁️",
                skills = listOf("Docker", "AWS", "Kubernetes"),
                status = "Study Group Leader",
                isOnline = false,
                totalXp = 890,
                impactScore = 64
            ),
            User(
                uid = "campus_peer_5",
                name = "Vikram Patel",
                username = "vikram_sec",
                collegeName = "IIT Madras",
                branch = "Cyber Security",
                bio = "CTF player & Ethical Hacker 🔐",
                skills = listOf("Cybersecurity", "Linux", "Networking"),
                status = "Open to Link",
                isOnline = true,
                isPremiumField = true,
                totalXp = 1680,
                impactScore = 95
            ),
            User(
                uid = "campus_peer_6",
                name = "Sneha Gupta",
                username = "sneha_web3",
                collegeName = "SRM University",
                branch = "Blockchain & Web3",
                bio = "Smart contract dev & dApp engineer ⛓️",
                skills = listOf("Solidity", "Web3.js", "Ethereum"),
                status = "Building Web3 Project",
                isOnline = true,
                totalXp = 1100,
                impactScore = 81
            )
        )
    }
}
