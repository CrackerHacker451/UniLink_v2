package com.example.unilink.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.unilink.R
import com.example.unilink.databinding.ActivityMainBinding
import com.example.unilink.fragments.HomeFragment
import com.example.unilink.fragments.MessagesFragment
import com.example.unilink.fragments.SearchFragment
import com.example.unilink.fragments.ProjectsFragment
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.ThemeUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()
    private val chatRepository = com.example.unilink.repository.ChatRepository()
    private var currentFragmentTag: String? = null
    private var chatsListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            updateFcmToken()
        } else {
            Toast.makeText(this, "Notifications disabled.", Toast.LENGTH_LONG).show()
        }
    }

    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try { ThemeUtils.applyTheme(this) } catch (_: Exception) {}
        super.onCreate(savedInstanceState)
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomNavigation.selectedItemId != R.id.nav_home) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_home
                } else {
                    finish()
                }
            }
        })
        
        if (auth.currentUser == null) {
            Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-Edge Insets Handling
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Adjust Bottom Navigation Card margin to float above navigation bar
            val layoutParams = binding.navCard.layoutParams as android.view.ViewGroup.MarginLayoutParams
            layoutParams.bottomMargin = systemBars.bottom + (24 * resources.displayMetrics.density).toInt()
            binding.navCard.layoutParams = layoutParams

            // Ensure content doesn't get hidden behind the floating elements
            // We use padding on the root to stop the fragments just above the nav card
            binding.mainRoot.updatePadding(top = 0, bottom = 0) 
            
            insets
        }

        userRepository.getCurrentUser { user ->
            if (isFinishing || isDestroyed) return@getCurrentUser
            
            val isAuraEnabled = com.example.unilink.utils.PrefsUtils.isAuraEnabled(this)
            val isPro = user?.isPremium == true
            binding.auraParticles.visibility = if (isPro && isAuraEnabled) android.view.View.VISIBLE else android.view.View.GONE
            binding.auraParticles.setPremiumMode(isPro)

            // Sync theme only on fresh launch
            if (savedInstanceState == null) {
                val remoteTheme = user?.appTheme ?: "Ars White"
                val localTheme = ThemeUtils.getSelectedTheme(this)
                
                if (remoteTheme != localTheme) {
                    ThemeUtils.saveTheme(this, remoteTheme)
                    Log.d("MainActivity", "Theme mismatch ($localTheme vs $remoteTheme), recreating...")
                    recreate()
                }
            }
        }

        setupNavigation(savedInstanceState)
        setupClickListeners()
        askNotificationPermission()
        observeUnreadMessages()
        setupGlobalNotificationListener()
        refreshLocation()
        updateOnlineStatus(online = true)
        startAnimations()
        initUniAI()
        
        // Handle Notification Intent
        intent.getStringExtra("chatId")?.let { cid ->
            val chatIntent = Intent(this, ChatActivity::class.java).apply {
                putExtra("chatId", cid)
            }
            startActivity(chatIntent)
        }

        // Sync theme only on fresh launch
        if (savedInstanceState == null) {
            userRepository.getCurrentUser { user ->
                if (isFinishing || isDestroyed) return@getCurrentUser
                val remoteTheme = user?.appTheme ?: "Ars White"
                val localTheme = ThemeUtils.getSelectedTheme(this)
                
                if (remoteTheme != localTheme) {
                    ThemeUtils.saveTheme(this, remoteTheme)
                    Log.d("MainActivity", "Theme mismatch ($localTheme vs $remoteTheme), recreating...")
                    recreate()
                }
            }
        }
        
        Log.d("MainActivity", "onCreate completed successfully")
    }

    private fun setupGlobalNotificationListener() {
        val uid = auth.currentUser?.uid ?: return
        notificationListener?.remove()
        
        val startTime = System.currentTimeMillis()
        
        notificationListener = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                
                for (dc in snapshots.documentChanges) {
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val notification = dc.document.toObject(com.example.unilink.models.Notification::class.java)
                        
                        // Show notification if it's new (created after we started the app) or timestamp is null (just sent)
                        val noteTime = notification.timestamp?.time ?: System.currentTimeMillis()
                        if (noteTime >= startTime) {
                            com.example.unilink.utils.NotificationHelper.showNotification(
                                this,
                                notification.title,
                                notification.message,
                                notification.targetId
                            )
                        }
                    }
                }
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("chatId")?.let { cid ->
            val chatIntent = Intent(this, ChatActivity::class.java).apply {
                putExtra("chatId", cid)
            }
            startActivity(chatIntent)
        }
    }

    private fun setupNavigation(savedInstanceState: Bundle?) {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            try {
                com.example.unilink.utils.HapticUtils.playSelection(binding.bottomNavigation)
                when (item.itemId) {
                    R.id.nav_home -> {
                        binding.fabCreate.visibility = android.view.View.VISIBLE
                        loadFragment(HomeFragment())
                        true
                    }
                    R.id.nav_search -> {
                        binding.fabCreate.visibility = android.view.View.GONE
                        loadFragment(SearchFragment())
                        true
                    }
                    R.id.nav_projects -> {
                        binding.fabCreate.visibility = android.view.View.VISIBLE
                        loadFragment(ProjectsFragment())
                        true
                    }
                    R.id.nav_messages -> {
                        binding.fabCreate.visibility = android.view.View.GONE
                        loadFragment(MessagesFragment())
                        true
                    }
                    R.id.nav_profile -> {
                        startActivity(Intent(this, ProfileActivity::class.java))
                        false
                    }
                    else -> false
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Navigation error", e)
                false
            }
        }

        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = R.id.nav_home
        }
    }

    private fun setupClickListeners() {
        binding.fabCreate.setOnClickListener {
            val currentItemId = binding.bottomNavigation.selectedItemId
            if (currentItemId == R.id.nav_projects) {
                startActivity(Intent(this, CreateTeamActivity::class.java))
            } else {
                startActivity(Intent(this, CreatePostActivity::class.java))
            }
        }
    }

    private fun updateOnlineStatus(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        userRepository.getCurrentUser { user ->
            // VIP privilege: Incognito Mode
            val finalOnline = if ((user?.isVip == true) && user.isIncognito && online) {
                false 
            } else {
                online
            }
            
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("isOnline", finalOnline)
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Status update failed: ${e.message}")
                }
        }
    }

    private fun observeUnreadMessages() {
        val uid = auth.currentUser?.uid ?: return
        chatsListener?.remove()
        chatsListener = chatRepository.getRecentChats { chats ->
            val totalUnread = chats.sumOf { room ->
                room.unreadCounts[uid] ?: 0
            }
            val badge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_messages)
            if (totalUnread > 0) {
                badge.isVisible = true
                badge.number = totalUnread
            } else {
                badge.isVisible = false
            }
        }
    }

    private fun refreshLocation() {
        if (com.example.unilink.utils.LocationHelper.hasLocationPermission(this)) {
            com.example.unilink.utils.LocationHelper.getLastLocation(this) { lat, lng, _, _ ->
                val uid = auth.currentUser?.uid ?: return@getLastLocation
                if (lat != null && lng != null) {
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                        .update(mapOf("latitude" to lat, "longitude" to lng))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatsListener?.remove()
        notificationListener?.remove()
        if (auth.currentUser != null) {
            updateOnlineStatus(online = false)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                updateFcmToken()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            updateFcmToken()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        val tag = fragment::class.java.simpleName
        if (currentFragmentTag == tag) return
        currentFragmentTag = tag

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.fade_out_subtle,
                R.anim.fade_in_subtle,
                R.anim.slide_out_left
            )
            .replace(R.id.fragmentContainer, fragment, tag)
            .commitAllowingStateLoss()
    }

    private fun updateFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update("fcmToken", token)
            } else {
                Log.e("MainActivity", "FCM token update failed: ${task.exception?.message}")
            }
        }
    }

    private fun startAnimations() {
        val pulse = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.fabCreate.startAnimation(pulse)
        binding.fabUniAI.startAnimation(pulse)
    }

    private fun initUniAI() {
        binding.fabUniAI.setOnClickListener {
            showAiAssistant()
        }
    }

    fun showAiAssistant() {
        val dialog = BottomSheetDialog(this)
        val aiBinding = com.example.unilink.databinding.BottomSheetUniAiBinding.inflate(layoutInflater)
        dialog.setContentView(aiBinding.root)
        
        val adapter = com.example.unilink.adapters.AiChatAdapter()
        aiBinding.rvAiChat.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        aiBinding.rvAiChat.adapter = adapter
        
        userRepository.getCurrentUser { user ->
            val userContext = user?.let {
                "Name: ${it.name}, Branch: ${it.branch}, Skills: ${it.skills.joinToString()}, UniCoins: ${it.uniCoins}, Impact Score: ${it.impactScore}, IsPro: ${it.isPremium}"
            }

            val welcomeMsg = if (user?.isPremium == true) {
                "Welcome back, Pro member ${user.name}! I'm prioritizing your requests today. How can I assist your campus journey?"
            } else {
                "Hello ${user?.name ?: "Student"}! I am UniAI. How can I help you today?"
            }
            
            adapter.addMessage(com.example.unilink.adapters.AiMessage(welcomeMsg, false))
            
            aiBinding.btnAiSend.setOnClickListener {
                val prompt = aiBinding.etAiInput.text.toString().trim()
                if (prompt.isNotEmpty()) {
                    adapter.addMessage(com.example.unilink.adapters.AiMessage(prompt, true))
                    aiBinding.etAiInput.setText("")
                    aiBinding.rvAiChat.smoothScrollToPosition(adapter.itemCount - 1)
                    
                    lifecycleScope.launch {
                        val response = com.example.unilink.repository.GeminiRepository().getAiHint(prompt, user?.isPremium ?: false, userContext)
                        adapter.addMessage(com.example.unilink.adapters.AiMessage(response ?: "I'm having trouble connecting. Try again!", false))
                        aiBinding.rvAiChat.smoothScrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
        }
        dialog.show()
    }
}
