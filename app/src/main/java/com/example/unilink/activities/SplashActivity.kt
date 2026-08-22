package com.example.unilink.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.unilink.R
import com.example.unilink.databinding.ActivitySplashBinding
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private var _binding: ActivitySplashBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        try { ThemeUtils.applyTheme(this) } catch (_: Exception) {}
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        _binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check for Google Play Services
        val gApi = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val status = gApi.isGooglePlayServicesAvailable(this)
        if (status != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            if (gApi.isUserResolvableError(status)) {
                gApi.getErrorDialog(this, status, 9000)?.show()
            } else {
                Toast.makeText(this, "Google Play Services is required", Toast.LENGTH_LONG).show()
            }
        }

        try {
            // Liquid Entrance Animations
            val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
            fadeIn.duration = 1000
            
            binding.cardLogo.startAnimation(fadeIn)
            binding.tvLogo.startAnimation(fadeIn)
            binding.tvBranding.startAnimation(fadeIn)

            val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.cardLogo.startAnimation(pulse)

            // Animated Progress
            binding.splashProgress.alpha = 0f
            binding.splashProgress.animate()?.alpha(1f)?.setDuration(500)?.setStartDelay(500)?.start()
        } catch (e: Exception) {
            Log.e("SplashActivity", "Animation error", e)
        }

        // Delay then check login status
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            
            val currentUser = auth.currentUser
            if (currentUser != null) {
                if (ThemeUtils.isAppLockEnabled(this)) {
                    showBiometricPromptForLock()
                } else {
                    navigateToMain()
                }
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }, 2500)
    }

    private fun showBiometricPromptForLock() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    finish() // Exit if auth fails or is cancelled
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    navigateToMain()
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("UniLink Locked")
            .setSubtitle("Authenticate to continue")
            .setNegativeButtonText("Exit App")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun navigateToMain() {
        val nextIntent = Intent(this, MainActivity::class.java)
        val chatId = intent.getStringExtra("chatId")
        chatId?.let { nextIntent.putExtra("chatId", it) }
        startActivity(nextIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
