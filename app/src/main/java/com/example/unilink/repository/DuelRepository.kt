package com.example.unilink.repository

import com.example.unilink.models.DuelPeer
import com.example.unilink.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class DuelRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getOnlinePeers(onResult: (List<DuelPeer>) -> Unit): ListenerRegistration {
        val currentUid = auth.currentUser?.uid
        return db.collection("users")
            .whereEqualTo("isOnline", true)
            .limit(10)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onResult(emptyList())
                    return@addSnapshotListener
                }
                
                val peers = snapshot?.documents?.filter { it.id != currentUid }?.map { doc ->
                    DuelPeer(
                        id = doc.id,
                        name = doc.getString("name") ?: "Student",
                        major = doc.getString("branch") ?: "Student",
                        initial = (doc.getString("name") ?: "S").take(1).uppercase()
                    )
                } ?: emptyList()
                onResult(peers)
            }
    }
}
