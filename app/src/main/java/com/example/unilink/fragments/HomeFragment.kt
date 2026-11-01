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
import androidx.core.view.ViewCompat
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
            binding.homeRootScroll.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }

        setupAdapters()
        setupUI()
        setupClickListeners()
        fetchFeed()
        fetchStories()
        setupDoubtBoard()
        setupSkillDuels()
        setupFocusRooms()
        setupDashboard()
        setupHighlights()
        updateCampusVibe()
        startFloatingAnimations()
        setupRefreshLayout()
        observeNotifications()

        binding.tvLivePulse.isSelected = true
    }

    private fun setupAdapters() {
        postAdapter = PostAdapter()
        binding.rvActivityFeed.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
            isNestedScrollingEnabled = false
            setItemViewCacheSize(25)
        }

        homeDoubtAdapter = DoubtAdapter(::answerDoubt)
        binding.rvDoubtBoard.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = homeDoubtAdapter
            isNestedScrollingEnabled = false
            setItemViewCacheSize(25)
        }

        storyAdapter = StoryAdapter()
        binding.rvStories.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = storyAdapter
        }
    }

    private fun setupDashboard() {
        val db = FirebaseFirestore.getInstance()

        db.collection("users").whereEqualTo("isOnline", true).get()
            .addOnSuccessListener { snap ->
                _binding?.let { animateCount(it.tvOnlineCount, if (snap.size() > 0) snap.size() else (12..48).random(), " Students") }
            }
            .addOnFailureListener {
                _binding?.let { animateCount(it.tvOnlineCount, (12..48).random(), " Students") }
            }

        db.collection("chats").whereEqualTo("isGroup", true).get()
            .addOnSuccessListener { snap ->
                _binding?.let { animateCount(it.tvActiveTeamsCount, if (snap.size() > 0) snap.size() else (4..12).random(), " Teams") }
            }
            .addOnFailureListener {
                _binding?.let { animateCount(it.tvActiveTeamsCount, (4..12).random(), " Teams") }
            }

        db.collection("vaults").get()
            .addOnSuccessListener { snap ->
                _binding?.let { animateCount(it.tvNewVaultsCount, if (snap.size() > 0) snap.size() else (8..20).random(), " Vaults") }
            }
            .addOnFailureListener {
                _binding?.let { animateCount(it.tvNewVaultsCount, (5..15).random(), " Vaults") }
            }
    }

    private fun animateCount(textView: TextView, target: Int, suffix: String) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 250
            addUpdateListener { anim ->
                _binding?.let { textView.text = "${anim.animatedValue}$suffix" }
            }
            start()
        }
    }

    private fun updateCampusVibe() {
        _binding?.tvLivePulse?.text = "Campus Pulse 📡"
    }

    private fun setupRefreshLayout() {
        binding.swipeRefreshHome.apply {
            setOnRefreshListener {
                fetchFeed()
                fetchStories()
                setupDoubtBoard()
                setupSkillDuels()
                setupDashboard()
                postDelayed({ _binding?.swipeRefreshHome?.isRefreshing = false }, 1500)
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

    private fun setupHighlights() {
        val highlights = listOf(
            CampusHighlight("system", "Aryan Sharma", "Earned 'Top Mentor' badge", true),
            CampusHighlight("system", "Sneha Reddy", "Shared new Java notes"),
            CampusHighlight("system", "Kurt", "Completed 'AI Assistant' project", true),
            CampusHighlight("system", "Meera", "Started a 10-day study streak"),
            CampusHighlight("system", "Lalit", "Boosted 'Aura Glass' theme", true)
        ).shuffled()

        binding.rvCampusHighlights.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = HighlightAdapter(highlights) { highlight ->
                if (highlight.userId != null && highlight.userId != "system") {
                    startActivity(Intent(context, ProfileActivity::class.java).putExtra("userId", highlight.userId))
                } else {
                    Toast.makeText(context, "${highlight.name} is a campus star! ⭐", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startFloatingAnimations() {
        binding.btnAiAssistant.clearAnimation()
        binding.ivLogo.clearAnimation()
        binding.livePulseBadge.visibility = View.VISIBLE
    }

    private fun setupUI() {
        userListener?.remove()
        userListener = userRepository.observeCurrentUser { user ->
            _binding?.apply {
                user?.let {
                    tvCollegeName.text = it.collegeName ?: "SRM University"
                    tvUniCoins.text = it.uniCoins.toString()
                    tvHeaderUniCoins.text = it.uniCoins.toString()
                    tvXpStreak.text = if (it.streakDays == 1) "🔥 1-day streak" else "🔥 ${it.streakDays}-day streak"

                    if (it.streakDays >= 3) {
                        tvXpStreak.setTextColor(Color.parseColor("#FACC15"))
                        tvXpStreak.startAnimation(AlphaAnimation(0.6f, 1.0f).apply {
                            duration = 1000
                            repeatMode = Animation.REVERSE
                            repeatCount = Animation.INFINITE
                        })
                    }
                    tvTotalXp.text = "Impact: ${it.impactScore}"
                    progressXp.progress = (it.totalXp % 1000) / 10
                }
            }
        }
    }

    private fun setupClickListeners() {
        // Campus Radar Hero Spotlight
        binding.cardHeroCampusRadar.setOnClickListener { navigateTo(NearbyStudentsActivity::class.java, it) }
        binding.btnScanCampusRadarHero.setOnClickListener { navigateTo(NearbyStudentsActivity::class.java, it) }

        // Random Peer Speed Match Hero Spotlight
        binding.cardHeroRandomMatch.setOnClickListener { navigateTo(RandomMatchActivity::class.java, it) }
        binding.btnStartRandomMatchHero.setOnClickListener { navigateTo(RandomMatchActivity::class.java, it) }

        binding.cardCampusArena.setOnClickListener { navigateTo(com.example.unilink.activities.CampusArenaActivity::class.java, it) }
        binding.btnEnterArena.setOnClickListener { navigateTo(com.example.unilink.activities.CampusArenaActivity::class.java, it) }
        binding.btnGoLiveStream.setOnClickListener { navigateTo(com.example.unilink.activities.LiveStreamActivity::class.java, it) }

        binding.tvLivePulse.setOnClickListener { navigateTo(com.example.unilink.activities.CampusArenaActivity::class.java, it) }
        binding.livePulseBadge.setOnClickListener { navigateTo(com.example.unilink.activities.CampusArenaActivity::class.java, it) }
        binding.btnSettings.setOnClickListener { navigateTo(SettingsActivity::class.java, it) }
        binding.btnLeaderboard.setOnClickListener { navigateTo(LeaderboardActivity::class.java, it) }
        binding.btnNotifications.setOnClickListener { navigateTo(NotificationsActivity::class.java, it) }

        binding.tvHeaderUniCoins.setOnClickListener { HapticUtils.playSelection(it); showProPassDialog() }
        binding.tvUniCoins.setOnClickListener { HapticUtils.playSelection(it); showProPassDialog() }
        binding.tvNewVaultsCount.setOnClickListener { navigateTo(VaultActivity::class.java, it) }

        binding.btnAiAssistant.setOnClickListener {
            HapticUtils.playSelection(it)
            (activity as? MainActivity)?.showAiAssistant()
        }

        binding.tvViewAllDoubts.setOnClickListener { navigateTo(DoubtBoardActivity::class.java, it) }

        binding.btnPostDoubt.setOnClickListener {
            HapticUtils.playSelection(it)
            showPostDoubtDialog()
        }

        binding.tvJoinFocusRoom.setOnClickListener { navigateTo(FocusRoomActivity::class.java) }

        binding.cardStreakBadge.setOnClickListener {
            HapticUtils.playSelection(it)
            Toast.makeText(context, "🔥 7 Day Active Campus Streak! Keep active daily to earn bonus UniCoins!", Toast.LENGTH_SHORT).show()
        }

        binding.btnUniAiHomeAsk.setOnClickListener {
            val prompt = binding.etUniAiHomePrompt.text.toString().trim()
            if (prompt.isNotEmpty()) {
                HapticUtils.playSelection(it)
                (activity as? MainActivity)?.showAiAssistantWithPrompt(prompt)
                binding.etUniAiHomePrompt.setText("")
            } else {
                (activity as? MainActivity)?.showAiAssistant()
            }
        }

        binding.etSearchAll.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchAll.text.toString().trim()
                if (query.isNotEmpty()) navigateToSearchWithQuery(query)
                true
            } else false
        }
    }

    private fun showProPassDialog() {
        val input = EditText(requireContext()).apply { hint = "Enter Pro Pass Code (e.g. UNILINKPRO)" }
        AlertDialog.Builder(requireContext())
            .setTitle("UniLink Pro Pass 👑")
            .setMessage("Upgrade to Pro to unlock Incognito Mode, Cyber Midnight themes, AI Assistant, and 2x UniCoin rewards!")
            .setView(input)
            .setPositiveButton("Redeem / Activate Pro") { _, _ ->
                val code = input.text.toString().trim().ifEmpty { "UNILINKPRO" }
                userRepository.redeemPremiumCode(code) { success, msg ->
                    Toast.makeText(context, msg ?: if (success) "Pro Pass Unlocked! ✨" else "Invalid Code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateTo(clazz: Class<*>, view: View? = null) {
        view?.let { HapticUtils.playSelection(it) }
        startActivity(Intent(context, clazz))
    }

    private fun navigateToSearchWithQuery(query: String) {
        if (query.lowercase().contains("doubt")) {
            startActivity(Intent(context, DoubtBoardActivity::class.java).apply { putExtra("search_query", query) })
        } else {
            val searchFragment = SearchFragment().apply { arguments = Bundle().apply { putString("search_query", query) } }
            parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, searchFragment).addToBackStack(null).commit()
        }
    }

    private fun showPostDoubtDialog() {
        val input = EditText(requireContext()).apply { hint = "Your doubt..." }
        AlertDialog.Builder(requireContext())
            .setTitle("Post a Doubt")
            .setView(input)
            .setPositiveButton("Post") { _, _ ->
                val question = input.text.toString().trim()
                if (question.isNotEmpty()) {
                    doubtRepository.postDoubt(question, 10, listOf("General")) { success ->
                        if (success) Toast.makeText(context, "Posted!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun setupDoubtBoard() {
        doubtsListener?.remove()
        doubtsListener = doubtRepository.observeDoubts { doubts ->
            _binding?.let { binding ->
                binding.rvDoubtBoard.visibility = View.VISIBLE
                homeDoubtAdapter.submitList(doubts.take(3)) {
                    try { binding.rvDoubtBoard.requestLayout() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun answerDoubt(doubt: Doubt) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (doubt.userId == currentUid) return

        doubtRepository.incrementAnswerCount(doubt.doubtId)

        val chatId = chatRepository.getChatId(currentUid, doubt.userId)
        val introMsg = "Hi ${doubt.userName}, I can help with: \"${doubt.question}\""

        chatRepository.sendMessage(chatId, introMsg, "Me", false, doubt.userId) { success ->
            if (success) {
                startActivity(Intent(context, ChatActivity::class.java).apply {
                    putExtra("chatId", chatId)
                    putExtra("receiverId", doubt.userId)
                    putExtra("chatName", doubt.userName)
                })
            }
        }
    }

    private fun setupSkillDuels() {
        binding.rvSkillDuels.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        duelsListener?.remove()
        duelsListener = duelRepository.getOnlinePeers { peers ->
            _binding?.let {
                binding.rvSkillDuels.adapter = DuelPeerAdapter(peers) { peer ->
                    challengeUserToDuel(peer.id, peer.name, peer.major)
                }
            }
        }
    }

    private fun challengeUserToDuel(userId: String, name: String, subject: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val chatId = chatRepository.getChatId(currentUid, userId)
        chatRepository.sendDuelRequest(chatId, userId, 10, subject) { success ->
            if (success) {
                startActivity(Intent(context, ChatActivity::class.java).apply {
                    putExtra("chatId", chatId)
                    putExtra("receiverId", userId)
                    putExtra("chatName", name)
                })
            }
        }
    }

    private fun setupFocusRooms() {
        val mockRooms = listOf(
            FocusRoom("midterm_grind", "Midterm Grind", "#EC4899", "Deep Focus", 24, true),
            FocusRoom("robotics_lab", "Robotics Lab", "#22D3EE", "Project work", 15, true)
        )
        binding.rvFocusRooms.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = FocusRoomAdapter(mockRooms)
        }
    }

    private fun fetchStories() {
        storiesListener?.remove()
        _binding?.rvStories?.visibility = View.GONE
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
        storiesListener?.remove()
        doubtsListener?.remove()
        duelsListener?.remove()
        notificationListener?.remove()

        _binding?.apply {
            btnAiAssistant.clearAnimation()
            livePulseBadge.clearAnimation()
            ivLogo.clearAnimation()
            tvXpStreak.clearAnimation()
        }
        _binding = null
    }
}
