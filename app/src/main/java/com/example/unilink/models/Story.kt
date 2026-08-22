package com.example.unilink.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class Story(
    var storyId: String = "",
    var userId: String = "",
    var userName: String = "",
    var userAvatarUrl: String? = null,
    var imageUrl: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Any? = null
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
