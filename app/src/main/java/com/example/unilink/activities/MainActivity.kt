package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.unilink.R
import com.example.unilink.databinding.ActivityMainBinding
import com.example.unilink.fragments.HomeFragment
import com.example.unilink.fragments.MessagesFragment
import com.example.unilink.fragments.ProjectsFragment
import com.example.unilink.fragments.SearchFragment
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.LocationHelper
import com.example.unilink.utils.ThemeUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()
    private var activeFragment: Fragment? = null
    private var appliedThemeName: String = ""
    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var unreadChatListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val shownNotificationIds = mutableSetOf<String>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                      (permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            fetchAndSaveUserLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            ThemeUtils.applyTheme(this)
            appliedThemeName = ThemeUtils.getSelectedTheme(this)
        } catch (_: Exception) {}
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateBottomNavThemeColors()

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = binding.navCard.layoutParams as android.view.ViewGroup.MarginLayoutParams
            lp.bottomMargin = systemBars.bottom + (12 * resources.displayMetrics.density).toInt()
            binding.navCard.layoutParams = lp
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }

        userRepository.getCurrentUser { user ->
            if (isFinishing || isDestroyed) return@getCurrentUser
            val isPro = user?.isPremium ?: false
            binding.auraParticles.setPremiumMode(isPro)

            val localTheme = ThemeUtils.getSelectedTheme(this)
            val remoteTheme = user?.appTheme

            if (!remoteTheme.isNullOrEmpty() && (remoteTheme != localTheme)) {
                ThemeUtils.saveTheme(this, remoteTheme)
                recreate()
            }
        }

        setupNavigation(savedInstanceState)
        setupClickListeners()
        updateOnlineStatus(online = true)
        observeNotifications()
        checkAndRequestPermissions()
        com.example.unilink.services.FcmService.subscribeToGlobalUpdates()
        checkForAppUpdates()
    }

    private fun checkAndRequestPermissions() {
        if (!LocationHelper.hasLocationPermission(this)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        } else {
            fetchAndSaveUserLocation()
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
            }
        }
    }

    private fun fetchAndSaveUserLocation() {
        LocationHelper.getLastLocation(this) { lat, lng, city, state ->
            if ((lat != null) && (lng != null)) {
                userRepository.updateUserLocation(lat, lng, city, state)
            }
        }
    }

    private fun updateBottomNavThemeColors() {
        try {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(R.attr.accentColor, typedValue, true)
            val accentColor = typedValue.data

            theme.resolveAttribute(R.attr.textSecondaryColor, typedValue, true)
            val inactiveColor = typedValue.data

            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked),
            )
            val colors = intArrayOf(
                accentColor,
                inactiveColor,
            )
            val colorStateList = android.content.res.ColorStateList(states, colors)

            binding.bottomNavigation.itemIconTintList = colorStateList
            binding.bottomNavigation.itemTextColor = colorStateList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeNotifications() {
        notificationListener?.remove()
        notificationListener = com.example.unilink.repository.NotificationRepository().observeNotifications { list ->
            val now = System.currentTimeMillis()
            list.forEach { notif ->
                val notifTime = notif.timestamp?.time ?: now
                val isRecent = (now - notifTime) < 30000L

                if (!notif.isRead && isRecent && !shownNotificationIds.contains(notif.notificationId)) {
                    shownNotificationIds.add(notif.notificationId)
                    if (notif.type == "video_call") {
                        com.example.unilink.utils.NotificationHelper.showIncomingCallNotification(
                            context = this,
                            callerName = notif.senderName ?: "Campus Peer",
                            channelId = notif.targetId ?: "call_room",
                            callerUid = notif.senderId ?: ""
                        )
                    } else {
                        com.example.unilink.utils.NotificationHelper.showNotification(
                            context = this,
                            title = notif.title,
                            message = notif.message,
                            targetId = notif.targetId
                        )
                    }
                }
            }
        }

        unreadChatListener?.remove()
        unreadChatListener = com.example.unilink.repository.ChatRepository().observeTotalUnreadChatCount { unreadChatCount ->
            val chatBadge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_messages)
            if (unreadChatCount > 0) {
                chatBadge.isVisible = true
                chatBadge.number = unreadChatCount
            } else {
                chatBadge.isVisible = false
                binding.bottomNavigation.removeBadge(R.id.nav_messages)
            }
        }
    }

    private fun setupNavigation(savedInstanceState: Bundle?) {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_profile) {
                com.example.unilink.utils.HapticUtils.playSelection(binding.bottomNavigation)
                startActivity(Intent(this, ProfileActivity::class.java))
                return@setOnItemSelectedListener false
            }

            val tag = item.itemId.toString()
            val fragment = supportFragmentManager.findFragmentByTag(tag) ?: createFragment(item.itemId)

            if (fragment == activeFragment) return@setOnItemSelectedListener true

            com.example.unilink.utils.HapticUtils.playSelection(binding.bottomNavigation)

            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in_subtle, R.anim.fade_out_subtle)
                .replace(R.id.fragmentContainer, fragment, tag)
                .commitAllowingStateLoss()

            activeFragment = fragment

            binding.fabCreate.visibility = if ((item.itemId == R.id.nav_home) || (item.itemId == R.id.nav_projects)) android.view.View.VISIBLE else android.view.View.GONE
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = R.id.nav_home
        } else {
            val selectedId = binding.bottomNavigation.selectedItemId
            if (selectedId != R.id.nav_profile) {
                activeFragment = supportFragmentManager.findFragmentByTag(selectedId.toString())
            }
        }
    }

    private fun createFragment(id: Int): Fragment {
        return when (id) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_search -> SearchFragment()
            R.id.nav_projects -> ProjectsFragment()
            R.id.nav_messages -> MessagesFragment()
            else -> throw IllegalStateException("Invalid nav id")
        }
    }

    private fun setupClickListeners() {
        binding.fabCreate.setOnClickListener {
            if (binding.bottomNavigation.selectedItemId == R.id.nav_projects) {
                startActivity(Intent(this, CreateTeamActivity::class.java))
            } else {
                startActivity(Intent(this, CreatePostActivity::class.java))
            }
        }
        binding.fabUniAI.setOnClickListener { showAiAssistant() }
    }

    private fun updateOnlineStatus(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        userRepository.setUserOnline(uid, online)
    }

    fun showAiAssistant() {
        val dialog = BottomSheetDialog(this)
        val aiBinding = com.example.unilink.databinding.BottomSheetUniAiBinding.inflate(layoutInflater)
        dialog.setContentView(aiBinding.root)

        val adapter = com.example.unilink.adapters.AiChatAdapter()
        aiBinding.rvAiChat.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        aiBinding.rvAiChat.adapter = adapter

        val processInput: (String) -> Unit = { prompt ->
            if (prompt.isNotBlank()) {
                adapter.addMessage(com.example.unilink.adapters.AiMessage(text = prompt, isUser = true))
                aiBinding.etAiInput.setText("")

                val agentResult = com.example.unilink.utils.NewtonAiAgent.parseAndExecuteCommand(this, prompt)
                when (agentResult) {
                    is com.example.unilink.utils.AgentExecutionResult.ExecutedAction -> {
                        adapter.addMessage(com.example.unilink.adapters.AiMessage(text = agentResult.message, isUser = false))
                        aiBinding.rvAiChat.smoothScrollToPosition(adapter.itemCount - 1)
                        com.example.unilink.utils.HapticUtils.playSelection(aiBinding.root)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try { dialog.dismiss() } catch (_: Exception) {}
                            startActivity(agentResult.intent)
                        }, 150)
                    }
                    is com.example.unilink.utils.AgentExecutionResult.HandledByAi -> {
                        lifecycleScope.launch {
                            val response = com.example.unilink.repository.GeminiRepository().getAiHint(prompt, true)
                            adapter.addMessage(com.example.unilink.adapters.AiMessage(text = response, isUser = false))
                            aiBinding.rvAiChat.smoothScrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }
            }
        }

        userRepository.getCurrentUser { user ->
            val welcome = "🤖 Hey ${user?.name ?: "Student"}! I'm NewtonAI, your Personal Campus Agent. Command me to start streams, post barters, find teams, or ask anything!"
            adapter.addMessage(com.example.unilink.adapters.AiMessage(text = welcome, isUser = false))

            aiBinding.btnAiSend.setOnClickListener {
                val text = aiBinding.etAiInput.text.toString().trim()
                processInput(text)
            }

            // Bind Quick Action Chips
            aiBinding.chipStartStream.setOnClickListener { processInput("start a live stream") }
            aiBinding.chipPostBarter.setOnClickListener { processInput("post a barter item") }
            aiBinding.chipFindTeam.setOnClickListener { processInput("find a team") }
            aiBinding.chipPostDoubt.setOnClickListener { processInput("ask a doubt") }
            aiBinding.chipFocusRoom.setOnClickListener { processInput("open focus room") }
            aiBinding.chipNearbyRadar.setOnClickListener { processInput("scan nearby radar") }
            aiBinding.chipLeaderboard.setOnClickListener { processInput("check leaderboard") }
            aiBinding.chipVaultNotes.setOnClickListener { processInput("open notes vault") }
            aiBinding.chipRandomMatch.setOnClickListener { processInput("start random match") }
        }
        dialog.show()
    }

    private fun checkForAppUpdates() {
        val prefs = getSharedPreferences("unilink_update_prefs", MODE_PRIVATE)

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("app_config").document("update_info")
            .get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed || snapshot == null || !snapshot.exists()) return@addOnSuccessListener

                val latestVersionCode = (snapshot.getLong("minVersionCode") ?: 1L).toInt()
                val updateTitle = snapshot.getString("updateTitle") ?: "New Version Available 🚀"
                val updateMessage = snapshot.getString("updateMessage") ?: "A new version of UniLink is available with important fixes and features. Tap below to download from Google Drive!"
                val updateUrl = snapshot.getString("updateUrl")
                val isForceUpdate = snapshot.getBoolean("isForceUpdate") ?: false

                val currentVersionCode = com.example.unilink.BuildConfig.VERSION_CODE
                val lastDismissedVersion = prefs.getInt("last_dismissed_version_code", 0)

                if (latestVersionCode > currentVersionCode && latestVersionCode > lastDismissedVersion && !updateUrl.isNullOrEmpty()) {
                    showUpdateDialog(latestVersionCode, updateTitle, updateMessage, updateUrl, isForceUpdate)
                }
            }
    }

    private fun showUpdateDialog(versionCode: Int, title: String, message: String, url: String, isForce: Boolean) {
        if (isFinishing || isDestroyed) return

        getSharedPreferences("unilink_update_prefs", MODE_PRIVATE)
            .edit()
            .putInt("last_dismissed_version_code", versionCode)
            .apply()

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(!isForce)
            .setPositiveButton("Download Update 🚀") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    startActivity(intent)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(this, "Opening browser for update...", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

        if (!isForce) {
            builder.setNegativeButton("Later", null)
        }

        builder.show()
    }

    override fun onResume() {
        super.onResume()
        updateBottomNavThemeColors()
        fetchAndSaveUserLocation()
        if (ThemeUtils.checkAndRecreateIfThemeChanged(this, appliedThemeName)) {
            appliedThemeName = ThemeUtils.getSelectedTheme(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationListener?.remove()
        unreadChatListener?.remove()
        updateOnlineStatus(online = false)
    }
}
