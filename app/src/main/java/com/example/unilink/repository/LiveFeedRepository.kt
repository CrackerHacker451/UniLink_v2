package com.example.unilink.repository

import com.example.unilink.models.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LiveFeedRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun observeCampusPulse(tagFilter: String? = null, onUpdate: (List<Post>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("posts").addSnapshotListener { snapshot, e ->
            val firestorePosts = if (e == null && snapshot != null && !snapshot.isEmpty) {
                snapshot.mapNotNull { doc ->
                    try {
                        val p = doc.toObject(Post::class.java)
                        if (p != null && p.postId.isNotEmpty()) p else {
                            Post(
                                postId = doc.id,
                                authorId = doc.getString("authorId") ?: "system",
                                authorName = doc.getString("authorName") ?: "Campus Live+ 📡",
                                authorAvatarUrl = doc.getString("authorAvatarUrl"),
                                authorMajor = doc.getString("authorMajor") ?: "Campus",
                                content = doc.getString("content") ?: doc.getString("message") ?: "Campus update",
                                imageUrl = doc.getString("imageUrl"),
                                postType = doc.getString("postType") ?: "post",
                                tag = doc.getString("tag") ?: "#General",
                                timestamp = doc.get("timestamp"),
                                likes = (doc.getLong("likes") ?: 0L).toInt(),
                                commentCount = (doc.getLong("commentCount") ?: 0L).toInt()
                            )
                        }
                    } catch (_: Exception) {
                        try {
                            Post(
                                postId = doc.id,
                                authorId = doc.getString("authorId") ?: "system",
                                authorName = doc.getString("authorName") ?: "Campus Live+ 📡",
                                authorAvatarUrl = doc.getString("authorAvatarUrl"),
                                authorMajor = doc.getString("authorMajor") ?: "Campus",
                                content = doc.getString("content") ?: doc.getString("message") ?: "Campus update",
                                imageUrl = doc.getString("imageUrl"),
                                postType = doc.getString("postType") ?: "post",
                                tag = doc.getString("tag") ?: "#General",
                                timestamp = doc.get("timestamp"),
                                likes = (doc.getLong("likes") ?: 0L).toInt(),
                                commentCount = (doc.getLong("commentCount") ?: 0L).toInt()
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            } else {
                emptyList()
            }

            var posts = if (firestorePosts.isNotEmpty()) firestorePosts else getSampleCampusPulsePosts()

            if (!tagFilter.isNullOrEmpty() && tagFilter != "All") {
                posts = posts.filter { it.tag.contains(tagFilter, ignoreCase = true) }
            }

            val sortedPosts = posts.sortedByDescending {
                it.timestampDate?.time ?: System.currentTimeMillis()
            }

            onUpdate(sortedPosts)
        }
    }

    private fun getSampleCampusPulsePosts(): List<Post> {
        return listOf(
            Post(
                postId = "sample_p1",
                authorId = "system",
                authorName = "Campus Live Stage 📡",
                authorMajor = "Official Update",
                content = "🔴 Newton's Lobby Hackathon 2026 is LIVE! World ID 4.0, Chainlink CRE & NewtonAI features are now active for all campus builders. 🚀",
                postType = "system_update",
                tag = "#Hackathon2026",
                likes = 42,
                commentCount = 8
            ),
            Post(
                postId = "sample_p2",
                authorId = "system",
                authorName = "NewtonAI Assistant 🤖",
                authorMajor = "AI Companion",
                content = "✨ Need a project idea, code audit, or study focus room? Command NewtonAI on the Home screen or tap any quick action chip!",
                postType = "twite",
                tag = "#NewtonAI",
                likes = 35,
                commentCount = 5
            ),
            Post(
                postId = "sample_p3",
                authorId = "system",
                authorName = "Doubt Board Drop 🧠",
                authorMajor = "Academic Q&A",
                content = "New academic doubt posted: \"How to optimize PageRank algorithm in Kotlin?\" (Reward: 25 UniCoins) 💰",
                postType = "doubt",
                tag = "#DoubtBoard",
                likes = 18,
                commentCount = 3
            )
        )
    }

    fun publishTwite(content: String, tag: String = "#General", onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val pulseId = db.collection("posts").document().id

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "Student"
            val avatar = doc.getString("profileImageUrl")
            val major = doc.getString("branch") ?: "Campus"

            val pulseData = hashMapOf(
                "postId" to pulseId,
                "authorId" to uid,
                "authorName" to name,
                "authorAvatarUrl" to avatar,
                "authorMajor" to major,
                "content" to content,
                "postType" to "twite",
                "tag" to tag,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "likes" to 0,
                "commentCount" to 0,
                "likedBy" to emptyList<String>(),
                "reactions" to emptyMap<String, List<String>>()
            )

            db.collection("posts").document(pulseId).set(pulseData).addOnSuccessListener {
                db.collection("users").document(uid).update(
                    "uniCoins", com.google.firebase.firestore.FieldValue.increment(5),
                    "totalXp", com.google.firebase.firestore.FieldValue.increment(10)
                )
                onComplete(true)
            }.addOnFailureListener {
                onComplete(false)
            }
        }
    }

    fun publishPulse(content: String, type: String = "info") {
        val pulseId = db.collection("posts").document().id
        val pulseData = hashMapOf(
            "postId" to pulseId,
            "authorId" to "system",
            "authorName" to "Campus Live+ 📡",
            "content" to content,
            "postType" to "system_update",
            "tag" to "#Official",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("posts").document(pulseId).set(pulseData)
    }
}
