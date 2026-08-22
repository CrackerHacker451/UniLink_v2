package com.example.unilink.repository

import com.example.unilink.models.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LiveFeedRepository {
    private val db = FirebaseFirestore.getInstance()

    fun observeCampusPulse(onUpdate: (List<Post>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("posts")
            .limit(20)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val posts = snapshot?.mapNotNull { it.toObject(Post::class.java) } ?: emptyList()
                val sortedPosts = posts.sortedByDescending { it.timestampDate?.time ?: 0L }
                onUpdate(sortedPosts)
            }
    }

    fun publishPulse(content: String, type: String = "info") {
        val pulseId = db.collection("posts").document().id
        val pulseData = hashMapOf(
            "postId" to pulseId,
            "authorId" to "system",
            "authorName" to "Campus Live+ 📡",
            "content" to content,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("posts").document(pulseId).set(pulseData)
    }
}
