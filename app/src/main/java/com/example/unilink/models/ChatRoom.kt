package com.example.unilink.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class ChatRoom(
    var chatId: String = "",
    var participants: List<String> = emptyList(),
    var lastMessage: String = "",
    var lastMessageSenderId: String = "",
    @get:PropertyName("lastTimestamp") @set:PropertyName("lastTimestamp")
    var lastTimestamp: Any? = null,
    
    var isGroup: Boolean = false,
    
    var groupName: String = "",
    var groupImageUrl: String = "",
    var unreadCounts: Map<String, Int> = emptyMap(), // Track unread messages per user
    var typingStatus: Map<String, Boolean> = emptyMap() // userId -> isTyping
) {
    @get:Exclude
    val timestampDate: Date?
        get() = when (lastTimestamp) {
            is Timestamp -> (lastTimestamp as Timestamp).toDate()
            is Date -> lastTimestamp as Date
            is Long -> Date(lastTimestamp as Long)
            else -> null
        }
}
