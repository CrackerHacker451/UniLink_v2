package com.example.unilink.repository

import com.example.unilink.models.Doubt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration

class DoubtRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun postDoubt(question: String, reward: Int, tags: List<String>, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val doubtId = db.collection("doubts").document().id
        
        // Fetch user info first
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "Student"
            
            val doubtData = hashMapOf(
                "doubtId" to doubtId,
                "userId" to uid,
                "userName" to name,
                "question" to question,
                "rewardCoins" to reward,
                "tags" to tags,
                "answerCount" to 0,
                "accentColor" to listOf("#22D3EE", "#EC4899", "#8B5CF6").random(),
                "isOnline" to true,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            db.collection("doubts").document(doubtId).set(doubtData)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Deduct coins from user
                        db.collection("users").document(uid).update("uniCoins", com.google.firebase.firestore.FieldValue.increment(-reward.toLong()))
                    }
                    onComplete(task.isSuccessful)
                }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun observeDoubts(onUpdate: (List<Doubt>) -> Unit): ListenerRegistration {
        return db.collection("doubts")
            .limit(30)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val doubts = snapshot?.mapNotNull { it.toObject(Doubt::class.java) } ?: emptyList()
                val sortedDoubts = doubts.sortedByDescending { it.timestampDate?.time ?: 0L }
                onUpdate(sortedDoubts)
            }
    }
}
