package com.example.unilink.trust

import android.content.Context
import android.util.Log
import com.example.unilink.worldid.HumanGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorldTrustManager(private val context: Context) {

    private val repository = WorldTrustRepository()

    /**
     * The reward requires a REAL, server-verified human (the `humanVerified` claim).
     * There is no simulated scan here any more, and no path that grants the reward on an error.
     */
    suspend fun executeRewardHumanProofFlow(
        projectId: String = WorldTrustRepository.REWARD_ACTION,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): RewardClaimResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Checking your World ID verification...", 0.3f)
            if (!HumanGate.isVerified(forceRefresh = true)) {
                return@withContext RewardClaimResult(false, 0, false, "Verify with World ID first to claim this reward.")
            }
            onProgress("Granting Day 7 Reward...", 1.0f)
            repository.claimDay7Reward()
        } catch (e: Exception) {
            Log.e("WorldTrustManager", "Reward flow error: ${e.message}")
            RewardClaimResult(false, 0, false, "Could not complete the reward claim. Please try again.")
        }
    }
}
