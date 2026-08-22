package com.example.unilink.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class VaultItem(
    var itemId: String = "",
    var title: String = "",
    var type: String = "Notes", // Notes, Paper, Project
    var description: String = "",
    var uploaderId: String = "",
    var uploaderName: String = "",
    var fileUrl: String = "",
    var price: Int = 0, // UniCoins
    var downloadCount: Int = 0,
    var tags: List<String> = emptyList(),
    @ServerTimestamp var createdAt: Date? = null
)
