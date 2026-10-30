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

    fun uploadPdfItem(title: String, type: String, description: String, fileUrl: String, tags: List<String>, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val itemId = db.collection("vault").document().id

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "Campus Innovator"
            val item = VaultItem(
                itemId = itemId,
                title = title,
                type = type,
                price = 10,
                description = description,
                fileUrl = fileUrl,
                tags = tags,
                uploaderId = uid,
                uploaderName = name
            )
            db.collection("vault").document(itemId).set(item).addOnSuccessListener {
                db.collection("users").document(uid).update(
                    "uniCoins", FieldValue.increment(15),
                    "totalXp", FieldValue.increment(25)
                )
                onComplete(true)
            }.addOnFailureListener {
                onComplete(false)
            }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun getAllItems(onResult: (List<VaultItem>) -> Unit) {
        db.collection("vault").get().addOnSuccessListener { snapshot ->
            val items = snapshot.toObjects(VaultItem::class.java)
            onResult(items.ifEmpty { getSampleVaultItems() })
        }.addOnFailureListener { onResult(getSampleVaultItems()) }
    }

    private fun getSampleVaultItems(): List<VaultItem> {
        return listOf(
            VaultItem(
                itemId = "sample_v1",
                title = "Data Structures & Algorithms Formula Sheet (PDF)",
                type = "Notes",
                description = "Complete handwritten notes with time complexity tables for trees, graphs, and dynamic programming.",
                uploaderName = "Sneha Reddy",
                price = 10,
                fileUrl = "https://www.w3.org/W3C/DesignIssues/diagrams/pdf-spec.pdf",
                tags = listOf("Notes", "DSA")
            ),
            VaultItem(
                itemId = "sample_v2",
                title = "Semester 4 Midterm Solved Papers (PDF)",
                type = "Papers",
                description = "Previous year solved exam papers with detailed step-by-step solutions.",
                uploaderName = "Aarav Sharma",
                price = 15,
                fileUrl = "https://www.w3.org/W3C/DesignIssues/diagrams/pdf-spec.pdf",
                tags = listOf("Papers", "Exams")
            )
        )
    }

    fun unlockItem(item: VaultItem, onComplete: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)

        db.runTransaction { transaction ->
            val userSnap = transaction.get(userRef)
            val currentCoins = userSnap.getLong("uniCoins") ?: 0
            val isPremium = userSnap.getBoolean("isPremium") ?: false

            val price = if (isPremium) item.price / 2 else item.price

            if (currentCoins < price) {
                throw Exception("Insufficient UniCoins. ${if (isPremium) "(Premium Price: $price)" else "(Price: $price)"}")
            }

            transaction.update(userRef, "uniCoins", FieldValue.increment(-price.toLong()))
            if (item.uploaderId.isNotEmpty()) {
                transaction.update(db.collection("users").document(item.uploaderId), "uniCoins", FieldValue.increment(price.toLong()))
            }
            if (item.itemId.isNotEmpty()) {
                transaction.update(db.collection("vault").document(item.itemId), "downloadCount", FieldValue.increment(1))
            }

        }.addOnSuccessListener { onComplete(true, "Item unlocked!") }
         .addOnFailureListener { onComplete(false, it.message ?: "Transaction failed") }
    }
}
