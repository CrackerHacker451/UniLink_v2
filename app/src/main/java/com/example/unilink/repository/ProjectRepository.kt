package com.example.unilink.repository

import com.example.unilink.models.Project
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ProjectRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun createProject(
        title: String,
        description: String,
        skills: List<String>,
        tags: List<String> = emptyList(),
        maxTeamSize: Int = 4,
        onComplete: (Boolean) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val projectId = db.collection("projects").document().id

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: doc.getString("displayName") ?: "User"
            val avatar = doc.getString("profileImageUrl") ?: ""

            val project = Project(
                projectId = projectId,
                title = title,
                description = description,
                ownerId = uid,
                ownerName = name,
                skillsRequired = skills,
                tags = tags,
                rawMembers = listOf(uid),
                maxTeamSize = maxTeamSize,
                status = "Open",
                chatGroupId = projectId,
                source = "unilink_app"
            )

            db.collection("projects").document(projectId).set(project, SetOptions.merge())
                .addOnSuccessListener {
                    onComplete(true)
                    LiveFeedRepository().publishPulse("🚀 New Project: '$title' is looking for teammates! ✨")

                    // Optional background sync to web collections
                    try {
                        val webMember = mapOf("uid" to uid, "name" to name, "role" to "Owner", "avatar" to avatar)
                        val webProjectData = hashMapOf(
                            "name" to title,
                            "description" to description,
                            "ownerId" to uid,
                            "ownerName" to name,
                            "skills" to skills,
                            "members" to listOf(webMember),
                            "maxMembers" to maxTeamSize,
                            "status" to "open",
                            "source" to "unilink_app",
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        db.collection("hackathon_teams").document(projectId).set(webProjectData, SetOptions.merge())
                        db.collection("hl_teams").document(projectId).set(webProjectData, SetOptions.merge())
                    } catch (_: Exception) {}
                }
                .addOnFailureListener {
                    onComplete(false)
                }
        }.addOnFailureListener {
            val project = Project(
                projectId = projectId,
                title = title,
                description = description,
                ownerId = uid,
                ownerName = "User",
                skillsRequired = skills,
                tags = tags,
                rawMembers = listOf(uid),
                maxTeamSize = maxTeamSize,
                status = "Open",
                chatGroupId = projectId,
                source = "unilink_app"
            )

            db.collection("projects").document(projectId).set(project, SetOptions.merge())
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }
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

            try {
                transaction.update(db.collection("hackathon_teams").document(projectId), "boostUntil", calendar.time)
                transaction.update(db.collection("hl_teams").document(projectId), "boostUntil", calendar.time)
            } catch (_: Exception) {}
        }.addOnSuccessListener {
            onComplete(true)
            LiveFeedRepository().publishPulse("🚀 A new project has been BOOSTED! Check it out in the Hub.")
        }.addOnFailureListener { onComplete(false) }
    }

    fun getProjectById(projectId: String, onResult: (Project?) -> Unit) {
        db.collection("projects").document(projectId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    onResult(snapshot.toObject(Project::class.java))
                } else {
                    db.collection("hackathon_teams").document(projectId).get()
                        .addOnSuccessListener { webSnap ->
                            val p = webSnap.toObject(Project::class.java)
                            if (p != null) {
                                p.projectId = webSnap.id
                                if (p.title.isNullOrEmpty()) p.title = webSnap.getString("name") ?: ""
                                onResult(p)
                            } else {
                                onResult(null)
                            }
                        }
                        .addOnFailureListener { onResult(null) }
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    fun getAllProjects(onResult: (List<Project>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        var appProjects = listOf<Project>()
        var webProjects = listOf<Project>()

        val updateResult = {
            val merged = (appProjects + webProjects).distinctBy { it.projectId }.sortedWith(compareByDescending<Project> {
                it.boostUntil?.after(java.util.Date()) ?: false
            }.thenByDescending { it.createdAt?.time ?: 0L })

            val finalProjects = if (merged.isNotEmpty()) merged else getSampleProjects()
            onResult(finalProjects)
        }

        val appListener = db.collection("projects").addSnapshotListener { snapshot, _ ->
            appProjects = snapshot?.mapNotNull { it.toObject(Project::class.java) } ?: emptyList()
            updateResult()
        }

        val webListener = db.collection("hackathon_teams").addSnapshotListener { snapshot, _ ->
            webProjects = snapshot?.mapNotNull { doc ->
                val p = doc.toObject(Project::class.java)
                p?.apply {
                    this.projectId = doc.id
                    if (this.title.isNullOrEmpty()) this.title = doc.getString("name") ?: ""
                }
            } ?: emptyList()
            updateResult()
        }

        return object : com.google.firebase.firestore.ListenerRegistration {
            override fun remove() {
                appListener.remove()
                webListener.remove()
            }
        }
    }

    private fun getSampleProjects(): List<Project> {
        return listOf(
            Project(
                projectId = "sample_p1",
                title = "Aura Glass - AR Campus Navigation",
                description = "Building an augmented reality app for university campus navigation and spatial mapping.",
                ownerId = "owner_1",
                ownerName = "Aarav Sharma",
                skillsRequired = listOf("Kotlin", "ARCore", "Figma"),
                tags = listOf("AI", "AR", "Hackathon"),
                status = "Open",
                progress = 25,
                maxTeamSize = 4,
                source = "unilink_app"
            ),
            Project(
                projectId = "sample_p2",
                title = "StudyVerse - AI Exam Tutor",
                description = "An AI agent generating practice quizzes from PDF lecture slides and tracking weak concepts.",
                ownerId = "owner_2",
                ownerName = "Priya Nair",
                skillsRequired = listOf("Python", "FastAPI", "React", "Gemini API"),
                tags = listOf("AI", "Dev", "Study"),
                status = "Open",
                progress = 40,
                maxTeamSize = 4,
                source = "unilink_app"
            ),
            Project(
                projectId = "sample_p3",
                title = "EcoTrack - Campus Energy Sentinel",
                description = "IoT sensor network tracking solar energy generation and power consumption across campus.",
                ownerId = "owner_3",
                ownerName = "Rohan Verma",
                skillsRequired = listOf("Embedded C", "Node.js", "Docker"),
                tags = listOf("Research", "Dev"),
                status = "Open",
                progress = 10,
                maxTeamSize = 3,
                source = "unilink_app"
            )
        )
    }

    fun joinProject(projectId: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)

        val projectRef = db.collection("projects").document(projectId)
        projectRef.update("members", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun leaveProject(projectId: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)

        val projectRef = db.collection("projects").document(projectId)
        projectRef.update("members", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun completeProject(projectId: String, onComplete: (Boolean) -> Unit) {
        val projectRef = db.collection("projects").document(projectId)

        projectRef.update(
            mapOf(
                "status" to "Completed",
                "progress" to 100
            )
        ).addOnSuccessListener { onComplete(true) }
         .addOnFailureListener { onComplete(false) }
    }
}
