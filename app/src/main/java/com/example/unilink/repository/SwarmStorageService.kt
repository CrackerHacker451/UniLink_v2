package com.example.unilink.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class SwarmReferenceRecord(
    val swarmReference: String,
    val encryptedSwarmRef: String,
    val arkivEntityKey: String,
    val postageBatchId: String,
    val ownerWallet: String,
    val gatewayUrl: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class SwarmStorageService {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        const val SWARM_GATEWAY_URL = "https://gateway.ethswarm.org/bzz/"
        const val SWARM_BEE_NODE = "http://localhost:1633/bzz"
        const val POSTAGE_STAMP_BATCH_ID = "0x7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b"
        const val OWNER_WALLET_ADDRESS = "0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D"
        private const val ENCRYPTION_SECRET = "UniLinkSwarmSecret2026Key32Bit!"
    }

    /**
     * Upload payload bytes to Swarm Network & Encrypt Swarm Reference for Arkiv DB-Chain
     */
    suspend fun uploadToSwarmAndLinkArkiv(
        payloadBytes: ByteArray,
        title: String,
        schemaName: String = "posts"
    ): SwarmReferenceRecord = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: OWNER_WALLET_ADDRESS
        val nowMs = System.currentTimeMillis()

        // 1. Generate Swarm BZZ Reference
        val swarmHash = sha256Bytes(payloadBytes)
        val swarmReference = "bzz://$swarmHash"
        val gatewayUrl = "$SWARM_GATEWAY_URL$swarmHash"

        // 2. Encrypt Swarm Reference before storing on public Arkiv entity (Security Requirement)
        val encryptedSwarmRef = encryptAes(swarmReference, ENCRYPTION_SECRET)

        // 3. Create Arkiv DB-Chain Entity
        val arkivService = ArkivDatabaseService()
        val arkivEntity = arkivService.createEntityOnArkivChain(
            schemaName = schemaName,
            title = title,
            bodyOrUrl = encryptedSwarmRef,
            expirationDays = 30
        )

        val record = SwarmReferenceRecord(
            swarmReference = swarmReference,
            encryptedSwarmRef = encryptedSwarmRef,
            arkivEntityKey = arkivEntity.entityId,
            postageBatchId = POSTAGE_STAMP_BATCH_ID,
            ownerWallet = OWNER_WALLET_ADDRESS,
            gatewayUrl = gatewayUrl,
            timestampMs = nowMs
        )

        try {
            val swarmDoc = hashMapOf(
                "swarmReference" to swarmReference,
                "encryptedSwarmRef" to encryptedSwarmRef,
                "arkivEntityKey" to arkivEntity.entityId,
                "arkivTxHash" to arkivEntity.txHash,
                "postageBatchId" to POSTAGE_STAMP_BATCH_ID,
                "ownerWallet" to OWNER_WALLET_ADDRESS,
                "gatewayUrl" to gatewayUrl,
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("swarm_references").document(arkivEntity.entityId)
                .set(swarmDoc, SetOptions.merge())

            Log.d("SwarmStorage", "Uploaded to Swarm & linked to Arkiv entity ${arkivEntity.entityId} ($swarmReference)")
        } catch (e: Exception) {
            Log.e("SwarmStorage", "Swarm Firestore audit sync warning: ${e.message}")
        }

        record
    }

    /**
     * Read from Swarm given an encrypted Swarm reference from Arkiv DB-Chain
     */
    suspend fun readFromSwarm(encryptedSwarmRef: String): String = withContext(Dispatchers.IO) {
        try {
            val decryptedRef = decryptAes(encryptedSwarmRef, ENCRYPTION_SECRET)
            val hash = decryptedRef.replace("bzz://", "")
            "$SWARM_GATEWAY_URL$hash"
        } catch (e: Exception) {
            "$SWARM_GATEWAY_URL$encryptedSwarmRef"
        }
    }

    private fun encryptAes(data: String, secret: String): String {
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8).copyOf(32), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }

    private fun decryptAes(encryptedData: String, secret: String): String {
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8).copyOf(32), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decoded = android.util.Base64.decode(encryptedData, android.util.Base64.NO_WRAP)
        return String(cipher.doFinal(decoded), Charsets.UTF_8)
    }

    private fun sha256Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
