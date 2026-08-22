package com.example.unilink.repository

import com.example.unilink.models.Story
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import java.util.Date
import java.util.Calendar

class StoryRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun uploadStory(imageUrl: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val storyId = db.collection("stories").document().id
        
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "User"
            val avatar = doc.getString("profileImageUrl")
            
            val story = Story(
                storyId = storyId,
                userId = uid,
                userName = name,
                userAvatarUrl = avatar,
                imageUrl = imageUrl,
                timestamp = FieldValue.serverTimestamp()
            )

            db.collection("stories").document(storyId).set(story)
                .addOnCompleteListener { task ->
                    onComplete(task.isSuccessful)
                }
        }
    }

    fun getActiveStories(onResult: (List<Story>) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        // Calculate the timestamp for 24 hours ago
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val twentyFourHoursAgo = cal.time

        return db.collection("stories")
            .whereGreaterThan("timestamp", twentyFourHoursAgo)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onResult(emptyList())
                    return@addSnapshotListener
                }
                
                val allStories = snapshot?.mapNotNull { it.toObject(Story::class.java) } ?: emptyList()
                
                // Group by userId and take the latest one for each user (Single Story Per User requirement)
                val groupedStories = allStories.distinctBy { it.userId }
                
                onResult(groupedStories)
            }
    }
}
