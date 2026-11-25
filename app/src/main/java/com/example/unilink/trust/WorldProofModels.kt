package com.example.unilink.trust

import com.google.firebase.Timestamp
import java.util.Date

data class BackendRpContext(
    val challengeId: String = "",
    val rpId: String = "rp_f622b6e137cc091b",
    val action: String = "unilink-human-gate-v1",
    val nonce: String = "",
    val createdAt: Long = System.currentTimeMillis() / 1000,
    val expiresAt: Long = (System.currentTimeMillis() / 1000) + 600,
    val signature: String = ""
)

data class IdKitResultPayload(
    val protocolVersion: String = "4.0",
    val nonce: String = "",
    val action: String = "newton-streak-day7-v1",
    val environment: String = "production",
    val nullifier: String = "",
    val proof: List<String> = emptyList(),
    val merkleRoot: String = "",
    val credentialType: String = "proof_of_human"
)

data class VerificationResult(
    val success: Boolean,
    val humanKey: String? = null,
    val badge: String? = null,
    val message: String? = null
)

data class RewardClaimResult(
    val success: Boolean,
    val coinsAwarded: Int = 150,
    val isProActivated: Boolean = true,
    val message: String? = null
)
