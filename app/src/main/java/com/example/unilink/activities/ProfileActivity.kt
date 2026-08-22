package com.example.unilink.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
import com.example.unilink.databinding.ActivityProfileBinding
import com.example.unilink.databinding.DialogPremiumCustomizeBinding
import com.example.unilink.models.User
import com.example.unilink.repository.ChatRepository
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.ThemeUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ProfileActivity : AppCompatActivity() {

    private var _binding: ActivityProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()
    private val chatRepository = ChatRepository()
    private val storageRepository = com.example.unilink.repository.StorageRepository()

    private var profileListener: ListenerRegistration? = null
    private var currentUserListener: ListenerRegistration? = null
    private var currentUid: String? = null
    private var currentUser: User? = null
    
    private var selectedScreenshotUri: Uri? = null
    private val pickScreenshotLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedScreenshotUri = it
            Toast.makeText(this, "Screenshot selected! ✅", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickProfilePictureLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            binding.ivProfileLarge.load(it) {
                transformations(CircleCropTransformation())
            }
            Toast.makeText(this, "Uploading Profile Picture...", Toast.LENGTH_SHORT).show()
            
            val uid = auth.currentUser?.uid ?: return@let
            storageRepository.uploadProfilePicture(uid, it) { url ->
                if (url != null) {
                    userRepository.updateUserPremiumSettings(profilePicUrl = url) { success ->
                        if (success) {
                            Toast.makeText(this, "Profile picture updated! ✨", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        // Security: Block screenshots for profiles (especially Vault/Premium)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)

        currentUid = intent.getStringExtra("userId") ?: auth.currentUser?.uid

        observeCurrentProfile()

        if (currentUid == auth.currentUser?.uid) {
            binding.btnSettings.visibility = View.VISIBLE
            binding.btnEditProfile.visibility = View.VISIBLE
            binding.btnBlockedUsers.visibility = View.VISIBLE
            binding.btnLogout.visibility = View.VISIBLE
            binding.llProfileActions.visibility = View.GONE
            
            binding.ivProfileLarge.setOnClickListener {
                pickProfilePictureLauncher.launch("image/*")
            }
        } else {
            binding.btnSettings.visibility = View.GONE
            binding.btnEditProfile.visibility = View.GONE
            binding.btnBlockedUsers.visibility = View.GONE
            binding.btnLogout.visibility = View.GONE
            binding.llProfileActions.visibility = View.VISIBLE
            
            currentUid?.let { observeOtherProfile(it) }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(this, ProfileSetupActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.btnRedeemPremium.setOnClickListener {
            showProfessionalPremiumDialog()
        }

        binding.btnCustomizePremium.setOnClickListener {
            showPremiumCustomizationDialog()
        }
    }

    private fun showProfessionalPremiumDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = com.example.unilink.databinding.DialogPremiumPurchaseBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        var isFree = false

        dialogBinding.btnApplyPromo.setOnClickListener {
            val code = dialogBinding.etPromoCode.text.toString().trim().uppercase()
            if (code == "UNI100" || code == "NDSIR") {
                isFree = true
                dialogBinding.tvPremiumPrice.text = "FREE"
                dialogBinding.tvDiscountNote.text = "$code Applied! 100% Discount"
                dialogBinding.tvDiscountNote.setTextColor(android.graphics.Color.parseColor("#22C55E"))
                dialogBinding.llPaymentStep.visibility = View.GONE
                dialogBinding.llVerificationStep.visibility = View.VISIBLE
                dialogBinding.btnSubmitVerification.text = "Claim Elite Pro"
                dialogBinding.etTransactionId.setText("PROMO_$code")
                dialogBinding.etPaymentTime.setText("N/A")
                dialogBinding.btnUploadScreenshot.visibility = View.GONE
                Toast.makeText(this, "Promo code applied! ✨", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid promo code", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.btnNextStep.setOnClickListener {
            dialogBinding.llPaymentStep.visibility = View.GONE
            dialogBinding.llVerificationStep.visibility = View.VISIBLE
        }

        dialogBinding.btnBackToPay.setOnClickListener {
            dialogBinding.llPaymentStep.visibility = View.VISIBLE
            dialogBinding.llVerificationStep.visibility = View.GONE
        }

        dialogBinding.btnUploadScreenshot.setOnClickListener {
            pickScreenshotLauncher.launch("image/*")
        }

        dialogBinding.btnSubmitVerification.setOnClickListener {
            val code = dialogBinding.etPromoCode.text.toString().trim().uppercase()
            if (isFree && (code == "NDSIR" || code == "PAPABOL1917")) {
                userRepository.redeemPremiumCode(code) { success, msg ->
                    if (success) {
                        Toast.makeText(this, "Welcome to Elite Pro! ✨", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (isFree) {
                userRepository.activatePremium { success ->
                    if (success) {
                        Toast.makeText(this, "Welcome to UniLink Pro! ✨", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                }
            } else {
                val txId = dialogBinding.etTransactionId.text.toString().trim()
                if (txId.isEmpty()) {
                    Toast.makeText(this, "Please enter Transaction ID", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                if (selectedScreenshotUri == null) {
                    Toast.makeText(this, "Please upload payment screenshot", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Toast.makeText(this, "Request submitted with screenshot! We will verify and activate your Pro features within 24 hours.", Toast.LENGTH_LONG).show()
                selectedScreenshotUri = null
                dialog.dismiss()
            }
        }

        dialogBinding.btnHaveCode.setOnClickListener {
            dialog.dismiss()
            showPremiumCodeDialog()
        }

        dialog.show()
    }

    private fun observeCurrentProfile() {
        currentUserListener = userRepository.observeCurrentUser { user ->
            if (_binding == null) return@observeCurrentUser
            currentUser = user
            if (currentUid == auth.currentUser?.uid) {
                user?.let { populateUI(it) }
            } else {
                // We are viewing someone else, but we need our user data for the link button status
                currentUid?.let { setupLinkButton(it) }
            }
        }
    }

    private fun observeOtherProfile(uid: String) {
        profileListener = userRepository.observeUser(uid) { user ->
            if (_binding == null) return@observeUser
            user?.let { populateUI(it) }
        }
    }

    private fun showPremiumCodeDialog() {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        input.hint = "Enter Redemption Code"
        builder.setTitle("Redeem Premium")
        builder.setView(input)
        builder.setPositiveButton("Redeem") { _, _ ->
            val code = input.text.toString().trim()
            userRepository.redeemPremiumCode(code) { _, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showPremiumCustomizationDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogPremiumCustomizeBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        var selectedBanner = "banner_sci_fi"
        val selectedTheme = ThemeUtils.getSelectedTheme(this)

        // Initialize UI with current selection
        when (selectedTheme) {
            "Cyber" -> dialogBinding.rbThemeCyber.isChecked = true
            "Indigo Liquid" -> dialogBinding.rbThemeIndigo.isChecked = true
            "Ocean Glass" -> dialogBinding.rbThemeTeal.isChecked = true
            "Rose Crystal" -> dialogBinding.rbThemeRose.isChecked = true
            "Amber Glow" -> dialogBinding.rbThemeAmber.isChecked = true
            "Midnight Gold" -> dialogBinding.rbThemeGold.isChecked = true
            "Ars Dark" -> dialogBinding.rbThemeArsDark.isChecked = true
            "Classic theme" -> dialogBinding.rbThemeLight.isChecked = true
            "Ars White" -> dialogBinding.rbThemeWhite.isChecked = true
            "Aura Glass" -> dialogBinding.rbThemeGlass.isChecked = true
        }

        val bannerOptions = listOf(
            dialogBinding.bannerOption1 to "banner_sci_fi",
            dialogBinding.bannerOption2 to "banner_lightning",
            dialogBinding.bannerOption3 to "bg_brand_gradient",
            dialogBinding.bannerOption4 to "banner_nebula",
            dialogBinding.bannerOption5 to "grad_cyber"
        )

        fun updateBannerSelection(selectedName: String) {
            selectedBanner = selectedName
            bannerOptions.forEach { (card, name) ->
                card.strokeWidth = if (name == selectedName) 6 else 0
                card.strokeColor = android.graphics.Color.CYAN
            }
        }

        bannerOptions.forEach { (card, name) ->
            card.setOnClickListener { updateBannerSelection(name) }
        }

        dialogBinding.btnSaveCustomization.setOnClickListener {
            val themeName = when (dialogBinding.rgThemes.checkedRadioButtonId) {
                R.id.rbThemeCyber -> "Cyber"
                R.id.rbThemeIndigo -> "Indigo Liquid"
                R.id.rbThemeTeal -> "Ocean Glass"
                R.id.rbThemeRose -> "Rose Crystal"
                R.id.rbThemeAmber -> "Amber Glow"
                R.id.rbThemeGold -> "Midnight Gold"
                R.id.rbThemeArsDark -> "Ars Dark"
                R.id.rbThemeLight -> "Classic theme"
                R.id.rbThemeWhite -> "Ars White"
                R.id.rbThemeGlass -> "Aura Glass"
                else -> "Ars White"
            }

            userRepository.updateUserPremiumSettings(
                bannerName = selectedBanner,
                wallpaperResId = null,
                appTheme = themeName
            ) { success ->
                if (success) {
                    ThemeUtils.saveTheme(this, themeName)
                    Toast.makeText(this, "Customizations Saved! Restarting app...", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    // Restart to apply theme
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Failed to save customizations", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun setupMoreMenu(uid: String) {
        binding.btnMore.setOnClickListener {
            val popup = androidx.appcompat.widget.PopupMenu(this, it)
            popup.menu.add("Report")
            popup.menu.add("Block")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Block" -> {
                        userRepository.blockUser(uid) { success ->
                            if (success) {
                                Toast.makeText(this, "Student blocked", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                    }
                }
                true
            }
            popup.show()
        }
    }

    private fun setupLinkButton(uid: String) {
        val isLinked = currentUser?.linked?.contains(uid) == true
        binding.btnLink.text = if (isLinked) "Unlink" else "Link"
        
        binding.btnLink.setOnClickListener {
            com.example.unilink.utils.HapticUtils.playSelection(it)
            binding.btnLink.isEnabled = false
            if (isLinked) {
                userRepository.unlinkUser(uid) { success ->
                    binding.btnLink.isEnabled = true
                    if (success) com.example.unilink.utils.HapticUtils.playHeavy(it)
                }
            } else {
                userRepository.linkUser(uid) { success ->
                    binding.btnLink.isEnabled = true
                    if (success) com.example.unilink.utils.HapticUtils.playSuccess(it)
                }
            }
        }
    }

    private fun startChatWithUser(uid: String) {
        val cid = chatRepository.getChatId(auth.currentUser?.uid ?: "", uid)
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chatId", cid)
        intent.putExtra("receiverId", uid)
        intent.putExtra("receiverName", binding.tvName.text.toString())
        startActivity(intent)
    }

    private fun populateUI(user: User) {
        binding.tvName.text = user.name
        binding.tvUsername.text = "@${user.username}"
        binding.tvBio.text = if (user.bio.isNotEmpty()) user.bio else "No bio yet."
        binding.tvLinkedCount.text = (user.linked?.size ?: 0).toString()
        binding.tvLinkersCount.text = (user.linkers?.size ?: 0).toString()
        
        // Impact Logic
        binding.tvImpactScore.text = "Impact Score: ${user.impactScore}"
        val impactLevel = (user.impactScore / 100) + 1
        binding.tvUserType.text = "Level $impactLevel Explorer"
        
        // Aura Glow for high impact
        if (user.impactScore >= 500) {
            binding.tvImpactScore.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33FACC15"))
            binding.tvImpactScore.setTextColor(android.graphics.Color.parseColor("#FACC15"))
        } else {
            binding.tvImpactScore.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1A22D3EE"))
            binding.tvImpactScore.setTextColor(android.graphics.Color.parseColor("#22D3EE"))
        }

        binding.ivProfileLarge.load(user.profileImageUrl ?: R.drawable.ic_profile_placeholder) {
            transformations(CircleCropTransformation())
        }

        if (user.isPremium) {
            binding.ivPremiumBadge.visibility = View.VISIBLE
            binding.tvVipStatus.visibility = if (user.isVip) View.VISIBLE else View.GONE
            binding.tvVipStatus.text = "VIP LVL ${user.vipLevel}"
            
            binding.btnRedeemPremium.visibility = View.GONE
            if (user.uid == auth.currentUser?.uid) {
                binding.btnCustomizePremium.visibility = View.VISIBLE
                binding.cardProFeatures.visibility = View.VISIBLE
            }
            if (!user.profileBannerUrl.isNullOrEmpty()) {
                binding.ivProfileBanner.visibility = View.VISIBLE
                val resId = ThemeUtils.getDrawableIdByName(this, user.profileBannerUrl)
                if (resId != 0) {
                    binding.ivProfileBanner.setImageResource(resId)
                } else {
                    binding.ivProfileBanner.load(user.profileBannerUrl)
                }
            }
        } else {
            binding.ivPremiumBadge.visibility = View.GONE
            binding.tvVipStatus.visibility = View.GONE
            binding.btnCustomizePremium.visibility = View.GONE
            if (user.uid == auth.currentUser?.uid) {
                binding.btnRedeemPremium.visibility = View.VISIBLE
            }
        }
        
        // Online Status
        binding.vOnlineStatus.visibility = if (user.isOnline) View.VISIBLE else View.GONE

        renderSkills(user)
        renderBadges(user)
        loadProfileVisitors(user)
        
        if (user.uid != auth.currentUser?.uid) {
            setupLinkButton(user.uid)
            setupMoreMenu(user.uid)
            binding.btnMessage.setOnClickListener { startChatWithUser(user.uid) }
            userRepository.logProfileView(user.uid)
        }
    }

    private fun renderBadges(user: User) {
        if (user.badges.isEmpty()) {
            binding.rvBadges.visibility = View.GONE
            return
        }
        binding.rvBadges.visibility = View.VISIBLE
        binding.rvBadges.adapter = com.example.unilink.adapters.BadgeAdapter(user.badges)
    }

    private fun renderSkills(user: User) {
        binding.llSkillsContainer.removeAllViews()
        val isOwnProfile = user.uid == auth.currentUser?.uid
        
        user.skills?.forEach { skill ->
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val tv = android.widget.TextView(this).apply {
                text = skill
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ThemeUtils.getTextPrimaryColor(this@ProfileActivity))
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            // Calculate Vouch Stats
            val vouches = user.skillVouches?.get(skill) ?: emptyList()
            val vouchCount = vouches.size
            val isGold = vouchCount >= 10
            
            val progress = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(140, 6)
                trackThickness = 6
                trackCornerRadius = 3
                this.progress = (vouchCount * 10).coerceAtMost(100)
                setIndicatorColor(android.graphics.Color.parseColor(if (isGold) "#FACC15" else "#22D3EE"))
                trackColor = android.graphics.Color.parseColor("#1AFFFFFF")
            }

            layout.addView(tv)
            layout.addView(progress)

            if (!isOwnProfile) {
                val btnVouch = android.widget.TextView(this).apply {
                    val hasVouched = vouches.contains(auth.currentUser?.uid)
                    text = if (hasVouched) "Vouched" else "Vouch"
                    setTextColor(android.graphics.Color.parseColor(if (hasVouched) "#22C55E" else "#22D3EE"))
                    textSize = 12f
                    setPadding(20, 8, 20, 8)
                    isEnabled = !hasVouched
                    
                    setOnClickListener {
                        isEnabled = false
                        userRepository.vouchForSkill(user.uid, skill) { success ->
                            if (success) {
                                text = "Vouched"
                                setTextColor(android.graphics.Color.parseColor("#22C55E"))
                                Toast.makeText(this@ProfileActivity, "Reputation Gained! ⭐", Toast.LENGTH_SHORT).show()
                            } else {
                                isEnabled = true
                            }
                        }
                    }
                }
                layout.addView(btnVouch)
            } else {
                val tvCount = android.widget.TextView(this).apply {
                    text = "⭐ $vouchCount"
                    setTextColor(android.graphics.Color.parseColor(if (isGold) "#FACC15" else "#94A3B8"))
                    textSize = 12f
                    setPadding(20, 0, 0, 0)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                layout.addView(tvCount)
            }

            binding.llSkillsContainer.addView(layout)
        }
    }

    private fun loadProfileVisitors(user: User) {
        if (!user.isPremium || user.profileViews.isNullOrEmpty() || user.uid != auth.currentUser?.uid) {
            binding.cardProfileVisitors.visibility = View.GONE
            return
        }

        binding.cardProfileVisitors.visibility = View.VISIBLE
        val visitorUids = user.profileViews!!.distinct().take(10)
        val visitorUsers = mutableListOf<User>()
        var loadedCount = 0

        visitorUids.forEach { vuid ->
            userRepository.getUserById(vuid) { vUser ->
                if (_binding == null) return@getUserById
                vUser?.let { visitorUsers.add(it) }
                loadedCount++
                if (loadedCount == visitorUids.size && _binding != null) {
                    binding.rvVisitors.adapter = com.example.unilink.adapters.VisitorAvatarAdapter(visitorUsers) { clickedUser ->
                        val intent = Intent(this, ProfileActivity::class.java)
                        intent.putExtra("userId", clickedUser.uid)
                        startActivity(intent)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        profileListener?.remove()
        currentUserListener?.remove()
        _binding = null
    }
}
