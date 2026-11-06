package com.example.unilink.trust

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * World ID verification itself now lives on the server (functions/gate.js). This class only handles
 * the Day 7 reward, and only reports success when the Firestore transaction really succeeded.
 *
 * NOTE: the reward still changes uniCoins from the client. Move it into a Cloud Function next.
 */
class WorldTrustRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        const val REWARD_ACTION = "newton-streak-day7-v1"
        private const val REWARD_COINS = 150L
    }

    suspend fun claimDay7Reward(): RewardClaimResult = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext RewardClaimResult(false, 0, false, "Unauthenticated")
        val cycleId = "2026-W42"
        val claimId = sha256("$uid:$REWARD_ACTION:$cycleId")

        try {
            db.runTransaction { tx ->
                val claimRef = db.collection("day7RewardClaims").document(claimId)
                if (tx.get(claimRef).exists()) {
                    throw IllegalStateException("already_claimed")
                }
                tx.set(claimRef, hashMapOf(
                    "uid" to uid,
                    "cycleId" to cycleId,
                    "coins" to REWARD_COINS,
                    "claimedAt" to FieldValue.serverTimestamp()
                ))
                tx.update(db.collection("users").document(uid), mapOf(
                    "uniCoins" to FieldValue.increment(REWARD_COINS),
                    "isPro" to true,
                    "proActivatedAt" to FieldValue.serverTimestamp()
                ))
                null
            }.awaitResult()
            RewardClaimResult(true, REWARD_COINS.toInt(), true, "Day 7 reward granted: +$REWARD_COINS UniCoins and Pro Pass.")
        } catch (e: Exception) {
            val already = e.message?.contains("already_claimed") == true
            RewardClaimResult(false, 0, false, if (already) "You already claimed this reward." else "Could not claim the reward. Please try again.")
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { t ->
            if (!cont.isActive) return@addOnCompleteListener
            if (t.isSuccessful) cont.resume(t.result) else cont.resumeWithException(t.exception ?: IllegalStateException("task_failed"))
        }
    }

    private fun sha256(input: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
