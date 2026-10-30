package com.example.unilink.chainlink

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CreWorkflowTriggerRequest(
    val projectId: String,
    val projectTitle: String,
    val authorUid: String
)

data class CreWorkflowTriggerResponse(
    val status: String, // "SIMULATION_SUCCESS"
    val creExecutionId: String,
    val aiCodeScore: Int,
    val sybilScore: Int,
    val grantWeight: String,
    val grantAmountUsdc: Int,
    val evmTxHash: String,
    val nftBadgeMinted: String,
    val timestamp: Long
)

class ChainlinkCreService {

    private val db = FirebaseFirestore.getInstance()

    suspend fun triggerCreWorkflow(request: CreWorkflowTriggerRequest): CreWorkflowTriggerResponse = withContext(Dispatchers.IO) {
        val execId = "cre_exec_${System.currentTimeMillis()}"
        val txHash = "0x" + (1..64).map { "0123456789abcdef".random() }.joinToString("")
        val badge = "Chainlink CRE Verified Builder Badge"

        val response = CreWorkflowTriggerResponse(
            status = "SIMULATION_SUCCESS",
            creExecutionId = execId,
            aiCodeScore = 92,
            sybilScore = 1,
            grantWeight = "1.0x",
            grantAmountUsdc = 500,
            evmTxHash = txHash,
            nftBadgeMinted = badge,
            timestamp = System.currentTimeMillis()
        )

        // Record CRE Workflow Execution in Firestore for Newton's Lobby
        val record = hashMapOf(
            "creExecutionId" to execId,
            "projectId" to request.projectId,
            "projectTitle" to request.projectTitle,
            "authorUid" to request.authorUid,
            "status" to "SIMULATION_SUCCESS",
            "aiCodeScore" to 92,
            "sybilScore" to 1,
            "grantWeight" to "1.0x",
            "grantAmountUsdc" to 500,
            "evmTxHash" to txHash,
            "nftBadgeMinted" to badge,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("projects").document(request.projectId)
            .collection("cre_executions").document(execId)
            .set(record, SetOptions.merge())

        response
    }
}
