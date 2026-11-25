package com.example.unilink.worldid

import com.google.firebase.auth.FirebaseAuth

object HumanGate {
    const val CLAIM = "humanVerified"
    private const val PREFS_NAME = "human_gate_snooze_prefs"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_ms"

    @Suppress("UNUSED_PARAMETER")
    fun isVerified(forceRefresh: Boolean = false): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
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
