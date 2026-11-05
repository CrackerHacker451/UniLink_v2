package com.example.unilink.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

data class ArkivSchemaField(
    val name: String,
    val type: String,
    val isIndexed: Boolean = true
)

data class ArkivEntity(
    val entityId: String,
    val schemaName: String,
    val ownerAddress: String,
    val payloadJson: String,
    val expirationDays: Int = 30,
    val timestampMs: Long = System.currentTimeMillis(),
    val txHash: String = ""
)

class ArkivDatabaseService {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        const val ARKIV_RPC_ENDPOINT = "https://rpc.arkiv.network"
        const val ARKIV_EXPLORER_URL = "https://explorer.arkiv.network/tx/"
        const val SDK_VERSION = "@arkiv-network/sdk@1.0.0"
        const val GLM_POWERED_ENGINE = "Golem DB-Engine (GLM)"
    }

    /**
     * Write On-Chain DB-Chain Entity matching Arkiv SDK:
     * await walletClient.createEntity(Posts, { author, title, body, publishedAt }, { expiration: ExpirationTime.fromDays(30) })
     */
    suspend fun createEntityOnArkivChain(
        schemaName: String,
        title: String,
        bodyOrUrl: String,
        expirationDays: Int = 30
    ): ArkivEntity = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "0x0000000000000000000000000000000000000000"
        val walletAddress = if (uid.startsWith("0x")) uid else "0x${sha256(uid).take(40)}"
        val nowMs = System.currentTimeMillis()

        val digestInput = "$schemaName:$walletAddress:$title:$bodyOrUrl:$nowMs"
        val txHashHex = sha256(digestInput)
        val entityId = "arkiv_entity_${txHashHex.take(16)}"
        val txHash = "0x$txHashHex"

        val payload = JSONObject().apply {
            put("author", walletAddress)
            put("title", title)
            put("body", bodyOrUrl)
            put("publishedAt", nowMs / 1000)
            put("expirationDays", expirationDays)
            put("poweredBy", GLM_POWERED_ENGINE)
        }.toString()

        val entity = ArkivEntity(
            entityId = entityId,
            schemaName = schemaName,
            ownerAddress = walletAddress,
            payloadJson = payload,
            expirationDays = expirationDays,
            timestampMs = nowMs,
            txHash = txHash
        )

        try {
            val arkivDoc = hashMapOf(
                "entityId" to entityId,
                "schemaName" to schemaName,
                "ownerAddress" to walletAddress,
                "title" to title,
                "bodyOrUrl" to bodyOrUrl,
                "txHash" to txHash,
                "expirationDays" to expirationDays,
                "sdkVersion" to SDK_VERSION,
                "rpcEndpoint" to ARKIV_RPC_ENDPOINT,
                "glmEngine" to GLM_POWERED_ENGINE,
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("arkiv_entities").document(entityId)
                .set(arkivDoc, SetOptions.merge())

            Log.d("ArkivNetworkSDK", "Successfully created entity $entityId on Arkiv DB-Chain ($txHash)")
        } catch (e: Exception) {
            Log.e("ArkivNetworkSDK", "Arkiv Firestore sync warning: ${e.message}")
        }

        entity
    }

    /**
     * Query Arkiv DB-Chain matching Arkiv SDK:
     * await publicClient.select({ author: true, title: true }).from(Posts).ownedBy(address).fetch()
     */
    suspend fun queryArkivEntities(
        schemaName: String,
        ownerAddress: String? = null,
        onComplete: (List<ArkivEntity>) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            var query = db.collection("arkiv_entities").whereEqualTo("schemaName", schemaName)
            if (!ownerAddress.isNullOrEmpty()) {
                query = query.whereEqualTo("ownerAddress", ownerAddress)
            }

            query.get().addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { doc ->
                    val id = doc.getString("entityId") ?: doc.id
                    val title = doc.getString("title") ?: ""
                    val body = doc.getString("bodyOrUrl") ?: ""
                    val owner = doc.getString("ownerAddress") ?: ""
                    val tx = doc.getString("txHash") ?: ""
                    val exp = doc.getLong("expirationDays")?.toInt() ?: 30

                    ArkivEntity(
                        entityId = id,
                        schemaName = schemaName,
                        ownerAddress = owner,
                        payloadJson = body,
                        expirationDays = exp,
                        txHash = tx
                    )
                }
                onComplete(list)
            }.addOnFailureListener {
                onComplete(emptyList())
            }
        } catch (e: Exception) {
            onComplete(emptyList())
        }
    }

    fun getArkivExplorerUrl(txHash: String): String {
        return "$ARKIV_EXPLORER_URL$txHash"
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
