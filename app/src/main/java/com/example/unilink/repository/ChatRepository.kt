package com.example.unilink.repository

import android.util.Log
import com.example.unilink.models.ChatMessage
import com.example.unilink.models.ChatRoom
import com.example.unilink.utils.EncryptionUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ListenerRegistration

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun sendMessage(
        chatId: String,
        text: String,
        senderUsername: String,
        isGroup: Boolean,
        receiverId: String? = null,
        replyToId: String? = null,
        replyToText: String? = null,
        onComplete: (Boolean) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid ?: run {
            Log.e("ChatRepo", "Send failed: User not logged in")
            onComplete(false)
            return
        }

        val finalReceiverId = if (!isGroup) {
            receiverId ?: chatId.split("_").firstOrNull { it != currentUserId } ?: ""
        } else ""

        if (!isGroup && finalReceiverId.isEmpty()) {
            Log.e("ChatRepo", "Send failed: Could not determine receiverId for chatId $chatId")
            onComplete(false)
            return
        }

        // Optimized block check: Skip if it's a group
        if (!isGroup) {
            db.collection("users").document(finalReceiverId).get()
                .addOnSuccessListener { doc ->
                    val blockedUsers = doc.get("blockedUsers") as? List<*>
                    if (blockedUsers?.contains(currentUserId) == true) {
                        Log.e("ChatRepo", "Message blocked by receiver")
                        onComplete(false)
                    } else {
                        proceedWithSendMessage(chatId, text, senderUsername, isGroup, finalReceiverId, replyToId, replyToText, onComplete)
                    }
                }
                .addOnFailureListener {
                    // Proceed if fetch fails to avoid blocking legitimate users due to temporary network issues
                    proceedWithSendMessage(chatId, text, senderUsername, isGroup, finalReceiverId, replyToId, replyToText, onComplete)
                }
        } else {
            proceedWithSendMessage(chatId, text, senderUsername, isGroup, "", replyToId, replyToText, onComplete)
        }
    }

    fun sendVoiceMessage(chatId: String, receiverId: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val messageRef = db.collection("chats").document(chatId).collection("messages").document()
        
        val voiceData = hashMapOf(
            "messageId" to messageRef.id,
            "senderId" to currentUserId,
            "receiverId" to receiverId,
            "messageText" to "🎙️ Voice Message",
            "type" to "voice",
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "sent"
        )
        
        messageRef.set(voiceData).addOnCompleteListener { 
            onComplete(it.isSuccessful)
            if (it.isSuccessful) {
                NotificationRepository().sendNotification(receiverId, "chat", "New Voice Message", "🎙️ Listen now", chatId)
            }
        }
    }

    fun sendDuelRequest(chatId: String, receiverId: String, wager: Int, subject: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val messageRef = db.collection("chats").document(chatId).collection("messages").document()
        
        val duelData = hashMapOf(
            "messageId" to messageRef.id,
            "senderId" to currentUserId,
            "receiverId" to receiverId,
            "messageText" to "⚔️ Challenged you to a $subject Duel!",
            "type" to "duel",
            "duelWager" to wager,
            "duelSubject" to subject,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "sent"
        )
        
        val chatRoomUpdate = mapOf(
            "lastMessage" to "⚔️ Duel Challenge!",
            "lastTimestamp" to FieldValue.serverTimestamp()
        )
        
        val batch = db.batch()
        batch.set(messageRef, duelData)
        batch.update(db.collection("chats").document(chatId), chatRoomUpdate)
        
        batch.commit().addOnCompleteListener { 
            onComplete(it.isSuccessful)
            if (it.isSuccessful) {
                NotificationRepository().sendNotification(
                    receiverUid = receiverId,
                    type = "duel",
                    title = "⚔️ Duel Challenge!",
                    message = "$subject challenge received!",
                    targetId = chatId
                )
            }
        }
    }

    fun sendTokenTransfer(chatId: String, receiverId: String, amount: Int, note: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // 1. Transaction to deduct from sender and add to receiver
        val senderRef = db.collection("users").document(currentUserId)
        val receiverRef = db.collection("users").document(receiverId)
        
        db.runTransaction { transaction ->
            val sender = transaction.get(senderRef)
            val currentBalance = sender.getLong("uniCoins") ?: 0L
            if (currentBalance < amount) {
                throw Exception("Insufficient UniCoins")
            }
            
            transaction.update(senderRef, "uniCoins", currentBalance - amount)
            transaction.update(receiverRef, "uniCoins", com.google.firebase.firestore.FieldValue.increment(amount.toLong()))
            
            // Create Receipt Log
            val logRef = db.collection("token_transfers").document()
            val logData = hashMapOf(
                "transferId" to logRef.id,
                "senderId" to currentUserId,
                "receiverId" to receiverId,
                "amount" to amount,
                "timestamp" to FieldValue.serverTimestamp(),
                "note" to note
            )
            transaction.set(logRef, logData)
            
            null
        }.addOnSuccessListener {
            // 2. Create message in chat
            val messageRef = db.collection("chats").document(chatId).collection("messages").document()
            val tokenData = hashMapOf(
                "messageId" to messageRef.id,
                "senderId" to currentUserId,
                "receiverId" to receiverId,
                "messageText" to "💰 Sent $amount UniCoins",
                "type" to "token",
                "uniCoinAmount" to amount,
                "tokenReason" to note,
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "sent"
            )
            
            messageRef.set(tokenData).addOnCompleteListener { 
                onComplete(it.isSuccessful)
                if (it.isSuccessful) {
                    NotificationRepository().sendNotification(
                        receiverUid = receiverId,
                        type = "chat",
                        title = "Received UniCoins! 💰",
                        message = "You received $amount coins from a fellow student.",
                        targetId = chatId
                    )
                }
            }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    private fun proceedWithSendMessage(
        chatId: String,
        text: String,
        senderUsername: String,
        isGroup: Boolean,
        finalReceiverId: String,
        replyToId: String? = null,
        replyToText: String? = null,
        onComplete: (Boolean) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // Encrypt message text (E2EE)
        val encryptedText = EncryptionUtils.encrypt(text, chatId)

        // Fetch current user avatar
        db.collection("users").document(currentUserId).get().addOnSuccessListener { userDoc ->
            val senderAvatar = userDoc.getString("profileImageUrl")
            
            val chatRoomRef = db.collection("chats").document(chatId)
            val messageRef = chatRoomRef.collection("messages").document()
            val msgId = messageRef.id

            val messageData = hashMapOf(
                "messageId" to msgId,
                "senderId" to currentUserId,
                "senderUsername" to senderUsername.ifBlank { "User" },
                "senderAvatarUrl" to senderAvatar,
                "receiverId" to finalReceiverId,
                "messageText" to encryptedText,
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "sent",
                "type" to "text",
                "replyToId" to replyToId,
                "replyToText" to replyToText
            )

        val chatRoomUpdate = mutableMapOf<String, Any>(
            "chatId" to chatId,
            "lastMessage" to encryptedText,
            "lastMessageSenderId" to currentUserId,
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "isGroup" to isGroup
        )

        if (isGroup) {
            chatRoomUpdate["participants"] = FieldValue.arrayUnion(currentUserId)
        } else {
            chatRoomUpdate["participants"] = listOf(currentUserId, finalReceiverId)
        }

        val batch = db.batch()
        // Using set with merge everywhere ensures the document is created if it's a new chat
        batch.set(chatRoomRef, chatRoomUpdate, SetOptions.merge())
        
        // Use set with merge for unread count to handle non-existent documents or fields
        if (!isGroup) {
            batch.set(chatRoomRef, mapOf("unreadCounts" to mapOf(finalReceiverId to com.google.firebase.firestore.FieldValue.increment(1))), SetOptions.merge())
        } else {
            // For groups, we need to increment for all participants except sender
            // Note: In a batch, we can't easily fetch participants first. 
            // So we rely on the client-side sendNotification loop below to handle group members.
            // But we can try to update unreadCounts if we had the list. 
            // Since we don't have the list here without a fetch, we'll ensure the unreadCounts map 
            // is updated in the commit success listener for groups if needed, 
            // or just rely on the existing logic which is better suited for a Cloud Function.
        }
        batch.set(messageRef, messageData)

        batch.commit()
            .addOnSuccessListener { 
                onComplete(true) 
                // Send internal notification and update unread counts
                if (isGroup) {
                    db.collection("chats").document(chatId).get().addOnSuccessListener { roomDoc ->
                        val participants = roomDoc.get("participants") as? List<String> ?: emptyList()
                        val groupName = roomDoc.getString("groupName") ?: "Group"
                        
                        val unreadUpdates = mutableMapOf<String, Any>()
                        participants.forEach { pUid ->
                            if (pUid != currentUserId) {
                                // Trigger simulated push notification
                                NotificationRepository().sendNotification(
                                    receiverUid = pUid,
                                    type = "chat",
                                    title = "$groupName: $senderUsername",
                                    message = text,
                                    targetId = chatId
                                )
                                // Increment unread count in Firestore
                                unreadUpdates["unreadCounts.$pUid"] = com.google.firebase.firestore.FieldValue.increment(1)
                            }
                        }
                        if (unreadUpdates.isNotEmpty()) {
                            db.collection("chats").document(chatId).update(unreadUpdates)
                        }
                    }
                } else {
                    NotificationRepository().sendNotification(
                        receiverUid = finalReceiverId,
                        type = "chat",
                        title = "New Message from $senderUsername",
                        message = text,
                        targetId = chatId
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatRepo", "Batch commit failed: ${e.message}")
                onComplete(false)
            }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun sendImageMessage(chatId: String, imageUrl: String, senderUsername: String, isGroup: Boolean, receiverId: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatRoomRef = db.collection("chats").document(chatId)
        val messageRef = chatRoomRef.collection("messages").document()

        val messageData = hashMapOf(
            "messageId" to messageRef.id,
            "senderId" to currentUserId,
            "senderUsername" to senderUsername,
            "receiverId" to receiverId,
            "messageText" to "📷 Image",
            "type" to "image",
            "mediaUrl" to imageUrl,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "status" to "sent"
        )

        val batch = db.batch()
        batch.set(chatRoomRef, mapOf(
            "lastMessage" to "📷 Image",
            "lastTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        ), SetOptions.merge())
        batch.set(messageRef, messageData)
        batch.commit().addOnCompleteListener { 
            onComplete(it.isSuccessful)
            if (it.isSuccessful) {
                NotificationRepository().sendNotification(receiverId, "chat", "New Image from $senderUsername", "📷 Photo", chatId)
            }
        }
    }

    fun listenForMessages(chatId: String, onMessagesUpdate: (List<ChatMessage>) -> Unit): ListenerRegistration {
        return db.collection("chats").document(chatId).collection("messages")
            .limit(100)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                val messages = snapshots?.mapNotNull { it.toObject(ChatMessage::class.java) } ?: emptyList()
                val sortedMessages = messages.sortedBy { it.timestampDate?.time ?: System.currentTimeMillis() }
                onMessagesUpdate(sortedMessages)
            }
    }

    fun getRecentChats(onChatsUpdate: (List<ChatRoom>) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        val currentUserId = auth.currentUser?.uid ?: return null
        return db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("ChatRepo", "Listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                val chats = snapshots?.mapNotNull { it.toObject(ChatRoom::class.java) } ?: emptyList()
                // Sort in memory to avoid indexing requirement for now
                val sortedChats = chats.sortedByDescending { it.timestampDate?.
                time ?: 0L }
                onChatsUpdate(sortedChats)
            }
    }

    fun markChatAsRead(chatId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val roomRef = db.collection("chats").document(chatId)
        
        // Use set with merge to handle cases where the room document might not exist yet (e.g. newly joined focus room)
        roomRef.set(mapOf("unreadCounts" to mapOf(currentUserId to 0)), SetOptions.merge())
            .addOnFailureListener { Log.e("ChatRepo", "Mark as read failed: ${it.message}") }
            
        // Mark all received messages in this chat as 'seen'
        db.collection("chats").document(chatId).collection("messages")
            .whereEqualTo("receiverId", currentUserId)
            .get()
            .addOnSuccessListener { snapshots ->
                val batch = db.batch()
                var hasUpdates = false
                snapshots.documents.forEach { doc ->
                    val currentStatus = doc.getString("status")
                    if (currentStatus != "seen") {
                        batch.update(doc.reference, "status", "seen")
                        hasUpdates = true
                    }
                }
                if (hasUpdates) {
                    batch.commit().addOnSuccessListener {
                        Log.d("ChatRepo", "Messages marked as seen successfully")
                    }
                }
            }
    }

    fun getChatId(id1: String, id2: String): String {
        return if (id1 < id2) "${id1}_${id2}" else "${id2}_${id1}"
    }

    fun forwardMessage(chatId: String, text: String, senderUsername: String, isGroup: Boolean, receiverId: String?, onComplete: (Boolean) -> Unit) {
        sendMessage(chatId, text, senderUsername, isGroup, receiverId, null, null, onComplete)
    }

    fun editMessage(chatId: String, messageId: String, newText: String, isLastMessage: Boolean = false, onComplete: (Boolean) -> Unit) {
        val batch = db.batch()
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        
        batch.update(msgRef, mapOf(
            "messageText" to newText,
            "isEdited" to true
        ))

        if (isLastMessage) {
            val roomRef = db.collection("chats").document(chatId)
            batch.update(roomRef, "lastMessage", newText)
        }

        batch.commit().addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun deleteMessage(chatId: String, messageId: String, isLastMessage: Boolean = false, onComplete: (Boolean) -> Unit) {
        val batch = db.batch()
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        
        batch.delete(msgRef)

        if (isLastMessage) {
            val roomRef = db.collection("chats").document(chatId)
            batch.update(roomRef, "lastMessage", "🚫 Message deleted")
        }

        batch.commit().addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun toggleReaction(chatId: String, messageId: String, emoji: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        
        // In a real app, you'd check if user already reacted with this emoji to remove it
        // For simplicity, we'll just add it here
        msgRef.update("reactions.$emoji", FieldValue.arrayUnion(currentUserId))
    }

    fun updateDuelStatus(chatId: String, messageId: String, status: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update("duelStatus", status)
    }

    fun setTypingStatus(chatId: String, isTyping: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("chats").document(chatId)
            .update("typingStatus.$currentUserId", isTyping)
    }

    fun observeChatRoom(chatId: String, onUpdate: (ChatRoom?) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("chats").document(chatId).addSnapshotListener { snapshot, _ ->
            onUpdate(snapshot?.toObject(ChatRoom::class.java))
        }
    }

    fun createGroup(name: String, description: String, memberIds: List<String>, onComplete: (Boolean, String?) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val groupId = db.collection("chats").document().id
        val allMembers = memberIds.toMutableList().apply { if (!contains(currentUserId)) add(currentUserId) }

        val chatRoomData = hashMapOf(
            "chatId" to groupId,
            "participants" to allMembers,
            "lastMessage" to "Group created",
            "isGroup" to true,
            "groupName" to name,
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "unreadCounts" to allMembers.associateWith { 0 }
        )

        db.collection("chats").document(groupId).set(chatRoomData)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful, if (task.isSuccessful) groupId else null)
            }
    }
}
