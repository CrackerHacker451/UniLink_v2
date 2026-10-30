package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
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
        val email = "$username@nl-lobby.app"
        showLoading(show = true, status = "Verifying with Newton's Lobby...")
        
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    handleUserSync()
                } else {
                    showLoading(show = false)
                    Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
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

    private fun signInWithGoogle() {
        val webClientId = try {
            getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            "326237133092-g8lqf9d2dbv4epoo9ofma5ubp04dqm33.apps.googleusercontent.com"
        }

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts = false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(autoSelectEnabled = false)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                showLoading(show = true, status = "Launching Secure Gateway...")
                val result = credentialManager.getCredential(request = request, context = this@LoginActivity)
                val credential = result.credential
                
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val googleIdToken = googleIdTokenCredential.idToken
                    
                    if (googleIdToken.isEmpty()) throw Exception("Auth token empty")

                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    
                    showLoading(show = true, status = "Authenticating with Firebase...")
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                handleUserSync()
                            } else {
                                showLoading(show = false)
                                Toast.makeText(this@LoginActivity, "Auth Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                } catch (_: Exception) {
                    showLoading(show = false)
                    Toast.makeText(this@LoginActivity, "Credential mismatch", Toast.LENGTH_SHORT).show()
                }
            } catch (e: GetCredentialException) {
                showLoading(show = false)
                if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    Toast.makeText(this@LoginActivity, "System error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                showLoading(show = false)
                Toast.makeText(this@LoginActivity, "Failed to connect", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleUserSync() {
        showLoading(show = true, status = "Syncing Campus Profile...")
        val user = auth.currentUser ?: return
        val uid = user.uid
        val name = user.displayName ?: ""
        val email = user.email ?: ""
        val profilePic = user.photoUrl?.toString()

        var locationProcessed = false
        
        val onLocationResult: (Double?, Double?, String?, String?) -> Unit = { lat, lng, city, state ->
            if (!locationProcessed) {
                locationProcessed = true
                saveUserData(uid, name, email, profilePic, lat, lng, city, state)
            }
        }

        // Safety timeout to prevent infinite loading if location fails silently
        lifecycleScope.launch {
            delay(4.seconds)
            if (!locationProcessed) {
                Log.w("LoginActivity", "Location timeout")
                onLocationResult(null, null, null, null)
            }
        }

        try {
            LocationHelper.getLastLocation(this, onLocationResult)
        } catch (_: Exception) {
            onLocationResult(null, null, null, null)
        }
    }

    private fun saveUserData(uid: String, name: String, email: String, profilePic: String?, lat: Double?, lng: Double?, city: String?, state: String?) {
        userRepository.getUserById(uid) { existingUser ->
            if (existingUser == null) {
                val newUser = User(
                    uid = uid,
                    name = name,
                    email = email,
                    profileImageUrl = profilePic,
                    latitude = lat,
                    longitude = lng,
                    city = city ?: "",
                    state = state ?: "",
                )
                userRepository.saveUser(newUser) {
                    showLoading(show = false)
                    startActivity(Intent(this@LoginActivity, ProfileSetupActivity::class.java))
                    finish()
                }
            } else {
                existingUser.latitude = lat ?: existingUser.latitude
                existingUser.longitude = lng ?: existingUser.longitude
                if (existingUser.profileImageUrl == null) existingUser.profileImageUrl = profilePic
                
                userRepository.saveUser(existingUser) {
                    showLoading(show = false)
                    navigateToMain()
                }
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
