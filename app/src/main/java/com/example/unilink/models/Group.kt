package com.example.unilink.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Group(
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val creatorId: String = "",
    val members: List<String> = emptyList(),
    val imageUrl: String = "",
    @ServerTimestamp val createdAt: Date? = null
)
