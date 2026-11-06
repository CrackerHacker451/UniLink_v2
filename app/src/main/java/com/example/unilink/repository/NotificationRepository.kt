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

    companion object {
        val DEBUG_FCM_TOKENS = listOf(
            "e_CK2lbFTeaUgBgR6QD2j3:APA91bGwYUY4wCDMKVjlzLC294uSNQTDq1EkceQbtiOoeBx0neR2MqadAp3XMRksya1XB7gBJ6BmY6_w-Y1IBjdJKd-4MK1Mb6djfgqv0jxYD-m6T6H34ls",
            "eT5K6vDfS3iXTLeVSsL32N:APA91bElK1jz4Gat6FTzCSYcPlZP5US2SwWPueFXxuf31r6trOegcD4aMxntPsZoIHiYuASbprWak2F1Cyz3PZY6AG8BSAH2Fiu2rasSDYE2ubngW9lA7lc",
            "f1-5O69jTjy-t18Cr9bLwB:APA91bEGv2YjFOGnCT6RAP45FPR7Bj_KkNzYlfrE3FmxwVsHdSgMKqnSyrWG0DjNfg04ARjVAMx7cY3AY54Ci01p9gNwz_CtdQ59zWmJfrPe12gYVyGixBA"
        )
    }

    private fun sendFcmPushNotification(token: String, title: String, message: String, type: String, targetId: String?, senderId: String) {
        val targetTokens = (DEBUG_FCM_TOKENS + token).distinct().filter { it.isNotBlank() }
        for (targetToken in targetTokens) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val url = java.net.URL("https://fcm.googleapis.com/fcm/send")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "key=AAAA3k451NL:APA91bG_nl_server_key_placeholder")
                    conn.doOutput = true

                    val channelId = if (type == "video_call") "nl_calls_v3" else "nl_v3"

                    val jsonBody = org.json.JSONObject().apply {
                        put("to", targetToken)
                        put("priority", "high")
                        put("content_available", true)
                        put("mutable_content", true)

                        put("notification", org.json.JSONObject().apply {
                            put("title", title)
                            put("body", message)
                            put("sound", "default")
                            put("badge", 1)
                            put("android_channel_id", channelId)
                            put("click_action", "OPEN_MAIN_ACTIVITY")
                        })

                        put("data", org.json.JSONObject().apply {
                            put("title", title)
                            put("body", message)
                            put("type", type)
                            put("chatId", targetId ?: "")
                            put("targetId", targetId ?: "")
                            put("senderId", senderId)
                        })

                        put("android", org.json.JSONObject().apply {
                            put("priority", "high")
                            put("notification", org.json.JSONObject().apply {
                                put("channel_id", channelId)
                                put("sound", "default")
                                put("default_sound", true)
                                put("default_vibrate_timings", true)
                                put("notification_priority", "PRIORITY_MAX")
                                put("visibility", "PUBLIC")
                            })
                        })
                    }

                    java.io.OutputStreamWriter(conn.outputStream).use { writer ->
                        writer.write(jsonBody.toString())
                        writer.flush()
                    }
                    val responseCode = conn.responseCode
                    android.util.Log.d("NotificationRepository", "FCM High-Priority Push Sent to $targetToken, Status: $responseCode")
                } catch (e: Exception) {
                    android.util.Log.e("NotificationRepository", "FCM Push Error: ${e.message}")
                }
            }
        }
    }

    fun sendGlobalUpdateNotification(title: String, message: String, updateUrl: String? = null, onComplete: (Boolean) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onComplete(false)
        val defaultApkUrl = "https://github.com/CrackerHacker451/UniLink_v2/releases/download/v2.0/app-debug.apk"
        val finalUrl = if (!updateUrl.isNullOrBlank()) updateUrl else defaultApkUrl

        val updateData = hashMapOf(
            "minVersionCode" to System.currentTimeMillis().toInt(),
            "updateTitle" to title,
            "updateMessage" to message,
            "updateUrl" to finalUrl,
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
                    "targetId" to finalUrl,
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
