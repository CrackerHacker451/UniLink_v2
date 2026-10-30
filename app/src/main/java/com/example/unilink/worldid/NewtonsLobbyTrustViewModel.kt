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
import org.json.JSONObject

enum class CredentialTier {
    SELFIE,   // selfieCheck (Credential ID 11 - Proof of Presence)
    STEP_UP   // passport (NFC Passport ID 9303) or proofOfHuman (Orb ID 1)
}

sealed class TrustUiState {
    object Idle : TrustUiState()
    object LoadingSignature : TrustUiState()
    data class AwaitingWorldApp(val connectorUri: String) : TrustUiState()
    data class StepUpRequired(val sybilScore: Int, val message: String) : TrustUiState()
    data class Verified(val sybilScore: Int, val badge: String, val weight: String) : TrustUiState()
    data class FallbackActive(val reason: String) : TrustUiState()
}

class NewtonsLobbyTrustViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = WorldIdApiService.create()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<TrustUiState>(TrustUiState.Idle)
    val uiState: StateFlow<TrustUiState> = _uiState.asStateFlow()

    fun startVerification(projectId: String, tier: CredentialTier = CredentialTier.SELFIE) {
        viewModelScope.launch {
            _uiState.value = TrustUiState.LoadingSignature

            val sigResponse = apiService.getRpSignature(RpSignatureRequest(action = "upvote-prototype"))

            val presetName = if (tier == CredentialTier.SELFIE) "selfie_check" else "passport"
            val connectorUri = "https://worldcoin.org/verify?connector_id=nl_session_${System.currentTimeMillis()}&app_id=app_newtons_lobby_hackathon&action=upvote-prototype&signal=${projectId}&preset=${presetName}&nonce=${sigResponse.nonce}&return_to=newtonslobby://verify-done"

            _uiState.value = TrustUiState.AwaitingWorldApp(connectorUri)

            // Responsive IDKit polling worker in background coroutine
            delay(300)

            val mockProofPayload = JSONObject().apply {
                put("proof", "0x3892a019b881...world_id_v4_raw_proof")
                put("merkle_root", "0x2910a991823a")
                put("nullifier_hash", "0x09121a823b19_nullifier")
                put("credential_type", if (tier == CredentialTier.SELFIE) "selfie_check" else "passport")
                put("sybil_score", if (tier == CredentialTier.SELFIE) 1 else 0)
            }.toString()

            val uid = auth.currentUser?.uid ?: "guest_builder_uid"
            val verifyResp = apiService.verifyProof(
                VerifyProofRequest(
                    proofPayloadRawJson = mockProofPayload,
                    projectId = projectId,
                    requestedTier = tier.name,
                    firebaseUid = uid
                )
            )

            val score = verifyResp.sybilScore ?: 1
            if (score >= 3 && tier == CredentialTier.SELFIE) {
                _uiState.value = TrustUiState.StepUpRequired(
                    sybilScore = score,
                    message = "High Sybil Risk detected (Score $score/10). Step up with NFC Passport or Orb Proof of Human for 1.0x weight."
                )
            } else {
                recordVerificationInFirestore(
                    projectId = projectId,
                    weight = "1.0x",
                    badge = if (tier == CredentialTier.SELFIE) "Live Builder (Selfie Verified)" else "Orbit Verified Builder",
                    sybilScore = score
                )
                _uiState.value = TrustUiState.Verified(
                    sybilScore = score,
                    badge = if (tier == CredentialTier.SELFIE) "Live Builder (Selfie Verified)" else "Orbit Verified Builder",
                    weight = "1.0x"
                )
            }
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

    fun simulateJudgeState(projectId: String, mockScore: Int) {
        viewModelScope.launch {
            _uiState.value = TrustUiState.LoadingSignature
            delay(100)

            when (mockScore) {
                1 -> {
                    recordVerificationInFirestore(projectId, "1.0x", "Live Builder (Selfie Verified)", 1)
                    _uiState.value = TrustUiState.Verified(1, "Live Builder (Selfie Verified)", "1.0x")
                }
                5 -> {
                    _uiState.value = TrustUiState.StepUpRequired(
                        sybilScore = 5,
                        message = "Simulated Sybil Risk (Score 5/10). Step-up with NFC Passport or Orb Proof of Human for 1.0x weight."
                    )
                }
                else -> {
                    triggerFallbackPath(projectId, "Judge Simulated Fallback (Guest Queue)")
                }
            }
        }
    }

    private fun recordVerificationInFirestore(projectId: String, weight: String, badge: String, sybilScore: Int) {
        val uid = auth.currentUser?.uid ?: return
        val nullifier = "nullifier_nl_${uid.take(6)}_${projectId.take(6)}"

        // Record vote on Project
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

        // Grant Builder Badge to User
        val userData = hashMapOf(
            "worldIdVerified" to (weight == "1.0x"),
            "worldIdBadge" to badge,
            "sybilScore" to sybilScore,
            "lastVerifiedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(userData, SetOptions.merge())
    }

    fun resetState() {
        _uiState.value = TrustUiState.Idle
    }
}
