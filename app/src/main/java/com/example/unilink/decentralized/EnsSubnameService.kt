package com.example.unilink.decentralized

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EnsSubnameRecord(
    val studentSubname: String, // e.g. alice.campus.newtonslobby.eth
    val aiCompanionSubname: String, // e.g. newton.alice.campus.newtonslobby.eth
    val ensip25Endpoints: List<String>,
    val ensip26ToolCapabilities: List<String>,
    val paymentAddressHts: String
)

class EnsSubnameService {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun registerCampusSubname(username: String, college: String = "campus"): EnsSubnameRecord = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "guest_uid"
        val cleanUser = username.lowercase().replace(" ", "")
        val studentSub = "$cleanUser.$college.newtonslobby.eth"
        val aiSub = "newton.$cleanUser.$college.newtonslobby.eth"

        val record = EnsSubnameRecord(
            studentSubname = studentSub,
            aiCompanionSubname = aiSub,
            ensip25Endpoints = listOf(
                "https://newtonslobby.app/api/ensip25/$studentSub",
                "https://newtonslobby.app/api/ensip25/$aiSub"
            ),
            ensip26ToolCapabilities = listOf(
                "ENSIP-26:CODE_AUDIT",
                "ENSIP-26:WORLD_ID_AGENT_PASS",
                "ENSIP-26:HEDERA_X402_MICROPAYMENT"
            ),
            paymentAddressHts = "0.0.108920521"
        )

        val firestoreData = hashMapOf(
            "ensSubname" to studentSub,
            "ensAiCompanionSubname" to aiSub,
            "ensip25Endpoints" to record.ensip25Endpoints,
            "ensip26Capabilities" to record.ensip26ToolCapabilities,
            "htsPaymentAddress" to record.paymentAddressHts,
            "registeredAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(uid).set(firestoreData, SetOptions.merge())

        record
    }
}
