package com.example.unilink.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Project(
    var projectId: String = "",
    var title: String = "",
    var description: String = "",
    var ownerId: String = "",
    var ownerName: String = "",
    var skillsRequired: List<String> = emptyList(),
    var members: List<String> = emptyList(),
    var maxTeamSize: Int = 4,
    var progress: Int = 0, // 0-100
    var tags: List<String> = emptyList(),
    var status: String = "Open", // Open, In Progress, Completed
    var chatGroupId: String? = null,
    var boostUntil: Date? = null,
    @ServerTimestamp var createdAt: Date? = null
)
