package com.example.unilink.repository

import com.example.unilink.models.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PostRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun createPost(content: String, imageUrl: String? = null, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val postId = db.collection("posts").document().id
        
        // Fetch user name for the post
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "User"
            val major = doc.getString("branch") ?: ""
            
            val postData = hashMapOf(
                "postId" to postId,
                "authorId" to uid,
                "authorName" to name,
                "authorMajor" to major,
                "content" to content,
                "imageUrl" to imageUrl,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "likes" to 0,
                "likedBy" to emptyList<String>()
            )

            db.collection("posts").document(postId).set(postData)
                .addOnCompleteListener { task ->
                    onComplete(task.isSuccessful)
                }
        }
    }

    fun deletePost(postId: String, onComplete: (Boolean) -> Unit) {
        db.collection("posts").document(postId).delete()
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun getFeed(onResult: (List<Post>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("posts")
            .limit(50)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onResult(emptyList())
                    return@addSnapshotListener
                }
                val posts = snapshot?.mapNotNull { it.toObject(Post::class.java) } ?: emptyList()
                val sortedPosts = posts.sortedByDescending { it.timestampDate?.time ?: 0L }
                onResult(sortedPosts)
            }
    }

    fun likePost(postId: String, onComplete: (Boolean) -> Unit) {
        reactToPost(postId, "❤️", onComplete)
    }

    fun reactToPost(postId: String, emoji: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val postRef = db.collection("posts").document(postId)
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(postRef)
            val reactions = snapshot.get("reactions") as? Map<String, List<String>> ?: emptyMap()
            val newReactions = reactions.toMutableMap()
            
            // Remove user from all other reactions first (optional, standard for feed apps)
            newReactions.forEach { (key, uids) ->
                if (uids.contains(uid)) {
                    newReactions[key] = uids.filter { it != uid }
                }
            }
            
            val uidsForEmoji = newReactions[emoji]?.toMutableList() ?: mutableListOf()
            if (!uidsForEmoji.contains(uid)) {
                uidsForEmoji.add(uid)
                newReactions[emoji] = uidsForEmoji
                
                // Update legacy like fields for backward compatibility
                if (emoji == "❤️") {
                    val likedBy = snapshot.get("likedBy") as? List<String> ?: emptyList()
                    if (!likedBy.contains(uid)) {
                        transaction.update(postRef, "likedBy", likedBy + uid)
                        transaction.update(postRef, "likes", (snapshot.getLong("likes") ?: 0) + 1)
                    }
                }
            } else {
                // Toggle off if clicking same emoji
                uidsForEmoji.remove(uid)
                newReactions[emoji] = uidsForEmoji
                if (emoji == "❤️") {
                    val likedBy = snapshot.get("likedBy") as? List<String> ?: emptyList()
                    transaction.update(postRef, "likedBy", likedBy.filter { it != uid })
                    transaction.update(postRef, "likes", ((snapshot.getLong("likes") ?: 1) - 1).coerceAtLeast(0))
                }
            }
            
            transaction.update(postRef, "reactions", newReactions)
        }.addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun addComment(postId: String, content: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val commentId = db.collection("posts").document(postId).collection("comments").document().id
        
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "User"
            val avatar = doc.getString("profileImageUrl")
            
            val commentData = hashMapOf(
                "commentId" to commentId,
                "authorId" to uid,
                "authorName" to name,
                "authorAvatarUrl" to avatar,
                "content" to content,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            val commentRef = db.collection("posts").document(postId).collection("comments").document(commentId)
            val postRef = db.collection("posts").document(postId)
            
            batch.set(commentRef, commentData)
            batch.update(postRef, "commentCount", com.google.firebase.firestore.FieldValue.increment(1))
            
            batch.commit().addOnCompleteListener { onComplete(it.isSuccessful) }
        }
    }

    fun getComments(postId: String, onResult: (List<com.example.unilink.models.Comment>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("posts").document(postId).collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val comments = snapshot?.mapNotNull { it.toObject(com.example.unilink.models.Comment::class.java) } ?: emptyList()
                onResult(comments)
            }
    }
}
