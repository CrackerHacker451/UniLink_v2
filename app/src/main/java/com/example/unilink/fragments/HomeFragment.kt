package com.example.unilink.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.example.unilink.R
import kotlinx.coroutines.launch
import com.example.unilink.adapters.*
import com.example.unilink.databinding.FragmentHomeBinding
import com.example.unilink.models.*
import com.example.unilink.repository.*
import com.example.unilink.utils.HapticUtils
import com.google.firebase.firestore.ListenerRegistration

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()
    private val liveFeedRepository = LiveFeedRepository()
    private val storyRepository = StoryRepository()
    private val userRepository = UserRepository()
    private val doubtRepository = DoubtRepository()
    private val geminiRepository = GeminiRepository()
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Edge-to-Edge Insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.swipeRefreshHome) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply top padding for status bar and bottom for navigation bar (floating)
            // But we only need top here, because the main activity handles the bottom nav card.
            binding.homeRootScroll.updatePadding(top = systemBars.top)
            insets
        }

        postAdapter = PostAdapter()
        binding.rvActivityFeed.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
            setHasFixedSize(true)
        }
        
        homeDoubtAdapter = DoubtAdapter { doubt -> answerDoubt(doubt) }
        binding.rvDoubtBoard.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = homeDoubtAdapter
            setHasFixedSize(true)
        }

        setupUI()
        setupClickListeners()
        fetchFeed()
        fetchStories()
        setupDoubtBoard()
        setupSkillDuels()
        setupFocusRooms()
        setupDashboard()
        setupHighlights()
        startFloatingAnimations()
        setupRefreshLayout()
        observeNotifications()
        
        binding.tvLivePulse.isSelected = true
        binding.root.layoutTransition = android.animation.LayoutTransition()
    }

    private fun setupDashboard() {
        if (_binding == null) return
        
        // Fetch real counts from Firestore for a "functional" experience
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        db.collection("users").whereEqualTo("isOnline", true).get().addOnSuccessListener { 
            if (_binding == null) return@addOnSuccessListener
            val count = it.size()
            animateCount(binding.tvOnlineCount, if (count > 0) count else (12..48).random(), " Students")
        }

        db.collection("chats").whereEqualTo("isGroup", true).get().addOnSuccessListener {
            if (_binding == null) return@addOnSuccessListener
            val count = it.size()
            animateCount(binding.tvActiveTeamsCount, if (count > 0) count else (4..12).random(), " Teams")
        }

        db.collection("vaults").get().addOnSuccessListener {
            if (_binding == null) return@addOnSuccessListener
            val count = it.size()
            animateCount(binding.tvNewVaultsCount, if (count > 0) count else (8..20).random(), " Vaults")
        }.addOnFailureListener {
            if (_binding == null) return@addOnFailureListener
            animateCount(binding.tvNewVaultsCount, (5..15).random(), " Vaults")
        }
    }

    private fun animateCount(textView: android.widget.TextView, target: Int, suffix: String) {
        val animator = android.animation.ValueAnimator.ofInt(0, target)
        animator.duration = 1500
        animator.addUpdateListener { 
            textView.text = "${it.animatedValue}$suffix"
        }
        animator.start()
    }

    private fun setupRefreshLayout() {
        binding.swipeRefreshHome.setOnRefreshListener {
            fetchFeed()
            fetchStories()
            setupDoubtBoard()
            setupSkillDuels()
            setupDashboard()
            binding.root.postDelayed({
                binding.swipeRefreshHome.isRefreshing = false
            }, 1500)
        }
        binding.swipeRefreshHome.setColorSchemeResources(R.color.cyan_brand, R.color.purple_brand)
    }

    private fun observeNotifications() {
        notificationListener?.remove()
        notificationListener = notificationRepository.observeNotifications { notifications ->
            if (_binding == null) return@observeNotifications
            val unreadCount = notifications.count { !it.isRead }
            if (unreadCount > 0) {
                binding.tvNotificationCount.visibility = View.VISIBLE
                binding.tvNotificationCount.text = if (unreadCount > 9) "9+" else unreadCount.toString()
            } else {
                binding.tvNotificationCount.visibility = View.GONE
            }
        }
    }

    private fun setupHighlights() {
        if (_binding == null) return
        val highlights = listOf(
            CampusHighlight("system", "Aryan Sharma", "Earned 'Top Mentor' badge", true),
            CampusHighlight("system", "Sneha Reddy", "Shared new Java notes"),
            CampusHighlight("system", "Kurt", "Completed 'AI Assistant' project", true),
            CampusHighlight("system", "Meera", "Started a 10-day study streak"),
            CampusHighlight("system", "Lalit", "Boosted 'Aura Glass' theme", true)
        ).shuffled()
        
        binding.rvCampusHighlights.adapter = HighlightAdapter(highlights) { highlight ->
            highlight.userId?.let { uid ->
                if (uid != "system") {
                    val intent = Intent(context, com.example.unilink.activities.ProfileActivity::class.java).apply {
                        putExtra("userId", uid)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(context, "${highlight.name} is a campus star! ⭐", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startFloatingAnimations() {
        val pulse = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.pulse)
        binding.btnAiAssistant.startAnimation(pulse)
        binding.livePulseBadge.visibility = View.VISIBLE
        binding.livePulseBadge.startAnimation(pulse)
        
        val floatAnim = android.view.animation.TranslateAnimation(0f, 0f, 0f, -15f).apply {
            duration = 2000
            repeatMode = android.view.animation.Animation.REVERSE
            repeatCount = android.view.animation.Animation.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        binding.ivLogo.startAnimation(floatAnim)
    }

    private fun setupUI() {
        userListener?.remove()
        userListener = userRepository.observeCurrentUser { user ->
            if (_binding == null) return@observeCurrentUser
            user?.let {
                binding.tvCollegeName.text = it.collegeName ?: "SRM University"
                binding.tvUniCoins.text = it.uniCoins.toString()
                
                val streakText = if (it.streakDays == 1) "🔥 1-day streak" else "🔥 ${it.streakDays}-day streak"
                binding.tvXpStreak.text = streakText
                
                // Streak Glow Animation
                if (it.streakDays >= 3) {
                    binding.tvXpStreak.setTextColor(android.graphics.Color.parseColor("#FACC15"))
                    val glow = android.view.animation.AlphaAnimation(0.6f, 1.0f).apply {
                        duration = 1000
                        repeatMode = android.view.animation.Animation.REVERSE
                        repeatCount = android.view.animation.Animation.INFINITE
                    }
                    binding.tvXpStreak.startAnimation(glow)
                }
                
                // Show Impact Score instead of just total XP
                binding.tvTotalXp.text = "Impact: ${it.impactScore}"
                
                val progress = (it.totalXp % 1000) / 10
                binding.progressXp.progress = progress
            }
        }
    }

    private fun setupClickListeners() {
        binding.actionFindTeam.root.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(context, com.example.unilink.activities.FindTeamActivity::class.java))
        }

        binding.actionCreateTeam.root.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(context, com.example.unilink.activities.CreateTeamActivity::class.java))
        }

        binding.actionRandomMatch.root.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(context, com.example.unilink.activities.RandomMatchActivity::class.java))
        }

        binding.actionNearby.root.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(context, com.example.unilink.activities.NearbyStudentsActivity::class.java))
        }
        
        binding.btnAiAssistant.setOnClickListener {
            HapticUtils.playSelection(it)
            (activity as? com.example.unilink.activities.MainActivity)?.showAiAssistant()
        }

        binding.btnLeaderboard.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(context, com.example.unilink.activities.LeaderboardActivity::class.java))
        }

        binding.btnNotifications.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(context, com.example.unilink.activities.NotificationsActivity::class.java))
        }
        
        binding.tvViewAllDoubts.setOnClickListener {
            HapticUtils.playSelection(it)
            startActivity(Intent(context, com.example.unilink.activities.DoubtBoardActivity::class.java))
        }

        binding.btnPostDoubt.setOnClickListener {
            HapticUtils.playSelection(it)
            showPostDoubtDialog()
        }

        binding.tvJoinFocusRoom.setOnClickListener {
            startActivity(Intent(context, com.example.unilink.activities.FocusRoomActivity::class.java))
        }

        binding.etSearchAll.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchAll.text.toString().trim()
                if (query.isNotEmpty()) navigateToSearchWithQuery(query)
                true
            } else false
        }
    }

    private fun navigateToSearchWithQuery(query: String) {
        val lowerQuery = query.lowercase()
        if (lowerQuery.contains("doubt")) {
            startActivity(Intent(context, com.example.unilink.activities.DoubtBoardActivity::class.java).apply { putExtra("search_query", query) })
        } else {
            val searchFragment = SearchFragment().apply { arguments = Bundle().apply { putString("search_query", query) } }
            parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, searchFragment).addToBackStack(null).commit()
        }
    }

    private fun showPostDoubtDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        val input = android.widget.EditText(requireContext())
        input.hint = "Your doubt..."
        builder.setTitle("Post a Doubt").setView(input).setPositiveButton("Post") { _, _ ->
            val question = input.text.toString().trim()
            if (question.isNotEmpty()) {
                doubtRepository.postDoubt(question, 10, listOf("General")) { success ->
                    if (success) Toast.makeText(context, "Posted!", Toast.LENGTH_SHORT).show()
                }
            }
        }.show()
    }

    private fun setupDoubtBoard() {
        doubtsListener?.remove()
        doubtsListener = doubtRepository.observeDoubts { doubts ->
            if (_binding != null) homeDoubtAdapter.submitList(doubts.take(3))
        }
    }

    private fun answerDoubt(doubt: Doubt) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (doubt.userId == currentUid) return
        
        val chatId = chatRepository.getChatId(currentUid, doubt.userId)
        val introMsg = "Hi ${doubt.userName}, I can help with: \"${doubt.question}\""
        
        chatRepository.sendMessage(chatId, introMsg, "Me", false, doubt.userId) { success ->
            if (success) {
                startActivity(Intent(context, com.example.unilink.activities.ChatActivity::class.java).apply {
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
            if (_binding != null) binding.rvSkillDuels.adapter = DuelPeerAdapter(peers) { peer ->
                challengeUserToDuel(peer.id, peer.name, peer.major)
            }
        }
    }

    private fun challengeUserToDuel(userId: String, name: String, subject: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val chatId = chatRepository.getChatId(currentUid, userId)
        chatRepository.sendDuelRequest(chatId, userId, 10, subject) { success ->
            if (success) {
                startActivity(Intent(context, com.example.unilink.activities.ChatActivity::class.java).apply {
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
        binding.rvFocusRooms.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFocusRooms.adapter = FocusRoomAdapter(mockRooms)
    }

    private fun fetchStories() {
        storiesListener?.remove()
        storiesListener = storyRepository.getActiveStories { stories ->
            if (_binding != null) {
                binding.rvStories.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                val myStoryPlaceholder = Story(storyId = "1", userId = "1", userName = "My Story")
                binding.rvStories.adapter = StoryAdapter(listOf(myStoryPlaceholder) + stories)
            }
        }
    }

    private fun fetchFeed() {
        feedListener?.remove()
        feedListener = liveFeedRepository.observeCampusPulse { posts ->
            if (_binding != null) postAdapter.submitList(posts)
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
        _binding = null
    }
}
