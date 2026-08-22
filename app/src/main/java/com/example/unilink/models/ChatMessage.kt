package com.example.unilink.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class ChatMessage(
    var messageId: String = "",
    var senderId: String = "",
    var senderUsername: String = "",
    var senderAvatarUrl: String? = null,
    var receiverId: String = "",
    var messageText: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Any? = null,
    var status: String = "sent", // sent, delivered, seen
    var type: String = "text", // text, poll, doubt, token, code, voice, duel, image, file
    var mediaUrl: String? = null,
    var fileName: String? = null,
    var replyToId: String? = null,
    var replyToText: String? = null,
    var reactions: HashMap<String, List<String>> = hashMapOf(), // emoji -> list of userIds
    
    // Metadata for special cards
    var pollQuestion: String? = null,
    var pollOptions: HashMap<String, Int> = hashMapOf(), // optionText -> voteCount
    var userVotes: HashMap<String, String> = hashMapOf(), // userId -> optionText
    
    var doubtReward: Int? = null,
    var isDoubtResolved: Boolean = false,
    var isEdited: Boolean = false,
    
    var uniCoinAmount: Int? = null,
    var tokenReason: String? = null,
    
    var duelWager: Int? = null,
    var duelSubject: String? = null,
    var duelStatus: String? = null // pending, active, finished
) {
    @get:Exclude
    val timestampDate: Date?
        get() = when (timestamp) {
            is Timestamp -> (timestamp as Timestamp).toDate()
            is Date -> timestamp as Date
            is Long -> Date(timestamp as Long)
            else -> null
        }
}
