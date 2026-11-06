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

    /** True only if the signed-in user's ID token carries the server-issued claim. Any error => false (fail closed). */
    suspend fun isVerified(forceRefresh: Boolean = false): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(forceRefresh).addOnCompleteListener { task ->
                val ok = task.isSuccessful && task.result?.claims?.get(CLAIM) == true
                if (cont.isActive) cont.resume(ok)
            }
        }
    }
}
