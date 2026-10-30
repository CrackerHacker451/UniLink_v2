package com.example.unilink.repository

import com.example.unilink.models.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

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
