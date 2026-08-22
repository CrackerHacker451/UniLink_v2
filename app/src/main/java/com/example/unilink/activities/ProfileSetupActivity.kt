package com.example.unilink.activities

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
import com.example.unilink.databinding.ActivityProfileSetupBinding
import com.example.unilink.models.User
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.ThemeUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()
    private val storageRepository = com.example.unilink.repository.StorageRepository()
    private var existingUser: User? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLat: Double? = null
    private var userLng: Double? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            binding.ivSetupAvatar.load(it) {
                transformations(CircleCropTransformation())
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        loadExistingProfile()

        binding.rgUserType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == binding.rbCollege.id) {
                binding.llCollegeFields.visibility = View.VISIBLE
                binding.llSchoolFields.visibility = View.GONE
            } else {
                binding.llCollegeFields.visibility = View.GONE
                binding.llSchoolFields.visibility = View.VISIBLE
            }
        }

        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnGetLocation.setOnClickListener {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        startAnimations()
    }

    private fun getCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    userLat = it.latitude
                    userLng = it.longitude
                    updateCityStateFromLocation(it.latitude, it.longitude)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun updateCityStateFromLocation(lat: Double, lng: Double) {
        try {
            val geocoder = android.location.Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                val city = address.locality ?: address.subAdminArea ?: ""
                val state = address.adminArea ?: ""
                
                binding.etCity.setText(city)
                binding.etState.setText(state)
                Toast.makeText(this, "Location detected: $city, $state", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to get city/state name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAnimations() {
        val pulse = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.btnSaveProfile.startAnimation(pulse)
    }

    private fun loadExistingProfile() {
        val uid = auth.currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE
        userRepository.getUserById(uid) { user ->
            binding.progressBar.visibility = View.GONE
            if (user != null) {
                existingUser = user
                userLat = user.latitude
                userLng = user.longitude
                populateFields(user)
            }
        }
    }

    private fun populateFields(user: User) {
        binding.etFullName.setText(user.name)
        binding.etUsername.setText(user.username)
        binding.etBio.setText(user.bio)
        binding.etCity.setText(user.city)
        binding.etState.setText(user.state)
        binding.etPhone.setText(user.phoneNumber)
        binding.etSkills.setText(user.skills?.joinToString(", "))
        binding.etGoals.setText(user.goals)

        if (user.userType == "School") {
            binding.rbSchool.isChecked = true
            binding.etSchoolName.setText(user.schoolName)
        } else {
            binding.rbCollege.isChecked = true
            binding.etCollegeName.setText(user.collegeName)
            binding.etBranch.setText(user.branch)
            binding.etYear.setText(user.yearOrSemester)
        }
        
        binding.ivSetupAvatar.load(user.profileImageUrl ?: R.drawable.ic_profile_placeholder) {
            transformations(CircleCropTransformation())
        }
    }

    private fun saveProfile() {
        val fullName = binding.etFullName.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        
        if (fullName.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Name and Username are required", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: return
        val user = existingUser ?: User(uid = uid)
        
        user.name = fullName
        user.username = username
        user.bio = binding.etBio.text.toString().trim()
        user.city = binding.etCity.text.toString().trim()
        user.state = binding.etState.text.toString().trim()
        user.phoneNumber = binding.etPhone.text.toString().trim()
        user.goals = binding.etGoals.text.toString().trim()
        user.skills = binding.etSkills.text.toString().trim().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        user.latitude = userLat
        user.longitude = userLng
        
        if (binding.rbCollege.isChecked) {
            user.userType = "College"
            user.collegeName = binding.etCollegeName.text.toString().trim()
            user.branch = binding.etBranch.text.toString().trim()
            user.yearOrSemester = binding.etYear.text.toString().trim()
        } else {
            user.userType = "School"
            user.schoolName = binding.etSchoolName.text.toString().trim()
        }

        binding.progressBar.visibility = View.VISIBLE
        
        if (selectedImageUri != null) {
            storageRepository.uploadProfilePicture(uid, selectedImageUri!!) { url ->
                if (url != null) {
                    user.profileImageUrl = url
                    performSave(user)
                } else {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            performSave(user)
        }
    }

    private fun performSave(user: User) {
        userRepository.saveUser(user) { success ->
            binding.progressBar.visibility = View.GONE
            if (success) {
                Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
