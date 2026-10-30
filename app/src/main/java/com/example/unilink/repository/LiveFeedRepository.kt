package com.example.unilink.repository

import com.example.unilink.models.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LiveFeedRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun observeCampusPulse(tagFilter: String? = null, onUpdate: (List<Post>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("posts").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || snapshot.isEmpty) {
                onUpdate(emptyList())
                return@addSnapshotListener
            }

            var posts = snapshot.mapNotNull { doc ->
                try {
                    doc.toObject(Post::class.java)
                } catch (_: Exception) {
                    null
                }
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
