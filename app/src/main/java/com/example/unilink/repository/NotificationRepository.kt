package com.example.unilink.repository

import com.example.unilink.models.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue

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
        }
    }

    fun observeNotifications(onUpdate: (List<Notification>) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection("users").document(uid).collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val notifications = snapshot?.mapNotNull { it.toObject(Notification::class.java) } ?: emptyList()
                onUpdate(notifications)
            }
    }

    fun markAsRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("notifications").document(notificationId)
            .update("isRead", true)
    }

    fun markNotificationsByTargetAsRead(targetId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("notifications")
            .whereEqualTo("targetId", targetId)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshots ->
                val batch = db.batch()
                snapshots.documents.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
                if (!snapshots.isEmpty) batch.commit()
            }
    }

    fun markAllAsRead() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("notifications")
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshots ->
                val batch = db.batch()
                snapshots.documents.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
                if (!snapshots.isEmpty) batch.commit()
            }
    }
}
