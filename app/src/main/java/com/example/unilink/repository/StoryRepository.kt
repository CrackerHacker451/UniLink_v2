package com.example.unilink.repository

import com.example.unilink.models.Story
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class StoryRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun uploadStory(imageUrl: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val storyId = db.collection("stories").document().id
        
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: auth.currentUser?.displayName ?: "Student"
            val avatar = doc.getString("profileImageUrl") ?: auth.currentUser?.photoUrl?.toString()
            
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
        }.addOnFailureListener {
            val user = auth.currentUser
            val name = user?.displayName ?: "Student"
            val avatar = user?.photoUrl?.toString()
            
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
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val twentyFourHoursAgoTime = cal.timeInMillis

        return db.collection("stories")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onResult(emptyList())
                    return@addSnapshotListener
                }

                val allStories = snapshot.mapNotNull { doc ->
                    try {
                        doc.toObject(Story::class.java)
                    } catch (_: Exception) {
                        null
                    }
                }

                val validStories = allStories.filter { story ->
                    val storyTime = story.timestampDate?.time ?: System.currentTimeMillis()
                    storyTime >= twentyFourHoursAgoTime
                }.sortedByDescending { story ->
                    story.timestampDate?.time ?: System.currentTimeMillis()
                }

                val groupedStories = validStories.distinctBy { it.userId }
                onResult(groupedStories)
            }
    }
}
