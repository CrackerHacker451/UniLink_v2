package com.example.unilink.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Notification(
    var notificationId: String = "",
    var type: String = "info",
    var title: String = "",
    var message: String = "",
    var senderId: String? = null,
    var senderName: String? = null,
    var targetId: String? = null,

    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isReadField: Boolean = false,

    @get:PropertyName("read") @set:PropertyName("read")
    var hasReadField: Boolean = false,

    @ServerTimestamp var timestamp: Date? = null
) {
    @get:Exclude
    val isRead: Boolean
        get() = isReadField || hasReadField
}
