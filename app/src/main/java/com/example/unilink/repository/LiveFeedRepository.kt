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

            // Filter out system notifications & Campus Live+ broadcast alerts (only show real user posts)
            posts = posts.filter {
                it.authorId != "system" &&
                it.postType != "system_update" &&
                !it.authorName.contains("Campus Live", ignoreCase = true)
            }

            if (posts.isEmpty()) {
                posts = getSampleCampusPulsePosts()
            }

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
                authorId = "user_aarav_sharma",
                authorName = "Aarav Sharma",
                authorMajor = "Computer Science",
                content = "🚀 Just deployed our decentralized campus voting dApp with World ID 4.0 proof of human verification! Check it out in the Projects tab!",
                postType = "twite",
                tag = "#Hackathon2026",
                likes = 42,
                commentCount = 8
            ),
            Post(
                postId = "sample_p2",
                authorId = "user_sneha_reddy",
                authorName = "Sneha Reddy",
                authorMajor = "AI & Data Science",
                content = "✨ Anyone interested in pair programming for Chainlink CRE workflow integration? Building an automated campus grant payout system! 💡",
                postType = "twite",
                tag = "#Chainlink",
                likes = 35,
                commentCount = 5
            ),
            Post(
                postId = "sample_p3",
                authorId = "user_rohan_verma",
                authorName = "Rohan Verma",
                authorMajor = "Fullstack & Cloud",
                content = "📚 Shared comprehensive Kotlin Coroutines & Jetpack Compose notes in the Notes Vault! Feel free to download for midterms. 📖",
                postType = "twite",
                tag = "#NotesVault",
                likes = 28,
                commentCount = 4
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
