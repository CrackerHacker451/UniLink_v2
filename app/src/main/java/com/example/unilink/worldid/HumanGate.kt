package com.example.unilink.worldid

import com.google.firebase.auth.FirebaseAuth

object HumanGate {
    const val CLAIM = "humanVerified"

    @Suppress("UNUSED_PARAMETER")
    fun isVerified(forceRefresh: Boolean = false): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }
}
