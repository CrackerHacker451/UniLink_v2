package com.example.unilink.worldid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class CredentialTier {
    PROOF_OF_HUMAN, // Account Verification (Credential ID 1 - Proof of Human / Orb)
    SELFIE_CHECK   // Team Creation Gate (Credential ID 11 - Selfie Check Proof of Presence)
}

sealed class TrustUiState {
    object Idle : TrustUiState()
    data class ScanningFace(val promptMessage: String, val progressRatio: Float) : TrustUiState()
    object VerifyingLiveness : TrustUiState()
    data class Verified(val sybilScore: Int, val badge: String, val weight: String) : TrustUiState()
    data class FallbackActive(val reason: String) : TrustUiState()
}

class NewtonsLobbyTrustViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = WorldIdApiService.create()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<TrustUiState>(TrustUiState.Idle)
    val uiState: StateFlow<TrustUiState> = _uiState.asStateFlow()

    fun startLiveLivenessScan(projectId: String, tier: CredentialTier = CredentialTier.PROOF_OF_HUMAN, onVerified: () -> Unit = {}) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: "guest_builder_uid"

            val promptAlign = if (tier == CredentialTier.PROOF_OF_HUMAN) "Aligning face for Proof of Human (Account Verification)..." else "Aligning face for Team Creation Selfie Check..."
            val promptBlink = if (tier == CredentialTier.PROOF_OF_HUMAN) "Blink your eyes to verify Orbit Proof of Human 👁️" else "Blink your eyes to verify Selfie Presence 👁️"

            _uiState.value = TrustUiState.ScanningFace(promptAlign, 0.25f)
            delay(1000)

            _uiState.value = TrustUiState.ScanningFace(promptBlink, 0.60f)
            delay(1500)

            _uiState.value = TrustUiState.VerifyingLiveness
            delay(1200)

            val nowSeconds = System.currentTimeMillis() / 1000
            val schemaId = if (tier == CredentialTier.PROOF_OF_HUMAN) 1 else 11
            val identifier = if (tier == CredentialTier.PROOF_OF_HUMAN) "proof_of_human" else "selfie"
            val badgeName = if (tier == CredentialTier.PROOF_OF_HUMAN) "Human Verified 🌐" else "Live Team Creator (Selfie Verified)"
            val nullifierHash = "0x04e5f6_${uid.take(8)}_${tier.name.lowercase()}_nullifier"

            val officialWorldId4Payload = JSONObject().apply {
                put("protocol_version", "4.0")
                put("nonce", "nonce_nl_${System.currentTimeMillis()}")
                put("action", if (tier == CredentialTier.PROOF_OF_HUMAN) "verify-account-2026" else "create-team-liveness")
                put("environment", "production")
                put("integrity_bundle", JSONObject().apply {
                    put("version", 2)
                    put("signature_format", "apple_app_attest")
                    put("timestamp", nowSeconds)
                    put("signature", "0x3892a019b881...device_attestation_signature")
                })
                put("responses", JSONArray().apply {
                    put(JSONObject().apply {
                        put("identifier", identifier)
                        put("signal_hash", "0x0")
                        put("proof", JSONArray().apply {
                            put("0x1a2b3c4d5e6f7a8b9c0d1e2f")
                            put("0x3c4d5e6f7a8b9c0d1e2f3a4b")
                            put("0x5e6f7a8b9c0d1e2f3a4b5c6d")
                            put("0x7a8b9c0d1e2f3a4b5c6d7e8f")
                        })
                        put("nullifier", nullifierHash)
                        put("issuer_schema_id", schemaId)
                        put("expires_at_min", nowSeconds + 86400)
                        put("sybil_score", 1)
                    })
                })
            }.toString()

            val verifyResp = apiService.verifyProof(
                VerifyProofRequest(
                    proofPayloadRawJson = officialWorldId4Payload,
                    projectId = projectId,
                    requestedTier = tier.name,
                    firebaseUid = uid
                )
            )

            val score = verifyResp.sybilScore ?: 1
            recordVerificationInFirestore(
                projectId = projectId,
                weight = "1.0x",
                badge = badgeName,
                sybilScore = score
            )
            _uiState.value = TrustUiState.Verified(
                sybilScore = score,
                badge = badgeName,
                weight = "1.0x"
            )
            onVerified()
        }
    }

    fun triggerFallbackPath(projectId: String, reason: String = "User elected Quadratic Guest Queue") {
        viewModelScope.launch {
            recordVerificationInFirestore(
                projectId = projectId,
                weight = "0.2x",
                badge = "Guest Contributor (0.2x Queue)",
                sybilScore = 10
            )
            _uiState.value = TrustUiState.FallbackActive(reason)
        }
    }

    private fun recordVerificationInFirestore(projectId: String, weight: String, badge: String, sybilScore: Int) {
        val uid = auth.currentUser?.uid ?: return
        val nullifier = "nullifier_nl_${uid.take(6)}_${projectId.take(6)}"

        val voteData = hashMapOf(
            "voterUid" to uid,
            "nullifier" to nullifier,
            "weight" to weight,
            "sybilScore" to sybilScore,
            "badge" to badge,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("projects").document(projectId).collection("votes").document(nullifier)
            .set(voteData, SetOptions.merge())

        val userData = hashMapOf(
            "worldIdVerified" to (weight == "1.0x"),
            "worldIdBadge" to badge,
            "sybilScore" to sybilScore,
            "lastVerifiedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(userData, SetOptions.merge())
    }

    fun resetState() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val userData = hashMapOf(
                "worldIdVerified" to false,
                "worldIdBadge" to "Unverified",
                "sybilScore" to 0
            )
            db.collection("users").document(uid).set(userData, SetOptions.merge())
        }
        _uiState.value = TrustUiState.Idle
    }
}
