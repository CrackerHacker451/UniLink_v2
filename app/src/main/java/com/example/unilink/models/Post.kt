package com.example.unilink.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class Post(
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorMajor: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Any? = null,
    val likes: Int = 0,
    val commentCount: Int = 0,
    val likedBy: List<String> = emptyList(),
    val reactions: Map<String, List<String>> = emptyMap() // emoji -> list of uids
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
