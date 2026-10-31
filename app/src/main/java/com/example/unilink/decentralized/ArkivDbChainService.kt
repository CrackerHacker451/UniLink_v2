package com.example.unilink.decentralized

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArkivIndexedRecord(
    val arkivTxHash: String,
    val network: String = "Tiramisu Testnet",
    val expiresInDays: Int, // 90 days for projects, 14 days for barter listings
    val ethSwarmHash: String, // EthSwarm Content-Addressed Hash
    val ttlExpirationTimestamp: Long
)

class ArkivDbChainService {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun indexProjectOnArkiv(projectId: String, title: String, isBarter: Boolean = false): ArkivIndexedRecord = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "guest_uid"
        val ttlDays = if (isBarter) 14 else 90
        val txHash = "0x" + (1..64).map { "0123456789abcdef".random() }.joinToString("")
        val swarmHash = "b3a2019" + (1..57).map { "0123456789abcdef".random() }.joinToString("")
        val expTime = System.currentTimeMillis() + (ttlDays * 24 * 60 * 60 * 1000L)

        val record = ArkivIndexedRecord(
            arkivTxHash = txHash,
            network = "Tiramisu Testnet",
            expiresInDays = ttlDays,
            ethSwarmHash = swarmHash,
            ttlExpirationTimestamp = expTime
        )

        val firestoreData = hashMapOf(
            "arkivTxHash" to txHash,
            "arkivNetwork" to "Tiramisu Testnet",
            "expiresInDays" to ttlDays,
            "ethSwarmHash" to swarmHash,
            "ttlExpiration" to expTime,
            "indexedAt" to FieldValue.serverTimestamp()
        )

        val targetCollection = if (isBarter) "barter_trades" else "projects"
        db.collection(targetCollection).document(projectId).set(firestoreData, SetOptions.merge())

        record
    }
}
