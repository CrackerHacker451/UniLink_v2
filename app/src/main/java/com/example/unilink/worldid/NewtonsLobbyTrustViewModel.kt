package com.example.unilink.worldid

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CredentialTier {
    PROOF_OF_HUMAN,
    SELFIE_CHECK
}

sealed class TrustUiState {
    object Idle : TrustUiState()
    object Checking : TrustUiState()
    data class Verified(val badge: String) : TrustUiState()
    data class Failed(val message: String) : TrustUiState()
}

/**
 * Honest verification state. The result is whatever the SERVER says (the `humanVerified` claim);
 * there is no simulated scan, no simulated proof and no client-side way to mark a user verified.
 */
class NewtonsLobbyTrustViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<TrustUiState>(TrustUiState.Idle)
    val uiState: StateFlow<TrustUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = if (HumanGate.isVerified(forceRefresh = true)) TrustUiState.Verified(VERIFIED_BADGE) else TrustUiState.Idle
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun startLiveLivenessScan(projectId: String, tier: CredentialTier = CredentialTier.PROOF_OF_HUMAN, onVerified: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = TrustUiState.Checking
            if (HumanGate.isVerified(forceRefresh = true)) {
                _uiState.value = TrustUiState.Verified(VERIFIED_BADGE)
                onVerified()
                return@launch
            }
            val app = getApplication<Application>()
            try {
                app.startActivity(Intent(app, HumanVerificationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                _uiState.value = TrustUiState.Idle
            } catch (e: Exception) {
                _uiState.value = TrustUiState.Failed("Could not open verification. Please try again.")
            }
        }
    }

    /** Kept for existing call sites: it now just re-reads the real, server-issued state. */
    fun resetState() = refresh()

    companion object {
        const val REWARD_ACTION = "newton-streak-day7-v1"
        const val TEAM_ACTION = "newton-team-create-v1"
        private const val VERIFIED_BADGE = "Human Verified"
    }
}
