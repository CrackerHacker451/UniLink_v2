package com.example.unilink.services

import android.util.Log
import com.example.unilink.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {

    companion object {
        fun subscribeToGlobalUpdates() {
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("FcmService", "Subscribed to all_users topic successfully")
                        }
                    }
            } catch (e: Exception) {
                Log.e("FcmService", "Failed to subscribe to all_users topic: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d("FcmService", "Message received: ${message.data}")

        val title = message.notification?.title ?: message.data["title"] ?: "Newton's Lobby"
        val body = message.notification?.body ?: message.data["body"] ?: "You have a new update"
        val type = message.data["type"] ?: ""
        val chatId = message.data["chatId"]
        val senderId = message.data["senderId"] ?: ""

        if (type == "video_call" || title.contains("Video Call", ignoreCase = true)) {
            NotificationHelper.showIncomingCallNotification(
                context = this,
                callerName = title.replace("Incoming Video Call 🎥", "").trim().ifEmpty { "Campus Peer" },
                channelId = chatId ?: "call_room",
                callerUid = senderId
            )
        } else {
            NotificationHelper.showNotification(this, title, body, chatId)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        updateTokenInFirestore(token)
    }

    private fun updateTokenInFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .update("fcmToken", token)
            .addOnFailureListener { e ->
                Log.e("FcmService", "Failed to update token: ${e.message}")
            }
    }
}
