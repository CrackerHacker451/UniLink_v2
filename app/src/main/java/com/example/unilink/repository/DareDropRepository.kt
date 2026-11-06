package com.example.unilink.repository

import com.example.unilink.ui.DareProofItem
import com.example.unilink.ui.ProposedDareItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class ActiveDailyDare(
    val id: String = "active_dare",
    val title: String = "Take a high-five selfie with a campus professor or library cat! 📸",
    val description: String = "Post your photo proof to earn +25 UniCoins & Campus Star Badge!",
    val rewardCoins: Int = 25,
    val expiresAt: Long = System.currentTimeMillis() + 86400000L
)

class DareDropRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val notificationRepository = NotificationRepository()

    private val presetDares = listOf(
        ActiveDailyDare(
            title = "Take a high-five selfie with a campus professor or library cat! 📸",
            description = "High five a professor, canteen uncle, or library pet to earn +25 UniCoins!",
            rewardCoins = 25
        ),
        ActiveDailyDare(
            title = "Deliver a wholesome whisper compliment in the quiet library zone 📚",
            description = "Brighten someone's study session with a quiet compliment and post proof!",
            rewardCoins = 25
        ),
        ActiveDailyDare(
            title = "Moonwalk 5 steps in the main Quad hall! 🕺",
            description = "Show off your retro dance moves in public for +25 UniCoins!",
            rewardCoins = 25
        ),
        ActiveDailyDare(
            title = "Draw a funny mini face on your morning canteen cup ☕",
            description = "Doodle a creative cup face and post your photo proof!",
            rewardCoins = 25
        )
    )

    fun observeActiveDare(onUpdate: (ActiveDailyDare) -> Unit): ListenerRegistration {
        val docRef = db.collection("dare_drop").document("active_dare")

        return docRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) {
                rotateNewDare(onUpdate)
                return@addSnapshotListener
            }

            try {
                val title = snapshot.getString("title") ?: presetDares[0].title
                val desc = snapshot.getString("description") ?: presetDares[0].description
                val coins = (snapshot.getLong("rewardCoins") ?: 25L).toInt()
                val expiresAt = snapshot.getLong("expiresAt") ?: (System.currentTimeMillis() + 86400000L)

                if (expiresAt <= System.currentTimeMillis()) {
                    rotateNewDare(onUpdate)
                } else {
                    onUpdate(ActiveDailyDare("active_dare", title, desc, coins, expiresAt))
                }
            } catch (_: Exception) {
                rotateNewDare(onUpdate)
            }
        }
    }

    private fun rotateNewDare(onUpdate: (ActiveDailyDare) -> Unit) {
        val newDare = presetDares.random()
        val expiresAt = System.currentTimeMillis() + 86400000L

        val data = hashMapOf(
            "id" to "active_dare",
            "title" to newDare.title,
            "description" to newDare.description,
            "rewardCoins" to newDare.rewardCoins,
            "expiresAt" to expiresAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("dare_drop").document("active_dare").set(data).addOnSuccessListener {
            onUpdate(ActiveDailyDare("active_dare", newDare.title, newDare.description, newDare.rewardCoins, expiresAt))
            notificationRepository.sendGlobalUpdateNotification(
                title = "🎯 New 24-Hour Dare Drop Live!",
                message = "Today's Challenge: ${newDare.title} (+25 UniCoins)"
            ) {}
        }
    }

    fun publishProof(caption: String, imageUrl: String, videoUrl: String? = null, isVideo: Boolean = false, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val proofId = db.collection("dare_proofs").document().id

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: auth.currentUser?.displayName ?: "Student"
            val dept = doc.getString("branch") ?: "Campus"

            val proofData = hashMapOf(
                "id" to proofId,
                "authorId" to uid,
                "authorName" to name,
                "authorDept" to dept,
                "caption" to caption,
                "imageUrl" to imageUrl,
                "videoUrl" to (videoUrl ?: ""),
                "isVideo" to isVideo,
                "likesCount" to 1,
                "cheersCount" to 0,
                "likedBy" to listOf(uid),
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("dare_proofs").document(proofId).set(proofData)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        db.collection("users").document(uid).update(
                            "uniCoins", FieldValue.increment(25),
                            "totalXp", FieldValue.increment(50)
                        )
                    }
                    onComplete(task.isSuccessful)
                }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun observeProofs(onUpdate: (List<DareProofItem>) -> Unit): ListenerRegistration {
        val currentUid = auth.currentUser?.uid ?: ""

        return db.collection("dare_proofs")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val proofs = snapshot.mapNotNull { doc ->
                    try {
                        val id = doc.getString("id") ?: doc.id
                        val name = doc.getString("authorName") ?: "Student"
                        val dept = doc.getString("authorDept") ?: "Campus"
                        val caption = doc.getString("caption") ?: ""
                        val img = doc.get("imageUrl")
                        val vid = doc.getString("videoUrl")
                        val isVid = doc.getBoolean("isVideo") ?: false
                        val likes = (doc.getLong("likesCount") ?: 0L).toInt()
                        val cheers = (doc.getLong("cheersCount") ?: 0L).toInt()
                        val likedBy = (doc.get("likedBy") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        val authorId = doc.getString("authorId") ?: ""

                        DareProofItem(
                            id = id,
                            authorName = name,
                            authorDept = dept,
                            timeAgo = "Live",
                            caption = caption,
                            imageUrl = img,
                            videoUrl = vid,
                            isVideo = isVid || !vid.isNullOrEmpty(),
                            likesCount = likes,
                            isLiked = likedBy.contains(currentUid),
                            cheersCount = cheers,
                            isCurrentUser = authorId == currentUid
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

                onUpdate(proofs)
            }
    }

    fun proposeDare(title: String, description: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val dareId = db.collection("proposed_dares").document().id

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "Student"

            val dareData = hashMapOf(
                "id" to dareId,
                "title" to title,
                "description" to description,
                "proposedBy" to name,
                "votesCount" to 1,
                "votedBy" to listOf(uid),
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("proposed_dares").document(dareId).set(dareData)
                .addOnCompleteListener { task -> onComplete(task.isSuccessful) }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun observeProposedDares(onUpdate: (List<ProposedDareItem>) -> Unit): ListenerRegistration {
        val currentUid = auth.currentUser?.uid ?: ""

        return db.collection("proposed_dares")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val proposed = snapshot.mapNotNull { doc ->
                    try {
                        val id = doc.getString("id") ?: doc.id
                        val title = doc.getString("title") ?: ""
                        val desc = doc.getString("description") ?: ""
                        val proposedBy = doc.getString("proposedBy") ?: "Student"
                        val votes = (doc.getLong("votesCount") ?: 0L).toInt()
                        val votedBy = (doc.get("votedBy") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                        ProposedDareItem(
                            id = id,
                            title = title,
                            description = desc,
                            proposedBy = proposedBy,
                            votesCount = votes,
                            hasVoted = votedBy.contains(currentUid)
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

                onUpdate(proposed)
            }
    }

    fun toggleLike(proofId: String) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("dare_proofs").document(proofId)

        docRef.get().addOnSuccessListener { doc ->
            val likedBy = (doc.get("likedBy") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            if (likedBy.contains(uid)) {
                docRef.update(
                    "likedBy", FieldValue.arrayRemove(uid),
                    "likesCount", FieldValue.increment(-1)
                )
            } else {
                docRef.update(
                    "likedBy", FieldValue.arrayUnion(uid),
                    "likesCount", FieldValue.increment(1)
                )
            }
        }
    }

    fun toggleVote(dareId: String) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("proposed_dares").document(dareId)

        docRef.get().addOnSuccessListener { doc ->
            val votedBy = (doc.get("votedBy") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            if (votedBy.contains(uid)) {
                docRef.update(
                    "votedBy", FieldValue.arrayRemove(uid),
                    "votesCount", FieldValue.increment(-1)
                )
            } else {
                docRef.update(
                    "votedBy", FieldValue.arrayUnion(uid),
                    "votesCount", FieldValue.increment(1)
                )
            }
        }
    }
}
