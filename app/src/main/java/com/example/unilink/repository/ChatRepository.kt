package com.example.unilink.repository

import android.util.Log
import com.example.unilink.models.ChatMessage
import com.example.unilink.models.ChatRoom
import com.example.unilink.utils.EncryptionUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
        isProjectSync: Boolean = false,
        onComplete: (Boolean) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid ?: run {
            Log.e("ChatRepo", "Send failed: User not logged in")
            onComplete(false)
            return
        }

        if (isProjectSync) {
            val messageData = hashMapOf(
                "text" to text,
                "messageText" to text,
                "senderId" to currentUserId,
                "senderName" to senderUsername,
                "senderUsername" to senderUsername,
                "timestamp" to FieldValue.serverTimestamp()
            )
            val batch = db.batch()
            val msgRef = db.collection("projects").document(chatId).collection("messages").document()
            batch.set(msgRef, messageData)
            batch.set(db.collection("hl_teams").document(chatId).collection("messages").document(msgRef.id), messageData)
            batch.commit().addOnCompleteListener { onComplete(it.isSuccessful) }
            return
        }

        val finalReceiverId = if (!isGroup) {
            val rid = receiverId ?: ""
            if (rid.isNotEmpty()) rid
            else chatId.split("_").firstOrNull { it != currentUserId && it.isNotEmpty() } ?: ""
        } else ""

        if (!isGroup && finalReceiverId.isEmpty()) {
            Log.e("ChatRepo", "Send failed: Could not determine receiverId for chatId $chatId")
            onComplete(false)
            return
        }

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
                    proceedWithSendMessage(chatId, text, senderUsername, isGroup, finalReceiverId, replyToId, replyToText, onComplete)
                }
        } else {
            proceedWithSendMessage(chatId, text, senderUsername, isGroup, "", replyToId, replyToText, onComplete)
        }
    }

    fun sendDoubtMessage(chatId: String, question: String, rewardCoins: Int, senderUsername: String, isGroup: Boolean, receiverId: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val receiverUid = if (!isGroup) {
            if (receiverId.isNotEmpty()) receiverId
            else chatId.split("_").firstOrNull { it != currentUserId && it.isNotEmpty() } ?: ""
        } else ""

        val chatRoomRef = db.collection("chats").document(chatId)
        val messageRef = chatRoomRef.collection("messages").document()

        val doubtData = hashMapOf(
            "messageId" to messageRef.id,
            "senderId" to currentUserId,
            "senderUsername" to senderUsername,
            "receiverId" to receiverUid,
            "messageText" to "🧠 Doubt: $question",
            "type" to "doubt",
            "doubtReward" to rewardCoins,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "sent"
        )

        val chatRoomUpdate = hashMapOf<String, Any>(
            "chatId" to chatId,
            "lastMessage" to "🧠 Doubt: $question",
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "isGroup" to isGroup
        )

        if (!isGroup && receiverUid.isNotEmpty()) {
            chatRoomUpdate["participants"] = listOf(currentUserId, receiverUid)
        }

        val batch = db.batch()
        batch.set(chatRoomRef, chatRoomUpdate, SetOptions.merge())
        batch.set(messageRef, doubtData)

        batch.commit().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                GlobalScope.launch(Dispatchers.IO) {
                    val solution = GeminiRepository().getAiHint(question, true)
                    if (solution.isNotEmpty()) {
                        val aiMsgRef = chatRoomRef.collection("messages").document()
                        val aiMsgData = hashMapOf(
                            "messageId" to aiMsgRef.id,
                            "senderId" to "uniai_genius",
                            "senderUsername" to "✨ UniAI Genius Assistant",
                            "receiverId" to currentUserId,
                            "messageText" to solution,
                            "type" to "text",
                            "timestamp" to FieldValue.serverTimestamp(),
                            "status" to "seen"
                        )
                        chatRoomRef.collection("messages").document(aiMsgRef.id).set(aiMsgData)
                    }
                }
            }
            onComplete(task.isSuccessful)
        }
    }

    fun sendPollMessage(chatId: String, question: String, options: Map<String, Int>, senderUsername: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatRoomRef = db.collection("chats").document(chatId)
        val messageRef = chatRoomRef.collection("messages").document()

        val pollData = hashMapOf(
            "messageId" to messageRef.id,
            "senderId" to currentUserId,
            "senderUsername" to senderUsername,
            "pollQuestion" to question,
            "pollOptions" to options,
            "userVotes" to mapOf<String, String>(),
            "type" to "poll",
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "sent"
        )

        val batch = db.batch()
        batch.set(chatRoomRef, mapOf(
            "chatId" to chatId,
            "lastMessage" to "📊 Poll: $question",
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "isGroup" to true
        ), SetOptions.merge())
        batch.set(messageRef, pollData)
        batch.commit().addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun sendVoiceMessage(chatId: String, receiverId: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val receiverUid = if (receiverId.isNotEmpty()) receiverId
                          else chatId.split("_").firstOrNull { it != currentUserId && it.isNotEmpty() } ?: ""

        val chatRoomRef = db.collection("chats").document(chatId)
        val messageRef = chatRoomRef.collection("messages").document()

        val voiceData = hashMapOf(
            "messageId" to messageRef.id,
            "senderId" to currentUserId,
            "receiverId" to receiverUid,
            "messageText" to "🎙️ Voice Message",
            "type" to "voice",
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "sent"
        )

        val chatRoomUpdate = hashMapOf<String, Any>(
            "chatId" to chatId,
            "lastMessage" to "🎙️ Voice Message",
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "isGroup" to false,
            "participants" to listOf(currentUserId, receiverUid)
        )

        val batch = db.batch()
        batch.set(chatRoomRef, chatRoomUpdate, SetOptions.merge())
        if (receiverUid.isNotEmpty()) {
            batch.set(chatRoomRef, mapOf("unreadCounts" to mapOf(receiverUid to FieldValue.increment(1))), SetOptions.merge())
        }
        batch.set(messageRef, voiceData)

        batch.commit().addOnCompleteListener { 
            onComplete(it.isSuccessful)
            if (it.isSuccessful && receiverUid.isNotEmpty()) {
                NotificationRepository().sendNotification(receiverUid, "chat", "New Voice Message", "🎙️ Listen now", chatId)
            }
        }
    }

    fun sendDuelRequest(chatId: String, receiverId: String, wager: Int, subject: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val receiverUid = if (receiverId.isNotEmpty()) receiverId
                          else chatId.split("_").firstOrNull { it != currentUserId && it.isNotEmpty() } ?: ""

        val chatRoomRef = db.collection("chats").document(chatId)
        val messageRef = chatRoomRef.collection("messages").document()

        val duelData = hashMapOf(
            "messageId" to messageRef.id,
            "senderId" to currentUserId,
            "receiverId" to receiverUid,
            "messageText" to "⚔️ Challenged you to a $subject Duel!",
            "type" to "duel",
            "duelWager" to wager,
            "duelSubject" to subject,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "sent"
        )

        val chatRoomUpdate = hashMapOf<String, Any>(
            "chatId" to chatId,
            "lastMessage" to "⚔️ Duel Challenge!",
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "isGroup" to false,
            "participants" to listOf(currentUserId, receiverUid)
        )

        val batch = db.batch()
        batch.set(chatRoomRef, chatRoomUpdate, SetOptions.merge())
        if (receiverUid.isNotEmpty()) {
            batch.set(chatRoomRef, mapOf("unreadCounts" to mapOf(receiverUid to FieldValue.increment(1))), SetOptions.merge())
        }
        batch.set(messageRef, duelData)

        batch.commit().addOnCompleteListener { 
            onComplete(it.isSuccessful)
            if (it.isSuccessful && receiverUid.isNotEmpty()) {
                NotificationRepository().sendNotification(
                    receiverUid = receiverUid,
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
        val receiverUid = if (receiverId.isNotEmpty()) receiverId
                          else chatId.split("_").firstOrNull { it != currentUserId && it.isNotEmpty() } ?: ""

        val senderRef = db.collection("users").document(currentUserId)
        val receiverRef = db.collection("users").document(receiverUid)
        val chatRoomRef = db.collection("chats").document(chatId)

        db.runTransaction { transaction ->
            val senderSnap = transaction.get(senderRef)
            val currentCoins = senderSnap.getLong("uniCoins") ?: 0L
            if (currentCoins < amount) {
                throw Exception("Insufficient UniCoins balance")
            }

            transaction.update(senderRef, "uniCoins", currentCoins - amount)
            transaction.update(receiverRef, "uniCoins", FieldValue.increment(amount.toLong()))

            val messageRef = chatRoomRef.collection("messages").document()
            val tokenData = hashMapOf(
                "messageId" to messageRef.id,
                "senderId" to currentUserId,
                "receiverId" to receiverUid,
                "messageText" to "💰 Sent $amount UniCoins: $note",
                "type" to "token",
                "uniCoinAmount" to amount,
                "tokenReason" to note,
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "sent"
            )
            transaction.set(messageRef, tokenData)

            val chatRoomUpdate = hashMapOf<String, Any>(
                "chatId" to chatId,
                "lastMessage" to "💰 Sent $amount UniCoins",
                "lastTimestamp" to FieldValue.serverTimestamp(),
                "isGroup" to false,
                "participants" to listOf(currentUserId, receiverUid)
            )
            transaction.set(chatRoomRef, chatRoomUpdate, SetOptions.merge())
            if (receiverUid.isNotEmpty()) {
                transaction.set(chatRoomRef, mapOf("unreadCounts" to mapOf(receiverUid to FieldValue.increment(1))), SetOptions.merge())
            }
        }.addOnSuccessListener {
            onComplete(true)
            if (receiverUid.isNotEmpty()) {
                NotificationRepository().sendNotification(
                    receiverUid = receiverUid,
                    type = "token",
                    title = "💰 UniCoins Received!",
                    message = "You received $amount UniCoins: $note",
                    targetId = chatId
                )
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
        replyToId: String?,
        replyToText: String?,
        onComplete: (Boolean) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid ?: return onComplete(false)
        val encryptedText = EncryptionUtils.encrypt(text, chatId)

        val receiverUid = if (!isGroup) {
            if (finalReceiverId.isNotEmpty()) finalReceiverId
            else chatId.split("_").firstOrNull { it != currentUserId && it.isNotEmpty() } ?: ""
        } else ""

        val chatRoomRef = db.collection("chats").document(chatId)
        val messageRef = chatRoomRef.collection("messages").document()

        val messageData = hashMapOf(
            "messageId" to messageRef.id,
            "senderId" to currentUserId,
            "senderUsername" to senderUsername,
            "receiverId" to receiverUid,
            "messageText" to encryptedText,
            "type" to "text",
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "sent",
            "replyToId" to replyToId,
            "replyToText" to replyToText
        )

        val chatRoomUpdate = hashMapOf<String, Any>(
            "chatId" to chatId,
            "lastMessage" to encryptedText,
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "isGroup" to isGroup
        )

        if (!isGroup && receiverUid.isNotEmpty()) {
            chatRoomUpdate["participants"] = listOf(currentUserId, receiverUid)
        }

        val batch = db.batch()
        batch.set(chatRoomRef, chatRoomUpdate, SetOptions.merge())

        if (!isGroup && receiverUid.isNotEmpty()) {
            batch.set(chatRoomRef, mapOf("unreadCounts" to mapOf(receiverUid to FieldValue.increment(1))), SetOptions.merge())
        }

        batch.set(messageRef, messageData)

        batch.commit().addOnSuccessListener { 
            onComplete(true) 
            if (isGroup) {
                db.collection("chats").document(chatId).get().addOnSuccessListener { roomDoc ->
                    val participants = roomDoc.get("participants") as? List<String> ?: emptyList()
                    val groupName = roomDoc.getString("groupName") ?: "Group"
                    val unreadUpdates = mutableMapOf<String, Any>()
                    participants.forEach { pUid ->
                        if (pUid != currentUserId) {
                            NotificationRepository().sendNotification(pUid, "chat", "$groupName: $senderUsername", text, chatId)
                            unreadUpdates["unreadCounts.$pUid"] = FieldValue.increment(1)
                        }
                    }
                    if (unreadUpdates.isNotEmpty()) db.collection("chats").document(chatId).update(unreadUpdates)
                }
            } else if (receiverUid.isNotEmpty()) {
                NotificationRepository().sendNotification(receiverUid, "chat", "New Message from $senderUsername", text, chatId)
            }
        }.addOnFailureListener { onComplete(false) }
    }

    fun sendImageMessage(chatId: String, imageUrl: String, senderUsername: String, isGroup: Boolean, receiverId: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val receiverUid = if (!isGroup) {
            if (receiverId.isNotEmpty()) receiverId
            else chatId.split("_").firstOrNull { it != currentUserId && it.isNotEmpty() } ?: ""
        } else ""

        val chatRoomRef = db.collection("chats").document(chatId)
        val messageRef = chatRoomRef.collection("messages").document()

        val messageData = hashMapOf(
            "messageId" to messageRef.id,
            "senderId" to currentUserId,
            "senderUsername" to senderUsername,
            "receiverId" to receiverUid,
            "messageText" to "📷 Image",
            "type" to "image",
            "mediaUrl" to imageUrl,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "sent"
        )

        val chatRoomUpdate = hashMapOf<String, Any>(
            "chatId" to chatId,
            "lastMessage" to "📷 Image",
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "isGroup" to isGroup
        )

        if (!isGroup && receiverUid.isNotEmpty()) {
            chatRoomUpdate["participants"] = listOf(currentUserId, receiverUid)
        }

        val batch = db.batch()
        batch.set(chatRoomRef, chatRoomUpdate, SetOptions.merge())
        if (!isGroup && receiverUid.isNotEmpty()) {
            batch.set(chatRoomRef, mapOf("unreadCounts" to mapOf(receiverUid to FieldValue.increment(1))), SetOptions.merge())
        }
        batch.set(messageRef, messageData)

        batch.commit().addOnCompleteListener { 
            onComplete(it.isSuccessful)
            if (it.isSuccessful && receiverUid.isNotEmpty()) {
                NotificationRepository().sendNotification(receiverUid, "chat", "New Image from $senderUsername", "📷 Photo", chatId)
            }
        }
    }

    fun listenForMessages(chatId: String, isProjectSync: Boolean = false, onMessagesUpdate: (List<ChatMessage>) -> Unit): ListenerRegistration {
        val ref = if (isProjectSync) db.collection("hl_teams").document(chatId).collection("messages")
                  else db.collection("chats").document(chatId).collection("messages")
        
        return ref.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("ChatRepo", "Error listening for messages", e)
                return@addSnapshotListener
            }
            val messages = snapshots?.mapNotNull { doc ->
                doc.toObject(ChatMessage::class.java).apply {
                    if (this.messageId.isEmpty()) {
                        this.messageId = doc.id
                    }
                }
            }?.sortedBy { msg ->
                msg.timestampDate?.time ?: Long.MAX_VALUE
            } ?: emptyList()

            onMessagesUpdate(messages)
        }
    }

    fun observeChatRoom(chatId: String, onUpdate: (ChatRoom?) -> Unit): ListenerRegistration {
        return db.collection("chats").document(chatId).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) {
                onUpdate(null)
                return@addSnapshotListener
            }
            onUpdate(snapshot.toObject(ChatRoom::class.java))
        }
    }

    fun editMessage(chatId: String, messageId: String, newText: String, isGroup: Boolean = false, onComplete: (Boolean) -> Unit) {
        val encryptedText = EncryptionUtils.encrypt(newText, chatId)
        val docRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        
        docRef.update(
            "messageText", encryptedText,
            "isEdited", true
        ).addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun toggleReaction(chatId: String, messageId: String, emoji: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val docRef = db.collection("chats").document(chatId).collection("messages").document(messageId)

        docRef.get().addOnSuccessListener { doc ->
            val reactions = (doc.get("reactions") as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { (it.value as? List<*>)?.mapNotNull { item -> item?.toString() } ?: emptyList() }?.toMutableMap() ?: mutableMapOf()
            val currentUsers = reactions[emoji]?.toMutableList() ?: mutableListOf()

            if (currentUsers.contains(currentUserId)) {
                currentUsers.remove(currentUserId)
            } else {
                currentUsers.add(currentUserId)
            }

            if (currentUsers.isEmpty()) {
                reactions.remove(emoji)
            } else {
                reactions[emoji] = currentUsers
            }

            docRef.update("reactions", reactions)
        }
    }

    fun updateDuelStatus(chatId: String, messageId: String, status: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update("duelStatus", status)
    }

    fun deleteMessage(chatId: String, messageId: String, isGroup: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .delete().addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun forwardMessage(targetChatId: String, text: String, senderUsername: String, isGroup: Boolean, receiverId: String? = null, onComplete: (Boolean) -> Unit = {}) {
        sendMessage(targetChatId, text, senderUsername, isGroup, receiverId, onComplete = onComplete)
    }

    fun getRecentChats(onChatsUpdate: (List<ChatRoom>) -> Unit): ListenerRegistration? {
        val currentUserId = auth.currentUser?.uid ?: return null
        return db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                val chats = snapshots?.mapNotNull { doc ->
                    doc.toObject(ChatRoom::class.java).apply {
                        if (this.chatId.isEmpty()) {
                            this.chatId = doc.id
                        }
                    }
                } ?: emptyList()
                onChatsUpdate(chats.sortedByDescending { it.timestampDate?.time ?: 0L })
            }
    }

    fun observeTotalUnreadChatCount(onUpdate: (Int) -> Unit): ListenerRegistration? {
        val currentUserId = auth.currentUser?.uid ?: return null
        return db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(0)
                    return@addSnapshotListener
                }

                var totalUnread = 0
                snapshot.documents.forEach { doc ->
                    val unreadMap = doc.get("unreadCounts") as? Map<*, *>
                    val count = (unreadMap?.get(currentUserId) as? Long ?: 0L).toInt()
                    totalUnread += count
                }

                onUpdate(totalUnread)
            }
    }

    fun markChatAsRead(chatId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("chats").document(chatId).set(mapOf("unreadCounts" to mapOf(currentUserId to 0)), SetOptions.merge())

        db.collection("chats").document(chatId).collection("messages")
            .whereEqualTo("status", "sent")
            .get()
            .addOnSuccessListener { snapshots ->
                if (snapshots == null || snapshots.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                snapshots.documents.forEach { doc ->
                    if (doc.getString("senderId") != currentUserId) {
                        batch.update(doc.reference, "status", "seen")
                    }
                }
                batch.commit()
            }
    }

    fun createGroup(groupName: String, memberIds: List<String>, onComplete: (String?) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return onComplete(null)
        val groupChatId = db.collection("chats").document().id
        val allParticipants = (memberIds + currentUserId).distinct()

        val groupData = hashMapOf(
            "chatId" to groupChatId,
            "groupName" to groupName,
            "isGroup" to true,
            "participants" to allParticipants,
            "lastMessage" to "Group '$groupName' created 🎉",
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "unreadCounts" to allParticipants.associateWith { 0 }
        )

        db.collection("chats").document(groupChatId).set(groupData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(groupChatId)
                } else {
                    onComplete(null)
                }
            }
    }

    fun setTypingStatus(chatId: String, isTyping: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        val docRef = db.collection("chats").document(chatId)
        docRef.set(mapOf("typingStatus" to mapOf(currentUserId to isTyping)), SetOptions.merge())
    }

    fun getChatId(id1: String, id2: String): String = if (id1 < id2) "${id1}_${id2}" else "${id2}_${id1}"
}
