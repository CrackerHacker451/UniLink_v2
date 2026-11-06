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
    private const val PREFS_NAME = "human_gate_snooze_prefs"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_ms"

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

    fun snooze24Hours(context: android.content.Context) {
        val snoozeUntil = System.currentTimeMillis() + 86400000L // 24 Hours
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SNOOZE_UNTIL, snoozeUntil)
            .apply()
    }

    fun isSnoozedActive(context: android.content.Context): Boolean {
        val snoozeUntil = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getLong(KEY_SNOOZE_UNTIL, 0L)
        return System.currentTimeMillis() < snoozeUntil
    }

    fun getSnoozeTimeRemainingHours(context: android.content.Context): Int {
        val snoozeUntil = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getLong(KEY_SNOOZE_UNTIL, 0L)
        val diffMs = snoozeUntil - System.currentTimeMillis()
        return if (diffMs > 0) (diffMs / 3600000L).toInt().coerceAtLeast(1) else 0
    }
}
