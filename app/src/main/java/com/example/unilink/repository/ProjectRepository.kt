package com.example.unilink.repository

import com.example.unilink.models.Project
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ProjectRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun createProject(title: String, description: String, skills: List<String>, tags: List<String> = emptyList(), onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val projectId = db.collection("projects").document().id
        
        // Create the group chat first
        val groupId = db.collection("chats").document().id
        val chatRoomData = hashMapOf(
            "chatId" to groupId,
            "participants" to listOf(uid),
            "lastMessage" to "Project Team Chat Created",
            "isGroup" to true,
            "groupName" to title,
            "lastTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "unreadCounts" to mapOf(uid to 0)
        )

        db.collection("chats").document(groupId).set(chatRoomData).addOnSuccessListener {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "User"
                
                val project = Project(
                    projectId = projectId,
                    title = title,
                    description = description,
                    ownerId = uid,
                    ownerName = name,
                    skillsRequired = skills,
                    tags = tags,
                    members = listOf(uid),
                    status = "Open",
                    chatGroupId = groupId
                )

                db.collection("projects").document(projectId).set(project)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Notify Campus Pulse
                            LiveFeedRepository().publishPulse("🚀 New Project: '$title' is looking for teammates! ✨")
                        }
                        onComplete(task.isSuccessful)
                    }
            }.addOnFailureListener { onComplete(false) }
        }.addOnFailureListener { onComplete(false) }
    }

    fun getProjectById(projectId: String, onResult: (Project?) -> Unit) {
        db.collection("projects").document(projectId).get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.toObject(Project::class.java))
            }
            .addOnFailureListener { onResult(null) }
    }

    fun boostProject(projectId: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val projectRef = db.collection("projects").document(projectId)
        val userRef = db.collection("users").document(uid)

        db.runTransaction { transaction ->
            val userSnap = transaction.get(userRef)
            val coins = userSnap.getLong("uniCoins") ?: 0
            if (coins < 50) throw Exception("Insufficient coins")

            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.HOUR, 24)

            transaction.update(userRef, "uniCoins", com.google.firebase.firestore.FieldValue.increment(-50))
            transaction.update(projectRef, "boostUntil", calendar.time)
        }.addOnSuccessListener { 
            onComplete(true) 
            LiveFeedRepository().publishPulse("🚀 A new project has been BOOSTED! Check it out in the Hub.")
        }.addOnFailureListener { onComplete(false) }
    }

    fun getAllProjects(onResult: (List<Project>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("projects")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onResult(emptyList())
                    return@addSnapshotListener
                }
                val projects = snapshot?.mapNotNull { it.toObject(Project::class.java) } ?: emptyList()
                // Advanced sorting: Boosted first, then by date
                val now = java.util.Date()
                val sortedProjects = projects.sortedWith(compareByDescending<Project> { 
                    it.boostUntil?.after(now) ?: false 
                }.thenByDescending { it.createdAt?.time ?: 0L })

                onResult(sortedProjects)
            }
    }

    fun joinProject(projectId: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val projectRef = db.collection("projects").document(projectId)
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(projectRef)
            val project = snapshot.toObject(Project::class.java) ?: return@runTransaction
            
            if (project.members.contains(uid)) return@runTransaction
            
            // Add to project members
            transaction.update(projectRef, "members", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
            
            // Add to chat participants
            project.chatGroupId?.let { groupId ->
                val chatRef = db.collection("chats").document(groupId)
                transaction.update(chatRef, "participants", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                transaction.update(chatRef, "unreadCounts.$uid", 0)
            }

            // Notify owner
            NotificationRepository().sendNotification(
                receiverUid = project.ownerId,
                type = "project_join",
                title = "New Team Member! 🚀",
                message = "Someone joined your project: ${project.title}",
                targetId = projectId
            )
        }.addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun completeProject(projectId: String, onComplete: (Boolean) -> Unit) {
        val projectRef = db.collection("projects").document(projectId)
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(projectRef)
            val project = snapshot.toObject(Project::class.java) ?: return@runTransaction
            
            transaction.update(projectRef, "status", "Completed")
            transaction.update(projectRef, "progress", 100)
            
            project.members.forEach { memberId ->
                val memberRef = db.collection("users").document(memberId)
                transaction.update(memberRef, "totalXp", com.google.firebase.firestore.FieldValue.increment(50))
                
                NotificationRepository().sendNotification(
                    receiverUid = memberId,
                    type = "project_complete",
                    title = "Project Completed! 🎓",
                    message = "Congratulations! You earned 50 XP for completing ${project.title}",
                    targetId = projectId
                )
            }
        }.addOnCompleteListener { onComplete(it.isSuccessful) }
    }
}
