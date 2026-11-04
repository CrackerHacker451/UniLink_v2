package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import com.example.unilink.databinding.ActivityRewardWheelBinding
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.ThemeUtils

class RewardWheelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRewardWheelBinding
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityRewardWheelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSpinWheel.setOnClickListener { v ->
            HapticUtils.playSelection(v)
            showWorldIdRewardGate()
        }
    }

    private fun showComposeBottomSheet(content: @androidx.compose.runtime.Composable (com.google.android.material.bottomsheet.BottomSheetDialog) -> Unit) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                content(dialog)
            }
        }
        dialog.setContentView(composeView)

        dialog.findViewById<android.view.View>(com.google.android.material.R.id.container)?.let { container ->
            container.setViewTreeLifecycleOwner(this)
            container.setViewTreeViewModelStoreOwner(this)
            container.setViewTreeSavedStateRegistryOwner(this)
        }

        dialog.show()
    }

    private fun showWorldIdRewardGate() {
        showComposeBottomSheet { dialog ->
            val viewModel = com.example.unilink.worldid.NewtonsLobbyTrustViewModel(application)
            viewModel.resetState()
            com.example.unilink.worldid.NewtonsLobbyTrustSheet(
                projectId = com.example.unilink.trust.WorldTrustRepository.REWARD_ACTION,
                projectTitle = "Day 7 Streak Gate (+150 UniCoins)",
                tier = com.example.unilink.worldid.CredentialTier.PROOF_OF_HUMAN,
                viewModel = viewModel,
                onVerifiedSuccess = {
                    dialog.dismiss()
                    claimReward()
                },
                onClose = { dialog.dismiss() }
            )
        }
    }

    private fun claimReward() {
        binding.btnSpinWheel.isEnabled = false
        lifecycleScope.launch {
            val trustManager = com.example.unilink.trust.WorldTrustManager(this@RewardWheelActivity)
            val result = trustManager.executeRewardHumanProofFlow()
            if (result.success) {
                HapticUtils.playSuccess(binding.btnSpinWheel)
                Toast.makeText(this@RewardWheelActivity, result.message ?: "🔥 Day 7 Streak Reward Granted! +150 UniCoins & Pro Pass Active!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                binding.btnSpinWheel.isEnabled = true
                Toast.makeText(this@RewardWheelActivity, result.message ?: "Failed to claim reward. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
