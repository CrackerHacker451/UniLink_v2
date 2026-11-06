package com.example.unilink.worldid

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Client-side view of the human gate. The REAL enforcement is in firestore.rules / storage.rules,
 * which require the `humanVerified` custom claim. That claim can only be set by the server
 * (functions/gate.js) after World ID itself confirmed a proof, so nothing in this app can grant it.
 */
object HumanGate {
    const val CLAIM = "humanVerified"
    private const val PREFS_NAME = "human_gate_skip_prefs"
    private const val KEY_SKIP_UNTIL = "skip_until_ms"

    /** True only if the signed-in user's ID token carries the server-issued claim or Firestore record is verified. Fail closed. */
    suspend fun isVerified(forceRefresh: Boolean = false): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(forceRefresh).addOnCompleteListener { task ->
                val claimVerified = task.isSuccessful && task.result?.claims?.get(CLAIM) == true
                if (claimVerified) {
                    if (cont.isActive) cont.resume(true)
                } else {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(user.uid).get()
                        .addOnSuccessListener { doc ->
                            val docVerified = doc.exists() && doc.getBoolean("worldIdVerified") == true
                            if (cont.isActive) cont.resume(docVerified)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(false)
                        }
                }
            }
        }
    }

    fun snooze24Hours(context: android.content.Context) {
        val skipUntil = System.currentTimeMillis() + 86400000L // 24 Hours
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SKIP_UNTIL, skipUntil)
            .apply()
    }

    fun isSnoozedActive(context: android.content.Context): Boolean {
        val skipUntil = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getLong(KEY_SKIP_UNTIL, 0L)
        return System.currentTimeMillis() < skipUntil
    }
}
