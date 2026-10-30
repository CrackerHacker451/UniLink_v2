package com.example.unilink.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

data class CampusLiveStream(
    val streamId: String = "",
    val title: String = "",
    val description: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val hostAvatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val isLive: Boolean = true,
    val viewerCount: Int = 1,
    val startedAtDate: java.util.Date? = null
)

data class StreamChatMessage(
    val id: String = "",
    val senderName: String = "",
    val messageText: String = "",
    val isVip: Boolean = false
)

data class StreamReaction(
    val id: String = "",
    val emoji: String = "❤️",
    val startX: Float = 300f
)

class LiveStreamRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun publishLiveStream(title: String, description: String, isPrivate: Boolean, onComplete: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false, null)
        val streamId = db.collection("live_streams").document().id

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: auth.currentUser?.displayName ?: "Campus Host"
            val avatar = doc.getString("profileImageUrl") ?: auth.currentUser?.photoUrl?.toString()

            val streamData = hashMapOf(
                "streamId" to streamId,
                "title" to title,
                "description" to description,
                "hostUid" to uid,
                "hostName" to name,
                "hostAvatarUrl" to avatar,
                "isPrivate" to isPrivate,
                "isLive" to true,
                "viewerCount" to 1,
                "startedAt" to FieldValue.serverTimestamp()
            )

            db.collection("live_streams").document(streamId).set(streamData)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Announce live stream to campus feed
                        LiveFeedRepository().publishPulse("🔴 $name is now LIVE: '$title'! Tap to join stream! 📺")
                    }
                    onComplete(task.isSuccessful, if (task.isSuccessful) streamId else null)
                }
        }.addOnFailureListener {
            onComplete(false, null)
        }
    }

    fun observeActiveStreams(onUpdate: (List<CampusLiveStream>) -> Unit): ListenerRegistration {
        return db.collection("live_streams")
            .whereEqualTo("isLive", true)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val streams = snapshot.mapNotNull { doc ->
                    try {
                        val id = doc.getString("streamId") ?: doc.id
                        val title = doc.getString("title") ?: "Campus Live Stream"
                        val desc = doc.getString("description") ?: ""
                        val hostUid = doc.getString("hostUid") ?: ""
                        val hostName = doc.getString("hostName") ?: "Campus Host"
                        val avatar = doc.getString("hostAvatarUrl")
                        val isPrivate = doc.getBoolean("isPrivate") ?: false
                        val isLive = doc.getBoolean("isLive") ?: true
                        val viewers = (doc.getLong("viewerCount") ?: 1L).toInt()

                        CampusLiveStream(
                            streamId = id,
                            title = title,
                            description = desc,
                            hostUid = hostUid,
                            hostName = hostName,
                            hostAvatarUrl = avatar,
                            isPrivate = isPrivate,
                            isLive = isLive,
                            viewerCount = viewers
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

                onUpdate(streams)
            }
    }

    fun endLiveStream(streamId: String) {
        if (streamId.isEmpty()) return
        db.collection("live_streams").document(streamId).update(
            "isLive", false,
            "endedAt", FieldValue.serverTimestamp()
        )
    }

    fun sendStreamChatMessage(streamId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        if (streamId.isEmpty() || text.isBlank()) return

        val currentUser = auth.currentUser
        val fallbackName = currentUser?.displayName ?: "Campus Peer"

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: fallbackName
            val isPro = doc.getBoolean("isPremium") ?: false

            val msgRef = db.collection("live_streams").document(streamId).collection("chat").document()
            val msgData = hashMapOf(
                "id" to msgRef.id,
                "senderName" to name,
                "messageText" to text,
                "isVip" to isPro,
                "timestamp" to FieldValue.serverTimestamp()
            )

            msgRef.set(msgData)
        }.addOnFailureListener {
            val msgRef = db.collection("live_streams").document(streamId).collection("chat").document()
            val msgData = hashMapOf(
                "id" to msgRef.id,
                "senderName" to fallbackName,
                "messageText" to text,
                "isVip" to false,
                "timestamp" to FieldValue.serverTimestamp()
            )
            msgRef.set(msgData)
        }
    }

    fun observeStreamChat(streamId: String, onUpdate: (List<StreamChatMessage>) -> Unit): ListenerRegistration? {
        if (streamId.isEmpty()) return null

        return db.collection("live_streams").document(streamId).collection("chat")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val messages = snapshot.mapNotNull { doc ->
                    try {
                        val id = doc.getString("id") ?: doc.id
                        val sender = doc.getString("senderName") ?: "Student"
                        val text = doc.getString("messageText") ?: ""
                        val isVip = doc.getBoolean("isVip") ?: false

                        StreamChatMessage(id = id, senderName = sender, messageText = text, isVip = isVip)
                    } catch (_: Exception) {
                        null
                    }
                }

                onUpdate(messages)
            }
    }

    fun sendStreamReaction(streamId: String, emoji: String) {
        if (streamId.isEmpty()) return
        val randomX = (100..800).random().toFloat()
        val ref = db.collection("live_streams").document(streamId).collection("reactions").document()
        val data = hashMapOf(
            "id" to ref.id,
            "emoji" to emoji,
            "startX" to randomX,
            "timestamp" to FieldValue.serverTimestamp()
        )
        ref.set(data)
    }

    fun observeStreamReactions(streamId: String, onReactionReceived: (StreamReaction) -> Unit): ListenerRegistration? {
        if (streamId.isEmpty()) return null
        return db.collection("live_streams").document(streamId).collection("reactions")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val id = doc.getString("id") ?: doc.id
                        val emoji = doc.getString("emoji") ?: "❤️"
                        val startX = (doc.getDouble("startX") ?: 300.0).toFloat()
                        onReactionReceived(StreamReaction(id = id, emoji = emoji, startX = startX))
                    }
                }
            }
    }
}
