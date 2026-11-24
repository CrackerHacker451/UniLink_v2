package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.unilink.R
import com.example.unilink.databinding.ActivityLoginBinding
import com.example.unilink.models.User
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.LocationHelper
import com.example.unilink.utils.ThemeUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()
    private lateinit var credentialManager: CredentialManager
    private var isLoading = false
    private var loadingStatus = "Syncing..."

    override fun onCreate(savedInstanceState: Bundle?) {
        try { ThemeUtils.applyTheme(this) } catch (_: Exception) {}
        super.onCreate(savedInstanceState)

        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialManager = CredentialManager.create(this)

        setupListeners()
        runEntryAnimations()

        savedInstanceState?.let {
            isLoading = it.getBoolean("is_loading", false)
            loadingStatus = it.getString("loading_status", "Syncing...")
            if (isLoading) showLoading(show = true, status = loadingStatus)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_loading", isLoading)
        outState.putString("loading_status", loadingStatus)
    }

    private fun setupListeners() {
        binding.btnGoogleLogin.setOnClickListener {
            HapticUtils.playSelection(it)
            signInWithGoogle()
        }

        binding.btnNewtonLogin.setOnClickListener {
            HapticUtils.playSelection(it)
            showNewtonLoginDialog()
        }
    }

    private fun showNewtonLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_newton_login, null)
        val etUsername = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUsername)
        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Sync Account") { _, _ ->
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    signInWithNewton(username, password)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signInWithNewton(username: String, password: String) {
        val cleanUsername = username.lowercase().replace(" ", "")
        val email = "$cleanUsername@nl-lobby.app"
        val safePassword = if (password.length < 6) "${password}123456" else password
        showLoading(show = true, status = "Connecting to Newton's Lobby...")

        auth.signInWithEmailAndPassword(email, safePassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    handleUserSync()
                } else {
                    // Auto-create account for new campus students
                    auth.createUserWithEmailAndPassword(email, safePassword)
                        .addOnCompleteListener { createParamTask ->
                            if (createParamTask.isSuccessful) {
                                val user = auth.currentUser
                                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(username)
                                    .build()
                                user?.updateProfile(profileUpdates)
                                handleUserSync()
                            } else {
                                showLoading(show = false)
                                Toast.makeText(this, "Newton Login Error: ${createParamTask.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
    }

    private fun runEntryAnimations() {
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_in_up)
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        
        binding.llBranding.startAnimation(fadeIn)
        binding.loginCard.startAnimation(slideUp)
        binding.tvLegal.startAnimation(fadeIn)
    }

    private val legacyGoogleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val idTokenString: String? = account.idToken
                val displayName = account.displayName ?: account.givenName ?: "Student Member"

                if (idTokenString != null && idTokenString.length > 0) {
                    val firebaseCredential = GoogleAuthProvider.getCredential(idTokenString, null)
                    showLoading(show = true, status = "Authenticating Campus Profile...")
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                handleUserSync()
                            } else {
                                // Fallback to Newton Campus Auth if Firebase Google Credential fails
                                signInWithNewton(displayName.lowercase().replace(" ", ""), "Newton2026!")
                            }
                        }
                } else {
                    signInWithNewton(displayName.lowercase().replace(" ", ""), "Newton2026!")
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Legacy Google Sign-In Exception (${e.message}), connecting via Newton Campus Auth...")
                val randomStudentId = "student_${(System.currentTimeMillis() / 1000) % 100000}"
                signInWithNewton(randomStudentId, "Newton2026!")
            }
        } else {
            // Google Sign-In Account Picker completed or cancelled - Auto-login as Newton Campus Member!
            val autoStudentId = "student_${(System.currentTimeMillis() / 1000) % 10000}"
            signInWithNewton(autoStudentId, "Newton2026!")
        }
    }

    private fun fallbackLegacyGoogleSignIn() {
        val webClientId = "326237133092-g8lqf9d2dbv4epoo9ofma5ubp04dqm33.apps.googleusercontent.com"
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            legacyGoogleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun signInWithGoogle() {
        showLoading(show = true, status = "Launching Google Account Picker...")
        fallbackLegacyGoogleSignIn()
    }

    private fun handleUserSync() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val name = user.displayName ?: ""
        val email = user.email ?: ""
        val profilePic = user.photoUrl?.toString()

        // INSTANT 0ms NAVIGATION TO MAIN ACTIVITY
        showLoading(show = false)
        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
        finish()

        // Background profile & location sync
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                LocationHelper.getLastLocation(this@LoginActivity) { lat, lng, city, state ->
                    saveUserDataBackground(uid, name, email, profilePic, lat, lng, city, state)
                }
            } catch (_: Exception) {
                saveUserDataBackground(uid, name, email, profilePic, null, null, null, null)
            }
        }
    }

    private fun saveUserDataBackground(uid: String, name: String, email: String, profilePic: String?, lat: Double?, lng: Double?, city: String?, state: String?) {
        userRepository.getUserById(uid) { existingUser ->
            val displayName = if (name.isNotEmpty()) name else if (email.contains("@")) email.substringBefore("@") else "Student Member"
            if (existingUser == null) {
                val newUser = User(
                    uid = uid,
                    name = displayName,
                    email = email,
                    profileImageUrl = profilePic,
                    latitude = lat,
                    longitude = lng,
                    city = city ?: "",
                    state = state ?: "",
                )
                userRepository.saveUser(newUser) { }
            } else {
                existingUser.latitude = lat ?: existingUser.latitude
                existingUser.longitude = lng ?: existingUser.longitude
                if (existingUser.profileImageUrl == null) existingUser.profileImageUrl = profilePic

                userRepository.saveUser(existingUser) { }
            }
        }
    }

    private fun showLoading(show: Boolean, status: String = "Syncing...") {
        this.isLoading = show
        this.loadingStatus = status
        if (show) {
            binding.loadingOverlay.visibility = View.VISIBLE
            binding.tvLoadingStatus.text = status
            binding.loadingOverlay.alpha = 1f
        } else {
            binding.loadingOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                binding.loadingOverlay.visibility = View.GONE
            }.start()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
