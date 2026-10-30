package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
            NotificationHelper.showNotification(this, "Newton's Lobby 🔔", "This is a test notification with sound!")
        }

        binding.rlSecretUnlock.setOnClickListener {
            showRoyalUnlockDialog()
        }

        binding.rlRoyalAdminPanel.setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
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
            AlertDialog.Builder(this)
                .setTitle("Delete Account?")
                .setMessage("This action is permanent and will delete all your data.")
                .setPositiveButton("Delete Permanently") { _, _ ->
                    val user = auth.currentUser
                    if (user != null) {
                        userRepository.deleteAccount { success ->
                            auth.signOut()
                            Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
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
            showPrivacyPolicyDialog()
        }
    }

    private fun showPrivacyPolicyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Newton's Lobby Privacy Policy 🛡️")
            .setMessage(
                "Your privacy and security are Newton's Lobby's top priorities.\n\n" +
                "1. Data Security: Your profile, messages, and campus data are encrypted in transit and at rest.\n" +
                "2. Location Controls: Location features (Radar & Nearby) are opt-in and can be hidden anytime using Incognito Mode.\n" +
                "3. Account Sovereignty: You have full control to edit or permanently delete your account and data at any time.\n" +
                "4. Zero Selling: We do not sell your personal information or university records to third parties."
            )
            .setPositiveButton("I Understand", null)
            .show()
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
        val activeTheme = ThemeUtils.getSelectedTheme(this)

        val selectionColor = getColor(R.color.neon_cyan)
        val inactiveColor = android.graphics.Color.TRANSPARENT

        binding.themeCyber.apply {
            root.visibility = android.view.View.VISIBLE
            viewColor.setBackgroundColor(getColor(R.color.bg_dark))
            cardThemeColor.strokeColor = if (activeTheme == "Dark") selectionColor else inactiveColor
            root.setOnClickListener { applyThemeAndRestart("Dark") }
        }

        binding.themeWhite.apply {
            root.visibility = android.view.View.VISIBLE
            viewColor.setBackgroundColor(getColor(R.color.white))
            cardThemeColor.strokeColor = if (activeTheme == "Light") selectionColor else inactiveColor
            root.setOnClickListener { applyThemeAndRestart("Light") }
        }

        binding.themeTeal.apply {
            root.visibility = android.view.View.VISIBLE
            viewColor.setBackgroundColor(getColor(R.color.liquid_teal))
            cardThemeColor.strokeColor = if (activeTheme == "Teal") selectionColor else inactiveColor
            root.setOnClickListener { applyThemeAndRestart("Teal") }
        }

        binding.themeIndigo.apply {
            root.visibility = android.view.View.VISIBLE
            viewColor.setBackgroundColor(getColor(R.color.liquid_indigo))
            cardThemeColor.strokeColor = if (activeTheme == "Indigo") selectionColor else inactiveColor
            root.setOnClickListener { applyThemeAndRestart("Indigo") }
        }

        binding.themeRose.apply {
            root.visibility = android.view.View.VISIBLE
            viewColor.setBackgroundColor(getColor(R.color.liquid_rose))
            cardThemeColor.strokeColor = if (activeTheme == "Rose") selectionColor else inactiveColor
            root.setOnClickListener { applyThemeAndRestart("Rose") }
        }

        binding.themeAmber.apply {
            root.visibility = android.view.View.VISIBLE
            viewColor.setBackgroundColor(getColor(R.color.liquid_amber))
            cardThemeColor.strokeColor = if (activeTheme == "Amber") selectionColor else inactiveColor
            root.setOnClickListener { applyThemeAndRestart("Amber") }
        }

        binding.switchDarkMode.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener
            val targetTheme = if (isChecked) "Dark" else "Light"
            applyThemeAndRestart(targetTheme)
        }
    }

    private fun applyThemeAndRestart(themeName: String) {
        if (ThemeUtils.getSelectedTheme(this) == themeName) return

        ThemeUtils.saveTheme(this, themeName)
        Toast.makeText(this, "Applied $themeName Theme!", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showRoyalUnlockDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Enter Secret Code"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("👑 Unlock Royal Admin Portal")
            .setMessage("Enter the secret unlock code to access system administration controls:")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val code = input.text.toString().trim()
                if (code.equals("Royal", ignoreCase = true)) {
                    binding.rlRoyalAdminPanel.visibility = android.view.View.VISIBLE
                    com.example.unilink.utils.HapticUtils.playHeavy(binding.root)
                    Toast.makeText(this, "👑 Royal Admin Mode Unlocked!", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, AdminDashboardActivity::class.java))
                } else {
                    Toast.makeText(this, "Incorrect unlock code!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadPreferences() {
        val currentTheme = ThemeUtils.getSelectedTheme(this)
        binding.switchDarkMode.isChecked = (currentTheme == "Dark")
        binding.switchAppLock.isChecked = ThemeUtils.isAppLockEnabled(this)
        binding.switchAuraParticles.isChecked = com.example.unilink.utils.PrefsUtils.isAuraEnabled(this)

        userRepository.getCurrentUser { user ->
            binding.switchIncognito.isChecked = user?.isIncognito ?: false
        }
    }
}
