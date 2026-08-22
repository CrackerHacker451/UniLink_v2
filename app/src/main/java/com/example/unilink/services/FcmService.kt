package com.example.unilink.services

import android.util.Log
import com.example.unilink.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d("FcmService", "Message received: ${message.data}")

        val title = message.notification?.title ?: message.data["title"] ?: "UniLink"
        val body = message.notification?.body ?: message.data["body"] ?: "You have a new update"
        val chatId = message.data["chatId"]
        
        NotificationHelper.showNotification(this, title, body, chatId)
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
