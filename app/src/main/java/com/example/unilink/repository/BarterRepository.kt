package com.example.unilink.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class BarterTradeItem(
    val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val offering: String = "",
    val wanting: String = "",
    val category: String = "Notes",
    val isAvailable: Boolean = true,
    val timeAgo: String = "Live"
)

class BarterRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun publishTrade(offering: String, wanting: String, category: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val tradeId = db.collection("barter_trades").document().id

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: auth.currentUser?.displayName ?: "Campus Trader"
            val avatar = doc.getString("profileImageUrl") ?: auth.currentUser?.photoUrl?.toString()

            val tradeData = hashMapOf(
                "id" to tradeId,
                "authorUid" to uid,
                "authorName" to name,
                "authorAvatarUrl" to avatar,
                "offering" to offering,
                "wanting" to wanting,
                "category" to category,
                "isAvailable" to true,
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("barter_trades").document(tradeId).set(tradeData)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Reward student with +10 UniCoins and +25 XP
                        db.collection("users").document(uid).update(
                            "uniCoins", FieldValue.increment(10),
                            "totalXp", FieldValue.increment(25)
                        )
                        LiveFeedRepository().publishPulse("🔄 $name posted a new Barter Trade: Offering '$offering' for '$wanting'!")
                    }
                    onComplete(task.isSuccessful)
                }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun observeTrades(categoryFilter: String? = null, onUpdate: (List<BarterTradeItem>) -> Unit): ListenerRegistration {
        return db.collection("barter_trades")
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val items = snapshot.mapNotNull { doc ->
                    try {
                        val id = doc.getString("id") ?: doc.id
                        val authorUid = doc.getString("authorUid") ?: ""
                        val authorName = doc.getString("authorName") ?: "Campus Student"
                        val authorAvatarUrl = doc.getString("authorAvatarUrl")
                        val offering = doc.getString("offering") ?: ""
                        val wanting = doc.getString("wanting") ?: ""
                        val category = doc.getString("category") ?: "Notes"
                        val isAvailable = doc.getBoolean("isAvailable") ?: true

                        BarterTradeItem(
                            id = id,
                            authorUid = authorUid,
                            authorName = authorName,
                            authorAvatarUrl = authorAvatarUrl,
                            offering = offering,
                            wanting = wanting,
                            category = category,
                            isAvailable = isAvailable,
                            timeAgo = "Live"
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.filter { item ->
                    categoryFilter == null || categoryFilter.equals("All", ignoreCase = true) || item.category.equals(categoryFilter, ignoreCase = true)
                }

                onUpdate(items)
            }
    }

    fun deleteTrade(tradeId: String, onComplete: (Boolean) -> Unit) {
        if (tradeId.isEmpty()) return onComplete(false)
        db.collection("barter_trades").document(tradeId).update("isAvailable", false)
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }
}
