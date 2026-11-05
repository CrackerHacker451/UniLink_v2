package com.example.unilink.fragments

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.R
import com.example.unilink.activities.*
import com.example.unilink.adapters.*
import com.example.unilink.databinding.FragmentHomeBinding
import com.example.unilink.models.*
import com.example.unilink.repository.*
import com.example.unilink.utils.HapticUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val liveFeedRepository = LiveFeedRepository()
    private val userRepository = UserRepository()
    private val doubtRepository = DoubtRepository()
    private val duelRepository = DuelRepository()
    private val chatRepository = ChatRepository()
    private val notificationRepository = NotificationRepository()

    private var userListener: ListenerRegistration? = null
    private var feedListener: ListenerRegistration? = null
    private var storiesListener: ListenerRegistration? = null
    private var doubtsListener: ListenerRegistration? = null
    private var duelsListener: ListenerRegistration? = null
    private var notificationListener: ListenerRegistration? = null

    private lateinit var postAdapter: PostAdapter
    private lateinit var homeDoubtAdapter: DoubtAdapter
    private lateinit var storyAdapter: StoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userRepository.getCurrentUser { }

        ViewCompat.setOnApplyWindowInsetsListener(binding.swipeRefreshHome) { _, insets ->
            binding.swipeRefreshHome.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }

        setupAdapters()
        setupClickListeners()
        fetchFeed()
        setupRefreshLayout()
        observeNotifications()
    }

    private fun setupAdapters() {
        postAdapter = PostAdapter()
        binding.rvActivityFeed.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
            isNestedScrollingEnabled = false
            setItemViewCacheSize(25)
        }
    }

    private fun setupRefreshLayout() {
        binding.swipeRefreshHome.apply {
            setOnRefreshListener {
                fetchFeed()
                postDelayed({ _binding?.swipeRefreshHome?.isRefreshing = false }, 1200)
            }
            setColorSchemeResources(R.color.cyan_brand, R.color.purple_brand)
        }
    }

    private fun observeNotifications() {
        notificationListener?.remove()
        notificationListener = notificationRepository.observeNotifications { notifications ->
            val unreadCount = notifications.count { !it.isRead }
            _binding?.tvNotificationCount?.apply {
                isVisible = unreadCount > 0
                text = if (unreadCount > 9) "9+" else unreadCount.toString()
            }
        }
    }

    private fun formatCompactCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    private fun setupUI() {
        userListener?.remove()
        userListener = userRepository.observeCurrentUser { user ->
            _binding?.apply {
                user?.let {
                    tvHeaderUniCoins.text = formatCompactCount(it.uniCoins)
                    tvHeaderStreak.text = it.streakDays.toString()
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnDashboardMenu.setOnClickListener {
            HapticUtils.playSelection(it)
            showCampusDashboardSheet()
        }

        binding.btnNotifications.setOnClickListener { navigateTo(NotificationsActivity::class.java, it) }
        binding.btnLeaderboard.setOnClickListener { navigateTo(LeaderboardActivity::class.java, it) }
        binding.cardHeaderStats.setOnClickListener { HapticUtils.playSelection(it); showStreakModal() }
    }

    private fun showCampusDashboardSheet() {
        val ctx = context ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_campus_dashboard, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        dialog.setContentView(dialogView)

        dialogView.findViewById<View>(R.id.btnCloseDashboard)?.setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<View>(R.id.cardDashSpeedLink)?.setOnClickListener {
            HapticUtils.playSelection(it)
            dialog.dismiss()
            startActivity(Intent(ctx, RandomMatchActivity::class.java))
        }

        dialogView.findViewById<View>(R.id.cardDashRadar)?.setOnClickListener {
            HapticUtils.playSelection(it)
            dialog.dismiss()
            startActivity(Intent(ctx, NearbyStudentsActivity::class.java))
        }

        dialogView.findViewById<View>(R.id.cardDashArena)?.setOnClickListener {
            HapticUtils.playSelection(it)
            dialog.dismiss()
            startActivity(Intent(ctx, com.example.unilink.activities.CampusArenaActivity::class.java))
        }

        dialogView.findViewById<View>(R.id.cardDashBroadcast)?.setOnClickListener {
            HapticUtils.playSelection(it)
            dialog.dismiss()
            startActivity(Intent(ctx, LiveStreamActivity::class.java))
        }

        dialogView.findViewById<View>(R.id.cardDashBarter)?.setOnClickListener {
            HapticUtils.playSelection(it)
            dialog.dismiss()
            startActivity(Intent(ctx, BarterBlockActivity::class.java))
        }

        dialogView.findViewById<View>(R.id.cardDashDoubts)?.setOnClickListener {
            HapticUtils.playSelection(it)
            dialog.dismiss()
            startActivity(Intent(ctx, DoubtBoardActivity::class.java))
        }

        dialogView.findViewById<View>(R.id.cardDashFocus)?.setOnClickListener {
            HapticUtils.playSelection(it)
            dialog.dismiss()
            startActivity(Intent(ctx, FocusRoomActivity::class.java))
        }

        dialogView.findViewById<View>(R.id.cardDashVault)?.setOnClickListener {
            HapticUtils.playSelection(it)
            dialog.dismiss()
            startActivity(Intent(ctx, VaultActivity::class.java))
        }

        dialog.show()
    }

    private fun showComposeBottomSheet(content: @androidx.compose.runtime.Composable (com.google.android.material.bottomsheet.BottomSheetDialog) -> Unit) {
        val ctx = context ?: return
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        val composeView = ComposeView(ctx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                content(dialog)
            }
        }
        dialog.setContentView(composeView)

        dialog.findViewById<View>(com.google.android.material.R.id.container)?.let { container ->
            container.setViewTreeLifecycleOwner(viewLifecycleOwner)
            container.setViewTreeViewModelStoreOwner(this)
            container.setViewTreeSavedStateRegistryOwner(this)
        }

        dialog.show()
    }

    private fun showDay7ProofOfHumanGate(onVerified: () -> Unit) {
        val act = activity ?: return
        showComposeBottomSheet { dialog ->
            val viewModel = com.example.unilink.worldid.NewtonsLobbyTrustViewModel(act.application)
            viewModel.resetState()
            com.example.unilink.worldid.NewtonsLobbyTrustSheet(
                projectId = "day7_streak_${System.currentTimeMillis()}",
                projectTitle = "Day 7 Streak Gate (+150 UniCoins)",
                tier = com.example.unilink.worldid.CredentialTier.PROOF_OF_HUMAN,
                viewModel = viewModel,
                onVerifiedSuccess = {
                    dialog.dismiss()
                    onVerified()
                },
                onClose = { dialog.dismiss() }
            )
        }
    }

    private fun showStreakModal() {
        val ctx = context ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_streak_rewards, null)
        val dialog = AlertDialog.Builder(ctx).setView(dialogView).create()

        val btnClose = dialogView.findViewById<TextView>(R.id.btnCloseStreak)
        val btnClaim = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClaimStreakBonus)

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnClaim?.setOnClickListener { v ->
            HapticUtils.playSuccess(v)
            showDay7ProofOfHumanGate {
                userRepository.addUniCoins(150) { success ->
                    if (success) {
                        Toast.makeText(ctx, "🔥 Day 7 Streak Claimed! Human Verified! +150 UniCoins Added! 🌐", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showProPassDialog() {
        val ctx = context ?: return
        val dialog = AlertDialog.Builder(ctx).create()
        val dialogBinding = com.example.unilink.databinding.DialogPremiumPurchaseBinding.inflate(layoutInflater)
        dialog.setView(dialogBinding.root)

        dialogBinding.btnNextStep.setOnClickListener {
            dialogBinding.llPaymentStep.visibility = View.GONE
            dialogBinding.llVerificationStep.visibility = View.VISIBLE
        }

        dialogBinding.btnBackToPay.setOnClickListener {
            dialogBinding.llVerificationStep.visibility = View.GONE
            dialogBinding.llPaymentStep.visibility = View.VISIBLE
        }

        dialogBinding.btnSubmitVerification.setOnClickListener { v ->
            HapticUtils.playSuccess(v)
            userRepository.redeemPremiumCode("UNILINKPRO") { success, msg ->
                if (success) {
                    Toast.makeText(ctx, "👑 Pro Pass Verified & Activated! +500 UniCoins Added!", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(ctx, msg ?: "Verification failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialogBinding.btnApplyPromo.setOnClickListener { v ->
            HapticUtils.playSuccess(v)
            val code = dialogBinding.etPromoCode.text.toString().trim().ifEmpty { "UNILINKPRO" }
            userRepository.redeemPremiumCode(code) { success, msg ->
                Toast.makeText(ctx, msg ?: if (success) "Pro Pass Unlocked! ✨" else "Invalid Code", Toast.LENGTH_SHORT).show()
                if (success) dialog.dismiss()
            }
        }

        dialogBinding.btnHaveCode.setOnClickListener {
            dialogBinding.llPromoArea.visibility = View.VISIBLE
        }

        dialog.show()
    }

    private fun navigateTo(clazz: Class<*>, view: View? = null) {
        view?.let { HapticUtils.playSelection(it) }
        startActivity(Intent(context, clazz))
    }

    private fun fetchFeed() {
        feedListener?.remove()
        feedListener = liveFeedRepository.observeCampusPulse { posts ->
            _binding?.let { binding ->
                binding.rvActivityFeed.visibility = View.VISIBLE
                postAdapter.submitList(posts) {
                    try { binding.rvActivityFeed.requestLayout() } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        feedListener?.remove()
        notificationListener?.remove()
        _binding = null
    }
}
