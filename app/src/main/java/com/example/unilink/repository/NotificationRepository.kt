package com.example.unilink.repository

import com.example.unilink.models.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun sendNotification(receiverUid: String, type: String, title: String, message: String, targetId: String? = null) {
        val currentUid = auth.currentUser?.uid ?: return
        if (currentUid == receiverUid) return

        db.collection("users").document(currentUid).get().addOnSuccessListener { doc ->
            val senderName = doc.getString("name") ?: "User"
            val notificationId = db.collection("users").document(receiverUid).collection("notifications").document().id
            
            val notification = hashMapOf(
                "notificationId" to notificationId,
                "type" to type,
                "title" to title,
                "message" to message,
                "senderId" to currentUid,
                "senderName" to senderName,
                "targetId" to targetId,
                "isRead" to false,
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("users").document(receiverUid).collection("notifications").document(notificationId).set(notification)

            // Trigger FCM Data Push Notification to Receiver's device token even when app is closed
            db.collection("users").document(receiverUid).get().addOnSuccessListener { receiverDoc ->
                val fcmToken = receiverDoc.getString("fcmToken") ?: ""
                if (fcmToken.isNotEmpty()) {
                    sendFcmPushNotification(fcmToken, title, message, type, targetId, currentUid)
                }
            }

            if (type == "video_call") {
                db.collection("users").document(receiverUid).update(
                    mapOf(
                        "activeIncomingCallId" to targetId,
                        "activeCallerName" to senderName,
                        "activeCallerUid" to currentUid,
                        "activeCallTimestamp" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
    }

    private fun sendFcmPushNotification(token: String, title: String, message: String, type: String, targetId: String?, senderId: String) {
        if (token.isBlank()) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val url = java.net.URL("https://fcm.googleapis.com/fcm/send")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "key=AAAA3k451NL:APA91bG_nl_server_key_placeholder")
                conn.doOutput = true

                val jsonBody = org.json.JSONObject().apply {
                    put("to", token)
                    put("priority", "high")
                    put("data", org.json.JSONObject().apply {
                        put("title", title)
                        put("body", message)
                        put("type", type)
                        put("chatId", targetId ?: "")
                        put("senderId", senderId)
                    })
                }

                java.io.OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }
                val responseCode = conn.responseCode
                android.util.Log.d("NotificationRepository", "FCM Push Sent, Status: $responseCode")
            } catch (e: Exception) {
                android.util.Log.e("NotificationRepository", "FCM Push Error: ${e.message}")
            }
        }
    }

    fun sendGlobalUpdateNotification(title: String, message: String, updateUrl: String? = null, onComplete: (Boolean) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onComplete(false)

        val updateData = hashMapOf(
            "minVersionCode" to System.currentTimeMillis().toInt(),
            "updateTitle" to title,
            "updateMessage" to message,
            "updateUrl" to (updateUrl ?: "https://drive.google.com"),
            "isForceUpdate" to false,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("app_config").document("update_info").set(updateData)

        db.collection("users").get().addOnSuccessListener { snapshot ->
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                val receiverUid = doc.id
                val notificationRef = db.collection("users").document(receiverUid).collection("notifications").document()
                val notification = hashMapOf(
                    "notificationId" to notificationRef.id,
                    "type" to "system_update",
                    "title" to title,
                    "message" to message,
                    "senderId" to currentUid,
                    "senderName" to "UniLink Broadcast 🚀",
                    "targetId" to updateUrl,
                    "isRead" to false,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                batch.set(notificationRef, notification)
            }
            batch.commit().addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun observeNotifications(onUpdate: (List<Notification>) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection("users").document(uid).collection("notifications")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val notifications = snapshot.mapNotNull { doc ->
                    try {
                        doc.toObject(Notification::class.java).apply {
                            if (this.notificationId.isEmpty()) {
                                this.notificationId = doc.id
                            }
                        }
                    } catch (_: Exception) {
                        null
                    }
                }.sortedByDescending { notif ->
                    notif.timestamp?.time ?: System.currentTimeMillis()
                }

                onUpdate(notifications)
            }
    }

    fun markAsRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (notificationId.isEmpty()) return
        db.collection("users").document(uid).collection("notifications").document(notificationId)
            .update("isRead", true)
    }

    fun markNotificationsByTargetAsRead(targetId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (targetId.isEmpty()) return
        db.collection("users").document(uid).collection("notifications")
            .whereEqualTo("targetId", targetId)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshots ->
                if (snapshots == null || snapshots.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                snapshots.documents.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
                batch.commit()
            }
    }

    fun markAllAsRead() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("notifications")
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshots ->
                if (snapshots == null || snapshots.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                snapshots.documents.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
                batch.commit()
            }
    }
}
