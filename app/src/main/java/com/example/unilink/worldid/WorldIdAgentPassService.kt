package com.example.unilink.worldid

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AgentPassRequest(
    val agentAction: String = "newton-agent-execute-v1",
    val appId: String = "app_ce59eafb4c64fb4dff9ac783c593702a",
    val requestedCredential: String = "orb",
    val userUid: String
)

data class AgentPassVerificationResult(
    val success: Boolean,
    val nullifierHash: String? = null,
    val agentActionAllowed: Boolean = false,
    val badgeGranted: String? = null,
    val errorMessage: String? = null
)

class WorldIdAgentPassService {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        const val AGENT_PASS_AUTH_URL = "https://auth.world.org/api/v1/request"
        const val AGENT_PASS_VERIFY_URL = "https://auth.world.org/v1/verify"
        const val WORLD_APP_ID = "app_ce59eafb4c64fb4dff9ac783c593702a"
    }

    /**
     * Request World ID Agent Pass authentication at the exact moment an agent action is triggered
     */
    suspend fun requestAgentPassVerification(
        context: Context,
        agentAction: String
    ): AgentPassVerificationResult = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext AgentPassVerificationResult(
            success = false,
            errorMessage = "User unauthenticated. Protected agent action blocked."
        )

        try {
            val requestId = "agent_pass_${System.currentTimeMillis()}"
            val nullifierHash = sha256("$uid:$agentAction:${WORLD_APP_ID}")

            val url = URL(AGENT_PASS_VERIFY_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val jsonBody = JSONObject().apply {
                put("app_id", WORLD_APP_ID)
                put("action", agentAction)
                put("signal", uid)
                put("credential_types", org.json.JSONArray().apply { put("orb") })
                put("nullifier_hash", nullifierHash)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val httpCode = conn.responseCode
            val isVerified = (httpCode == 200 || httpCode == 201)

            if (isVerified) {
                // Secure Backend Ledger Settlement
                val agentDoc = hashMapOf(
                    "requestId" to requestId,
                    "uid" to uid,
                    "agentAction" to agentAction,
                    "nullifierHash" to nullifierHash,
                    "verifiedAt" to FieldValue.serverTimestamp(),
                    "agentPassStatus" to "VERIFIED_SUCCESS"
                )

                db.collection("worldIdAgentPassLedger").document(requestId).set(agentDoc)
                db.collection("users").document(uid).update(
                    mapOf(
                        "worldIdBadge" to "Orb Verified 🌐",
                        "agentPassVerified" to true,
                        "lastAgentAction" to agentAction
                    )
                )

                Log.d("WorldIdAgentPass", "Successfully validated World ID Agent Pass for action: $agentAction")

                AgentPassVerificationResult(
                    success = true,
                    nullifierHash = nullifierHash,
                    agentActionAllowed = true,
                    badgeGranted = "Orb Verified 🌐",
                    errorMessage = null
                )
            } else {
                Log.w("WorldIdAgentPass", "Agent Pass verification denied with HTTP code $httpCode")
                AgentPassVerificationResult(
                    success = false,
                    agentActionAllowed = false,
                    errorMessage = "World ID Agent Pass verification denied or cancelled by user."
                )
            }
        } catch (e: Exception) {
            Log.e("WorldIdAgentPass", "Agent Pass error: ${e.message}")
            AgentPassVerificationResult(
                success = false,
                agentActionAllowed = false,
                errorMessage = "Verification error: ${e.message}. Protected action blocked."
            )
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
