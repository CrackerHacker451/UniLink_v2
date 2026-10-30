package com.example.unilink.repository

import com.example.unilink.models.Doubt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DoubtRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun postDoubt(question: String, reward: Int, tags: List<String>, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val doubtId = db.collection("doubts").document().id
        
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: auth.currentUser?.displayName ?: "Student"
            val avatar = doc.getString("profileImageUrl") ?: auth.currentUser?.photoUrl?.toString()
            
            val doubtData = hashMapOf(
                "doubtId" to doubtId,
                "userId" to uid,
                "userName" to name,
                "userAvatarUrl" to avatar,
                "question" to question,
                "rewardCoins" to reward,
                "tags" to tags,
                "answerCount" to 0,
                "accentColor" to listOf("#22D3EE", "#EC4899", "#8B5CF6", "#10B981", "#F59E0B").random(),
                "isOnline" to true,
                "timestamp" to FieldValue.serverTimestamp()
            )

            // Publish doubt to 'doubts' collection
            db.collection("doubts").document(doubtId).set(doubtData)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Deduct coins from user balance
                        db.collection("users").document(uid).update("uniCoins", FieldValue.increment(-reward.toLong()))
                        
                        // Publish doubt to Campus Pulse feed ('posts' collection) so all campus users see it in real-time
                        val pulseId = "pulse_doubt_$doubtId"
                        val pulseData = hashMapOf(
                            "postId" to pulseId,
                            "authorId" to uid,
                            "authorName" to name,
                            "authorAvatarUrl" to avatar,
                            "authorMajor" to "Student Doubt",
                            "content" to "🧠 DOUBT BOARD DROP: \"$question\" (Reward: $reward UniCoins)",
                            "postType" to "doubt",
                            "tag" to "#DoubtBoard",
                            "timestamp" to FieldValue.serverTimestamp(),
                            "likes" to 0,
                            "commentCount" to 0
                        )
                        db.collection("posts").document(pulseId).set(pulseData)

                        // Auto-generate instant UniAI Genius verified answer
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val aiResponse = GeminiRepository().getAiHint(question, isPremium = true)
                                val answerRef = db.collection("doubts").document(doubtId).collection("answers").document()
                                val answerData = hashMapOf(
                                    "answerId" to answerRef.id,
                                    "authorName" to "✨ UniAI Genius Assistant",
                                    "answerText" to aiResponse,
                                    "upvotes" to 5,
                                    "isVerified" to true,
                                    "timestamp" to FieldValue.serverTimestamp()
                                )
                                answerRef.set(answerData)
                                incrementAnswerCount(doubtId)
                            } catch (_: Exception) {}
                        }
                    }
                    onComplete(task.isSuccessful)
                }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun incrementAnswerCount(doubtId: String) {
        if (doubtId.isEmpty() || doubtId.startsWith("sample_")) return
        db.collection("doubts").document(doubtId).update(
            "answerCount", FieldValue.increment(1)
        )
    }

    fun observeDoubts(onUpdate: (List<Doubt>) -> Unit): ListenerRegistration {
        val tenHoursAgoTime = System.currentTimeMillis() - (10 * 60 * 60 * 1000L) // 10 Hours Active Window

        return db.collection("doubts")
            .addSnapshotListener { snapshot, e ->
                val firestoreDoubts = if (e == null && snapshot != null) {
                    snapshot.mapNotNull { doc ->
                        try {
                            doc.toObject(Doubt::class.java)
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else {
                    emptyList()
                }

                // Filter active doubts posted within last 10 hours
                val activeFirestoreDoubts = firestoreDoubts.filter { doubt ->
                    val doubtTime = doubt.timestampDate?.time ?: System.currentTimeMillis()
                    doubtTime >= tenHoursAgoTime
                }.sortedByDescending { doubt ->
                    doubt.timestampDate?.time ?: System.currentTimeMillis()
                }

                onUpdate(activeFirestoreDoubts)
            }
    }
}
