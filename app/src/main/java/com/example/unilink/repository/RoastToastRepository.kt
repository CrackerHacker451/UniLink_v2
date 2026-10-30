package com.example.unilink.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class RoastPoll(
    val pollId: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val optionVotes: Map<String, Int> = emptyMap(),
    val userVotes: Map<String, Int> = emptyMap(),
    val createdBy: String = "",
    val createdByName: String = "",
    val totalVotes: Int = 0
)

class RoastToastRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val notificationRepository = NotificationRepository()

    fun createPoll(question: String, options: List<String>, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val pollId = db.collection("roast_polls").document().id

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "Student"

            val initialVotes = options.associateWith { 0 }

            val pollData = hashMapOf(
                "pollId" to pollId,
                "question" to question,
                "options" to options,
                "optionVotes" to initialVotes,
                "userVotes" to mapOf<String, Int>(),
                "createdBy" to uid,
                "createdByName" to name,
                "totalVotes" to 0,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.collection("roast_polls").document(pollId).set(pollData)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        notificationRepository.sendGlobalUpdateNotification(
                            title = "🔥 New Roast & Toast Poll!",
                            message = "$name posted: '$question'"
                        ) {}
                    }
                    onComplete(task.isSuccessful)
                }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun observePolls(onUpdate: (List<RoastPoll>) -> Unit): ListenerRegistration {
        return db.collection("roast_polls")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val polls = snapshot.mapNotNull { doc ->
                    try {
                        val id = doc.getString("pollId") ?: doc.id
                        val q = doc.getString("question") ?: ""
                        val opts = (doc.get("options") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        val creator = doc.getString("createdBy") ?: ""
                        val creatorName = doc.getString("createdByName") ?: "Student"
                        val votesMap = (doc.get("optionVotes") as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { (it.value as? Long ?: 0L).toInt() } ?: emptyMap()
                        val userVotesMap = (doc.get("userVotes") as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { (it.value as? Long ?: 0L).toInt() } ?: emptyMap()
                        val total = (doc.getLong("totalVotes") ?: 0L).toInt()

                        RoastPoll(id, q, opts, votesMap, userVotesMap, creator, creatorName, total)
                    } catch (_: Exception) {
                        null
                    }
                }

                onUpdate(polls)
            }
    }

    fun voteInPoll(pollId: String, optionIndex: Int, optionText: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val docRef = db.collection("roast_polls").document(pollId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val userVotes = (snapshot.get("userVotes") as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { (it.value as? Long ?: 0L).toInt() }?.toMutableMap() ?: mutableMapOf()

            if (!userVotes.containsKey(uid)) {
                userVotes[uid] = optionIndex
                val optionVotes = (snapshot.get("optionVotes") as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { (it.value as? Long ?: 0L).toInt() }?.toMutableMap() ?: mutableMapOf()
                val currentVotes = optionVotes[optionText] ?: 0
                optionVotes[optionText] = currentVotes + 1
                val totalVotes = (snapshot.getLong("totalVotes") ?: 0L).toInt() + 1

                transaction.update(docRef, mapOf(
                    "userVotes" to userVotes,
                    "optionVotes" to optionVotes,
                    "totalVotes" to totalVotes
                ))
            }
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                db.collection("users").document(uid).update("uniCoins", FieldValue.increment(5))
            }
            onComplete(task.isSuccessful)
        }
    }
}
