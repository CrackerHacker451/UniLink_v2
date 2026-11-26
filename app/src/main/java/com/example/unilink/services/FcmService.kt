package com.example.unilink.services

import android.util.Log
import com.example.unilink.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {
    companion object {
        fun subscribeToGlobalUpdates() {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnFailureListener { Log.w("FcmService", "Topic subscription failed") }
        }
        fun refreshToken() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { saveToken(uid, it) }
                .addOnFailureListener { Log.w("FcmService", "Token refresh failed") }
        }
        private fun saveToken(uid: String, token: String) {
            if (token.isBlank() || FirebaseAuth.getInstance().currentUser?.uid != uid) return
            FirebaseFirestore.getInstance().collection("pushRegistrations").document(uid)
                .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
                .addOnFailureListener { Log.w("FcmService", "Token registration failed") }
        }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (data["recipientUid"] != null && data["recipientUid"] != uid) return
        if (data["notificationId"] != null && data["recipientUid"] != uid) return
        val title = data["title"] ?: message.notification?.title ?: "UniLink"
        val body = data["body"] ?: message.notification?.body ?: "You have a new update"
        val callId = data["targetId"]
        val callerId = data["senderId"]
        if (data["type"] == "video_call" && !callId.isNullOrBlank() && !callerId.isNullOrBlank()) {
            NotificationHelper.showIncomingCallNotification(this, "Campus peer", callId, callerId)
        } else {
            NotificationHelper.showNotification(this, title, body, notificationId = data["notificationId"])
        }
    }
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        saveToken(uid, token)
    }
}
