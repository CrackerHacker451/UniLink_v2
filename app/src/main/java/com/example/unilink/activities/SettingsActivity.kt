package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.unilink.R
import com.example.unilink.databinding.ActivitySettingsBinding
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.NotificationHelper
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.updatePadding(top = systemBars.top)
            binding.settingsScroll.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupListeners()
        setupThemeSelectors()
        loadPreferences()
    }

    private fun setupListeners() {
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        binding.switchIncognito.setOnCheckedChangeListener { _, isChecked ->
            userRepository.getCurrentUser { user ->
                if (user?.isPremium == true) {
                    userRepository.setIncognitoMode(isChecked) { success ->
                        if (success) Toast.makeText(this, "Incognito mode updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    binding.switchIncognito.isChecked = false
                    Toast.makeText(this, "Incognito Mode is a Pro feature! ✨", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showBiometricPrompt { success ->
                    if (success) {
                        ThemeUtils.setAppLockEnabled(this, true)
                        Toast.makeText(this, "App Lock enabled", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.switchAppLock.isChecked = false
                    }
                }
            } else {
                ThemeUtils.setAppLockEnabled(this, false)
            }
        }

        binding.switchAuraParticles.setOnCheckedChangeListener { _, isChecked ->
            userRepository.getCurrentUser { user ->
                if (user?.isPremium == true) {
                    com.example.unilink.utils.PrefsUtils.setAuraEnabled(this, isChecked)
                    Toast.makeText(this, "Aura effects updated", Toast.LENGTH_SHORT).show()
                } else {
                    binding.switchAuraParticles.isChecked = false
                    Toast.makeText(this, "Aura Particles are a Pro feature! ✨", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.rlTestNotification.setOnClickListener {
            NotificationHelper.showNotification(this, "Uni Link", "This is a test notification!")
        }

        binding.rlCopyToken.setOnClickListener {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("FCM Token", token)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Token copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.rlLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.rlDeleteAccount.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Account?")
                .setMessage("This action is permanent and will delete all your data.")
                .setPositiveButton("Delete") { _, _ ->
                    userRepository.deleteAccount { success ->
                        if (success) {
                            Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, "Failed to delete. Try logging in again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.rlBlockedUsers.setOnClickListener {
            startActivity(Intent(this, BlockedUsersActivity::class.java))
        }

        binding.rlPrivacy.setOnClickListener {
            Toast.makeText(this, "Privacy Policy coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBiometricPrompt(onResult: (Boolean) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onResult(false)
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(true)
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onResult(false)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Confirm your identity to enable App Lock")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun setupThemeSelectors() {
        val currentTheme = ThemeUtils.getSelectedTheme(this)
        
        // Helper to update selection UI
        fun updateSelection() {
            val activeTheme = ThemeUtils.getSelectedTheme(this)
            val selectionColor = getColor(R.color.neon_cyan)
            val inactiveColor = android.graphics.Color.TRANSPARENT
            
            binding.themeCyber.cardThemeColor.strokeColor = if (activeTheme == "Cyber") selectionColor else inactiveColor
            binding.themeIndigo.cardThemeColor.strokeColor = if (activeTheme == "Indigo Liquid") selectionColor else inactiveColor
            binding.themeTeal.cardThemeColor.strokeColor = if (activeTheme == "Ocean Glass") selectionColor else inactiveColor
            binding.themeRose.cardThemeColor.strokeColor = if (activeTheme == "Rose Crystal") selectionColor else inactiveColor
            binding.themeAmber.cardThemeColor.strokeColor = if (activeTheme == "Amber Glow") selectionColor else inactiveColor
            binding.themeWhite.cardThemeColor.strokeColor = if (activeTheme == "Ars White") selectionColor else inactiveColor
            binding.themeGlass.cardThemeColor.strokeColor = if (activeTheme == "Aura Glass") selectionColor else inactiveColor
        }

        // Theme Cyber (Default Pink/Cyan)
        binding.themeCyber.apply {
            viewColor.setBackgroundResource(R.drawable.grad_cyber)
            root.setOnClickListener { applyThemeAndRestart("Cyber") }
        }
        
        // Theme Indigo
        binding.themeIndigo.apply {
            viewColor.setBackgroundColor(getColor(R.color.liquid_indigo))
            root.setOnClickListener { applyThemeAndRestart("Indigo Liquid") }
        }
        
        // Theme Teal
        binding.themeTeal.apply {
            viewColor.setBackgroundColor(getColor(R.color.liquid_teal))
            root.setOnClickListener { applyThemeAndRestart("Ocean Glass") }
        }
        
        // Theme Rose
        binding.themeRose.apply {
            viewColor.setBackgroundColor(getColor(R.color.liquid_rose))
            root.setOnClickListener { applyThemeAndRestart("Rose Crystal") }
        }
        
        // Theme Amber
        binding.themeAmber.apply {
            viewColor.setBackgroundColor(getColor(R.color.liquid_amber))
            root.setOnClickListener { applyThemeAndRestart("Amber Glow") }
        }

        // Theme White
        binding.themeWhite.apply {
            viewColor.setBackgroundColor(getColor(R.color.white))
            root.setOnClickListener { applyThemeAndRestart("Ars White") }
        }

        // Theme Aura Glass
        binding.themeGlass.apply {
            viewColor.setBackgroundResource(R.drawable.bg_glass_cluster)
            root.setOnClickListener { applyThemeAndRestart("Aura Glass") }
        }
        
        updateSelection()
    }

    private fun applyThemeAndRestart(themeName: String) {
        if (ThemeUtils.getSelectedTheme(this) == themeName) return
        
        ThemeUtils.saveTheme(this, themeName)
        Toast.makeText(this, "Applied $themeName Theme!", Toast.LENGTH_SHORT).show()
        
        // Restart activity to apply theme
        recreate()
    }

    private fun loadPreferences() {
        val isNightMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        binding.switchDarkMode.isChecked = isNightMode
        binding.switchAppLock.isChecked = ThemeUtils.isAppLockEnabled(this)
        binding.switchAuraParticles.isChecked = com.example.unilink.utils.PrefsUtils.isAuraEnabled(this)
        
        userRepository.getCurrentUser { user ->
            binding.switchIncognito.isChecked = user?.isIncognito ?: false
        }
    }
}
