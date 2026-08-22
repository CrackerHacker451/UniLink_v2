package com.example.unilink.repository

import com.example.unilink.models.VaultItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class VaultRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun uploadItem(title: String, type: String, price: Int, description: String, tags: List<String>, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val itemId = db.collection("vault").document().id
        
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "User"
            val item = VaultItem(
                itemId = itemId,
                title = title,
                type = type,
                price = price,
                description = description,
                tags = tags,
                uploaderId = uid,
                uploaderName = name
            )
            db.collection("vault").document(itemId).set(item).addOnCompleteListener { onComplete(it.isSuccessful) }
        }
    }

    fun getAllItems(onResult: (List<VaultItem>) -> Unit) {
        db.collection("vault").get().addOnSuccessListener { snapshot ->
            onResult(snapshot.toObjects(VaultItem::class.java))
        }.addOnFailureListener { onResult(emptyList()) }
    }

    fun unlockItem(item: VaultItem, onComplete: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        
        db.runTransaction { transaction ->
            val userSnap = transaction.get(userRef)
            val currentCoins = userSnap.getLong("uniCoins") ?: 0
            val isPremium = userSnap.getBoolean("isPremium") ?: false
            
            // Premium users get 50% discount on vault resources
            val price = if (isPremium) item.price / 2 else item.price
            
            if (currentCoins < price) {
                throw Exception("Insufficient UniCoins. ${if (isPremium) "(Premium Price: $price)" else "(Price: $price)"}")
            }
            
            // Deduct from buyer
            transaction.update(userRef, "uniCoins", FieldValue.increment(-price.toLong()))
            // Add to uploader
            transaction.update(db.collection("users").document(item.uploaderId), "uniCoins", FieldValue.increment(price.toLong()))
            // Update download count
            transaction.update(db.collection("vault").document(item.itemId), "downloadCount", FieldValue.increment(1))
            
        }.addOnSuccessListener { onComplete(true, "Item unlocked!") }
         .addOnFailureListener { onComplete(false, it.message ?: "Transaction failed") }
    }
}
