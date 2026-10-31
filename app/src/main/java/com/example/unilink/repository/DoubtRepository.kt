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
        return db.collection("doubts")
            .addSnapshotListener { snapshot, e ->
                val firestoreDoubts = if (e == null && snapshot != null && !snapshot.isEmpty) {
                    snapshot.mapNotNull { doc ->
                        try {
                            val d = doc.toObject(Doubt::class.java)
                            if (d != null && d.doubtId.isNotEmpty()) d else {
                                Doubt(
                                    doubtId = doc.id,
                                    userId = doc.getString("userId") ?: "",
                                    userName = doc.getString("userName") ?: "Student",
                                    userAvatarUrl = doc.getString("userAvatarUrl"),
                                    question = doc.getString("question") ?: "",
                                    rewardCoins = (doc.getLong("rewardCoins") ?: 0L).toInt(),
                                    tags = (doc.get("tags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                    answerCount = (doc.getLong("answerCount") ?: 0L).toInt(),
                                    accentColor = doc.getString("accentColor") ?: "#22D3EE",
                                    isOnline = doc.getBoolean("isOnline") ?: true,
                                    aiResponse = doc.getString("aiResponse"),
                                    timestamp = doc.get("timestamp")
                                )
                            }
                        } catch (_: Exception) {
                            try {
                                Doubt(
                                    doubtId = doc.id,
                                    userId = doc.getString("userId") ?: "",
                                    userName = doc.getString("userName") ?: "Student",
                                    userAvatarUrl = doc.getString("userAvatarUrl"),
                                    question = doc.getString("question") ?: "",
                                    rewardCoins = (doc.getLong("rewardCoins") ?: 0L).toInt(),
                                    tags = (doc.get("tags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                    answerCount = (doc.getLong("answerCount") ?: 0L).toInt(),
                                    accentColor = doc.getString("accentColor") ?: "#22D3EE",
                                    isOnline = doc.getBoolean("isOnline") ?: true,
                                    aiResponse = doc.getString("aiResponse"),
                                    timestamp = doc.get("timestamp")
                                )
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                } else {
                    emptyList()
                }

                val sortedDoubts = firestoreDoubts.sortedByDescending { doubt ->
                    doubt.timestampDate?.time ?: System.currentTimeMillis()
                }

                if (sortedDoubts.isNotEmpty()) {
                    onUpdate(sortedDoubts)
                } else {
                    onUpdate(getSampleCampusDoubts())
                }
            }
    }

    private fun getSampleCampusDoubts(): List<Doubt> {
        return listOf(
            Doubt(
                doubtId = "sample_1",
                userName = "Sneha Reddy",
                question = "How to optimize PageRank algorithm & graph traversal in Kotlin for large datasets? 💻",
                rewardCoins = 25,
                tags = listOf("Algorithms", "Kotlin"),
                answerCount = 2,
                accentColor = "#22D3EE"
            ),
            Doubt(
                doubtId = "sample_2",
                userName = "Aryan Sharma",
                question = "Can someone explain Quantum Entanglement in simple terms for Physics Lab assignment? ⚛️",
                rewardCoins = 15,
                tags = listOf("Physics", "Quantum"),
                answerCount = 1,
                accentColor = "#EC4899"
            ),
            Doubt(
                doubtId = "sample_3",
                userName = "Meera Nair",
                question = "Best architectural pattern for Jetpack Compose StateFlow in Android Studio? 📱",
                rewardCoins = 20,
                tags = listOf("Android", "Compose"),
                answerCount = 3,
                accentColor = "#10B981"
            )
        )
    }
}
