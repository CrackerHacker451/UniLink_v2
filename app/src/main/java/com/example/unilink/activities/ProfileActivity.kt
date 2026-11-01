package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.unilink.R
import com.example.unilink.databinding.ActivityProfileBinding
import com.example.unilink.models.User
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.ImageLoaderUtils
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ProfileActivity : AppCompatActivity() {

    private var _binding: ActivityProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()

    private var profileListener: ListenerRegistration? = null
    private var currentUserListener: ListenerRegistration? = null
    private var currentUid: String? = null
    private var currentUser: User? = null
    private var appliedThemeName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        appliedThemeName = ThemeUtils.getSelectedTheme(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUid = intent.getStringExtra("userId") ?: auth.currentUser?.uid

        observeCurrentProfile()

        val isSelf = (currentUid == auth.currentUser?.uid)

        if (isSelf) {
            binding.btnEditProfile.visibility = View.VISIBLE
            binding.btnLogout.visibility = View.VISIBLE
            binding.cardFeaturesHub.visibility = View.VISIBLE
            binding.btnSettings.visibility = View.VISIBLE
            binding.llProfileActions.visibility = View.GONE
        } else {
            binding.btnEditProfile.visibility = View.GONE
            binding.btnLogout.visibility = View.GONE
            binding.cardFeaturesHub.visibility = View.GONE
            binding.btnSettings.visibility = View.GONE
            binding.llProfileActions.visibility = View.VISIBLE
            currentUid?.let { observeOtherProfile(it) }
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnEditProfile.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(this, ProfileSetupActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnSettingsMenu.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnRedeemPremium.setOnClickListener {
            HapticUtils.playSelection(it)
            showProPassDialog()
        }

        binding.cardWorldIdBadge.setOnClickListener {
            HapticUtils.playSelection(it)
            showIdKitTrustSheet()
        }

        binding.btnVault.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(this, VaultActivity::class.java))
        }

        binding.btnBlockedUsers.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(this, BlockedUsersActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
            finish()
        }
    }

    private fun showProPassDialog() {
        com.example.unilink.ui.ProPassBottomSheet {
            Toast.makeText(this, "Pro Pass Active! 👑", Toast.LENGTH_SHORT).show()
        }.show(supportFragmentManager, "ProPassBottomSheet")
    }

    private fun showIdKitTrustSheet() {
        val viewModel = com.example.unilink.worldid.NewtonsLobbyTrustViewModel(application)
        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                com.example.unilink.worldid.NewtonsLobbyTrustSheet(
                    projectId = "student_idkit_${currentUid ?: "guest"}",
                    projectTitle = "IDKit World ID 4.0 Student Verification",
                    viewModel = viewModel
                )
            }
        }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(composeView)
        dialog.show()
    }

    private fun observeCurrentProfile() {
        currentUserListener = userRepository.observeCurrentUser { user ->
            if (_binding == null) return@observeCurrentUser
            currentUser = user
            if (currentUid == auth.currentUser?.uid) {
                user?.let { populateUI(it) }
            } else {
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

    private fun setupLinkButton(uid: String) {
        val isLinked = currentUser?.linked?.contains(uid) == true
        binding.btnLink.text = if (isLinked) "Unlink" else "Link UP"
        binding.btnLink.setOnClickListener {
            HapticUtils.playSelection(it)
            if (isLinked) userRepository.unlinkUser(uid) { } else userRepository.linkUser(uid) { }
        }
    }

    private fun populateUI(user: User) {
        binding.tvName.text = user.name.ifEmpty { "Student Member" }
        binding.tvUsername.text = "@${user.username.ifEmpty { "student" }}"
        binding.tvBio.text = user.bio.ifEmpty { "No bio yet." }
        binding.tvLinkedCount.text = user.linked.size.toString()
        binding.tvLinkersCount.text = user.linkers.size.toString()
        binding.tvProfileUniCoins.text = user.uniCoins.toString()

        ImageLoaderUtils.loadSmartImage(
            imageView = binding.ivProfileLarge,
            url = user.profileImageUrl,
            placeholderRes = R.drawable.ic_profile_placeholder,
            isCircle = true,
            fallbackName = user.name
        )

        // University Details
        val college = user.collegeName ?: user.schoolName
        if (!college.isNullOrEmpty()) {
            binding.tvDetail1.text = college
            binding.tvDetail1.visibility = View.VISIBLE
        } else {
            binding.tvDetail1.visibility = View.GONE
        }

        if (!user.branch.isNullOrEmpty() || !user.yearOrSemester.isNullOrEmpty()) {
            val branchYear = listOfNotNull(user.branch, user.yearOrSemester).joinToString(" • ")
            binding.tvDetail2.text = branchYear
            binding.tvDetail2.visibility = View.VISIBLE
        } else {
            binding.tvDetail2.visibility = View.GONE
        }

        if (!user.city.isNullOrEmpty() || !user.state.isNullOrEmpty()) {
            val location = listOfNotNull(user.city, user.state).joinToString(", ")
            binding.tvLocation.text = location
            binding.tvLocation.visibility = View.VISIBLE
        } else {
            binding.tvLocation.visibility = View.GONE
        }

        binding.cardUniversityDetails.visibility =
            if (binding.tvDetail1.visibility == View.VISIBLE ||
                binding.tvDetail2.visibility == View.VISIBLE ||
                binding.tvLocation.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        binding.ivPremiumBadge.visibility = if (user.isPremium) View.VISIBLE else View.GONE

        if (user.worldIdVerified) {
            binding.tvWorldIdStatus.text = "IDKit World ID Verified 🌐"
            binding.cardWorldIdBadge.setCardBackgroundColor(android.graphics.Color.parseColor("#1F10B981"))
            binding.cardWorldIdBadge.strokeColor = android.graphics.Color.parseColor("#10B981")
            binding.tvWorldIdStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
        } else {
            binding.tvWorldIdStatus.text = "Verify IDKit Liveness 🌐"
            binding.cardWorldIdBadge.setCardBackgroundColor(android.graphics.Color.parseColor("#1F06B6D4"))
            binding.cardWorldIdBadge.strokeColor = android.graphics.Color.parseColor("#06B6D4")
            binding.tvWorldIdStatus.setTextColor(android.graphics.Color.parseColor("#06B6D4"))
        }

        if (user.uid != auth.currentUser?.uid) {
            setupLinkButton(user.uid)
            binding.btnGrantStar.setOnClickListener {
                HapticUtils.playSelection(it)
                userRepository.grantLoyaltyStar(user.uid) { success, msg ->
                    Toast.makeText(this, msg ?: if (success) "Loyalty Star Granted! ⭐" else "Failed to grant star", Toast.LENGTH_SHORT).show()
                }
            }
            binding.btnMessage.setOnClickListener {
                val cid = com.example.unilink.repository.ChatRepository().getChatId(auth.currentUser?.uid ?: "", user.uid)
                startActivity(Intent(this, ChatActivity::class.java).apply {
                    putExtra("chatId", cid)
                    putExtra("receiverId", user.uid)
                    putExtra("receiverName", user.name)
                })
            }
            userRepository.logProfileView(user.uid)
        }
    }

    override fun onResume() {
        super.onResume()
        if (ThemeUtils.checkAndRecreateIfThemeChanged(this, appliedThemeName)) {
            appliedThemeName = ThemeUtils.getSelectedTheme(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        profileListener?.remove()
        currentUserListener?.remove()
        _binding = null
    }
}
