package com.example.unilink.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Project(
    var projectId: String = "",
    var title: String? = "",
    var description: String? = "",
    var ownerId: String? = "",
    var ownerName: String? = "",
    var skillsRequired: List<String>? = emptyList(),
    
    @get:PropertyName("members") @set:PropertyName("members")
    var rawMembers: Any? = null,
    
    var maxTeamSize: Int = 4,
    @get:PropertyName("maxMembers") @set:PropertyName("maxMembers")
    var webMaxMembers: Int? = null,
    
    var progress: Int = 0, // 0-100
    var tags: List<String>? = emptyList(),
    var status: String? = "Open", // Open, In Progress, Completed
    var chatGroupId: String? = null,
    var boostUntil: Date? = null,
    @ServerTimestamp var createdAt: Date? = null,

    // Newton's Lobby Compatibility Fields
    var source: String? = "unilink_app",
    var hackathonName: String? = null,
    var lookingFor: String? = null,
    var githubUrl: String? = null,
    var discordUrl: String? = null,
    var announcement: String? = null,
    var hackathonDeadline: Date? = null,
    
    // For handling different field names from website / Firestore
    @get:PropertyName("displayTitle") @set:PropertyName("displayTitle")
    var storedDisplayTitle: String? = null,

    var skills: List<String>? = emptyList(),

    @get:PropertyName("name") @set:PropertyName("name")
    var webName: String? = null
) {
    @get:Exclude
    val members: List<String>
        get() = when (val data = rawMembers) {
            is List<*> -> {
                data.mapNotNull { 
                    when (it) {
                        is String -> it
                        is Map<*, *> -> it["uid"] as? String
                        else -> null
                    }
                }
            }
            else -> emptyList()
        }

    // Helper to get skills falling back to skills property
    @get:Exclude
    val effectiveSkills: List<String>
        get() = if (!skillsRequired.isNullOrEmpty()) skillsRequired!! else skills ?: emptyList()

    // Helper to get title regardless of source
    @get:Exclude
    val displayTitle: String
        get() = if (!storedDisplayTitle.isNullOrEmpty()) {
            storedDisplayTitle!!
        } else if (source == "newtonslobby") {
            webName ?: title?.ifEmpty { "Untitled Web Project" } ?: "Untitled Web Project"
        } else {
            title?.ifEmpty { "Untitled Project" } ?: "Untitled Project"
        }

    @get:Exclude
    val effectiveMaxMembers: Int
        get() = webMaxMembers ?: maxTeamSize
}
