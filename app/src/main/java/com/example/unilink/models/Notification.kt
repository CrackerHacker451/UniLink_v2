package com.example.unilink.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Notification(
    var notificationId: String = "",
    var type: String = "info", // vouch, project_join, system, mention
    var title: String = "",
    var message: String = "",
    var senderId: String? = null,
    var senderName: String? = null,
    var targetId: String? = null, // e.g. projectId or chatGroupId
    var isRead: Boolean = false,
    @ServerTimestamp var timestamp: Date? = null
)
