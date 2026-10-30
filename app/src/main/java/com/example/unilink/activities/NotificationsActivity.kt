package com.example.unilink.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.R
import com.example.unilink.adapters.NotificationAdapter
import com.example.unilink.databinding.ActivityNotificationsBinding
import com.example.unilink.repository.NotificationRepository
import com.example.unilink.utils.ThemeUtils

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private val notificationRepository = NotificationRepository()
    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        
        val adapter = NotificationAdapter { notification ->
            notificationRepository.markAsRead(notification.notificationId)
            
            // Handle navigation
            when (notification.type) {
                "chat", "duel", "token" -> {
                    notification.targetId?.let { cid ->
                        val intent = Intent(this, ChatActivity::class.java).apply {
                            putExtra("chatId", cid)
                        }
                        startActivity(intent)
                    }
                }
                "vouch" -> {
                    notification.senderId?.let { sid ->
                        val intent = Intent(this, ProfileActivity::class.java).apply {
                            putExtra("userId", sid)
                        }
                        startActivity(intent)
                    }
                }
                "video_call" -> {
                    notification.targetId?.let { cid ->
                        val intent = Intent(this, VideoCallActivity::class.java).apply {
                            putExtra("channelId", cid)
                            putExtra("partnerId", notification.senderId ?: "")
                            putExtra("partnerName", notification.senderName ?: "Peer")
                            putExtra("isOffer", false)
                        }
                        startActivity(intent)
                    }
                }
                "system_update" -> {
                    val url = notification.targetId?.ifEmpty { "https://drive.google.com" } ?: "https://drive.google.com"
                    try {
                        android.widget.Toast.makeText(this, "Opening update download link... 🚀", android.widget.Toast.LENGTH_SHORT).show()
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(this, "Opening browser for update...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.rvNotifications.adapter = adapter
        
        notificationListener = notificationRepository.observeNotifications { notifications ->
            if (isFinishing || isDestroyed) return@observeNotifications
            adapter.submitList(notifications)
            if (notifications.any { !it.isRead }) {
                notificationRepository.markAllAsRead()
            }
        }

        binding.toolbar.inflateMenu(R.menu.notifications_menu)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_mark_all_read) {
                notificationRepository.markAllAsRead()
                true
            } else false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationListener?.remove()
    }
}
