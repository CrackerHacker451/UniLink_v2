package com.example.unilink.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class Doubt(
    var doubtId: String = "",
    var userId: String = "",
    var userName: String = "",
    var userAvatarUrl: String? = null,
    var question: String = "",
    var rewardCoins: Int = 0,
    var tags: List<String> = emptyList(),
    var answerCount: Int = 0,
    var accentColor: String = "#22D3EE",
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    var aiResponse: String? = null,
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
