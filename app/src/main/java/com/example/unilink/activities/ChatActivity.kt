package com.example.unilink.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
import com.example.unilink.adapters.ChatAdapter
import com.example.unilink.databinding.ActivityChatBinding
import com.example.unilink.models.ChatMessage
import com.example.unilink.repository.ChatRepository
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.EncryptionUtils
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.SwipeReplyCallback
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ChatActivity : AppCompatActivity() {

    private var _binding: ActivityChatBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()
    private val storageRepository = com.example.unilink.repository.StorageRepository()

    private lateinit var adapter: ChatAdapter
    private var chatId: String? = null
    private var receiverId: String? = null
    private var isGroup: Boolean = false
    private var isProjectSync: Boolean = false
    private var currentUsername: String = ""

    private var chatRoomListener: ListenerRegistration? = null
    private var messageListener: ListenerRegistration? = null
    private var duelStartListener: ListenerRegistration? = null
    private var replyToMessage: ChatMessage? = null
    private var editingMessage: ChatMessage? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val cid = chatId ?: return@let
            val rid = receiverId ?: ""
            
            if (isProjectSync) {
                val fileName = "file_${System.currentTimeMillis()}"
                Toast.makeText(this, "Uploading to Web Team...", Toast.LENGTH_SHORT).show()
                storageRepository.uploadTeamAttachment(cid, it, fileName) { url ->
                    if (url != null) {
                        chatRepository.sendMessage(cid, "📎 Attached File", currentUsername, isGroup, null, isProjectSync = true) {
                            if (it) Toast.makeText(this, "File Shared!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                return@let
            }
            
            Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()
            storageRepository.uploadChatImage(cid, it) { url ->
                if (url != null) {
                    chatRepository.sendImageMessage(cid, url, currentUsername, isGroup, rid) { success ->
                        if (success) Toast.makeText(this, "Image Sent!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.appBarLayout.updatePadding(top = systemBars.top)
            binding.inputArea.updatePadding(bottom = if (ime.bottom > 0) ime.bottom else systemBars.bottom)
            insets
        }

        chatId = intent.getStringExtra("chatId")
        receiverId = intent.getStringExtra("receiverId")
        if (chatId.isNullOrEmpty() && !receiverId.isNullOrEmpty()) {
            val currentUid = auth.currentUser?.uid ?: ""
            chatId = chatRepository.getChatId(currentUid, receiverId!!)
        }
        isGroup = intent.getBooleanExtra("isGroup", false)
        isProjectSync = intent.getBooleanExtra("isProjectSync", false)
        val receiverName = intent.getStringExtra("receiverName") ?: intent.getStringExtra("chatName")

        setupToolbar(receiverName)
        setupRecyclerView()
        setupInputLogic()
        setupQuickActions()
        fetchCurrentUsername()
        listenForMessages()
        
        if (!isProjectSync) {
            listenForChatRoomUpdates()
            listenForDuelStart()
            loadPremiumWallpaper()
        }
        setupToolbarActions()
        
        chatId?.let { 
            if (!isProjectSync) chatRepository.markChatAsRead(it)
            com.example.unilink.repository.NotificationRepository().markNotificationsByTargetAsRead(it)
        }
    }

    private fun setupToolbarActions() {
        binding.btnVideoCall.setOnClickListener {
            val cid = chatId ?: return@setOnClickListener
            val rid = receiverId ?: ""
            val rName = binding.tvChatReceiverName.text.toString()

            val intent = Intent(this, VideoCallActivity::class.java).apply {
                putExtra("channelId", cid)
                putExtra("partnerId", rid)
                putExtra("partnerName", rName)
                putExtra("isOffer", true)
            }
            startActivity(intent)

            if (rid.isNotEmpty()) {
                com.example.unilink.repository.NotificationRepository().sendNotification(
                    rid,
                    "video_call",
                    "Incoming Video Call 🎥",
                    "$currentUsername is calling you for a video call!",
                    cid
                )
            }
        }

        binding.btnViewProfile.setOnClickListener {
            receiverId?.let { rid ->
                val intent = Intent(this, ProfileActivity::class.java).apply {
                    putExtra("userId", rid)
                }
                startActivity(intent)
            }
        }

        binding.btnChatSettings.setOnClickListener {
            showWallpaperPicker()
        }

        binding.btnMore.setOnClickListener {
            showMessageActionsMenu()
        }
    }

    private fun showMessageActionsMenu() {
        val popup = androidx.appcompat.widget.PopupMenu(this, binding.btnMore)
        popup.menu.add("Clear Chat")
        popup.menu.add("Export Chat")
        popup.menu.add("Block Student")
        popup.menu.add("Change Wallpaper")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Block Student" -> {
                    receiverId?.let { rid ->
                        userRepository.blockUser(rid) { finish() }
                    }
                }
                "Change Wallpaper" -> {
                    showWallpaperPicker()
                }
                else -> Toast.makeText(this, "Action executed", Toast.LENGTH_SHORT).show()
            }
            true
        }
        popup.show()
    }

    private fun setWallpaperImage(wallpaperSource: Any) {
        if (_binding == null) return
        binding.ivChatWallpaper.visibility = View.VISIBLE
        binding.ivChatWallpaper.alpha = 0.95f

        try {
            when {
                wallpaperSource is Int -> {
                    val drawable = androidx.core.content.ContextCompat.getDrawable(this, wallpaperSource)
                    binding.ivChatWallpaper.setImageDrawable(drawable)
                    binding.ivChatWallpaper.background = drawable
                }
                wallpaperSource is android.net.Uri -> {
                    binding.ivChatWallpaper.background = null
                    binding.ivChatWallpaper.load(wallpaperSource) {
                        crossfade(true)
                        allowHardware(false)
                    }
                }
                else -> {
                    val sourceStr = wallpaperSource.toString()
                    val resId = ThemeUtils.getDrawableIdByName(this, sourceStr)
                    if (resId != 0) {
                        val drawable = androidx.core.content.ContextCompat.getDrawable(this, resId)
                        binding.ivChatWallpaper.setImageDrawable(drawable)
                        binding.ivChatWallpaper.background = drawable
                    } else {
                        binding.ivChatWallpaper.background = null
                        val uriToLoad = if (sourceStr.startsWith("content://") || sourceStr.startsWith("file://")) {
                            android.net.Uri.parse(sourceStr)
                        } else {
                            sourceStr
                        }
                        binding.ivChatWallpaper.load(uriToLoad) {
                            crossfade(true)
                            allowHardware(false)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatActivity", "Error setting wallpaper image: ${e.message}")
        }

        binding.ivChatWallpaper.requestLayout()
        binding.ivChatWallpaper.invalidate()
    }

    private fun applyDynamicChatTheme(hexColor: String) {
        if (_binding == null || hexColor.isEmpty()) return
        try {
            val colorInt = Color.parseColor(hexColor)
            binding.btnSend.backgroundTintList = ColorStateList.valueOf(colorInt)
            binding.quickActionBarCard.strokeColor = colorInt
            adapter?.dynamicThemeColor = hexColor
            adapter?.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e("ChatActivity", "Error applying dynamic theme color", e)
        }
    }

    private fun promptWallpaperScope(wallpaperRef: String, themeColor: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Apply Wallpaper Scope 🔒")
            .setMessage("Would you like to apply this wallpaper strictly to THIS CHAT ROOM (shared only with this connected person) or set as your default for ALL YOUR CHATS?")
            .setPositiveButton("This Chat Only 🔒") { _, _ ->
                applyWallpaperToThisChatOnly(wallpaperRef, themeColor)
            }
            .setNegativeButton("All My Chats 🌐") { _, _ ->
                applyWallpaperToAllChats(wallpaperRef, themeColor)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun applyWallpaperToThisChatOnly(wallpaperRef: String, themeColor: String) {
        setWallpaperImage(wallpaperRef)
        applyDynamicChatTheme(themeColor)

        val cid = chatId
        if (!cid.isNullOrEmpty()) {
            val roomUpdates = mapOf(
                "chatWallpaperUrl" to wallpaperRef,
                "chatThemeColor" to themeColor
            )
            db.collection("chats").document(cid).set(roomUpdates, com.google.firebase.firestore.SetOptions.merge())
            Toast.makeText(this, "Wallpaper locked to THIS CHAT ONLY! Strictly private 🔒", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyWallpaperToAllChats(wallpaperRef: String, themeColor: String) {
        val uid = auth.currentUser?.uid ?: return
        setWallpaperImage(wallpaperRef)
        applyDynamicChatTheme(themeColor)

        getSharedPreferences("unilink_wallpaper_prefs", MODE_PRIVATE)
            .edit()
            .putString("local_wallpaper_$uid", wallpaperRef)
            .putString("local_theme_color_$uid", themeColor)
            .apply()

        db.collection("users").document(uid).update(
            mapOf(
                "chatWallpaperUrl" to wallpaperRef,
                "defaultWallpaperUrl" to wallpaperRef,
                "defaultThemeColor" to themeColor
            )
        )
        Toast.makeText(this, "Default wallpaper updated for ALL your chats! 🌐", Toast.LENGTH_SHORT).show()
    }

    private val pickWallpaperGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { wallpaperUri ->
            Toast.makeText(this, "Extracting matching chat theme color... ✨", Toast.LENGTH_SHORT).show()

            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, wallpaperUri))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(contentResolver, wallpaperUri)
                }
                val dynamicThemeColor = ThemeUtils.extractDominantHexColor(bitmap)

                // Instant visual update on screen!
                setWallpaperImage(wallpaperUri)
                applyDynamicChatTheme(dynamicThemeColor)

                com.example.unilink.repository.StorageRepository().uploadPostImage(wallpaperUri) { imageUrl ->
                    val finalRef = imageUrl ?: wallpaperUri.toString()
                    promptWallpaperScope(finalRef, dynamicThemeColor)
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error extracting wallpaper color", e)
                setWallpaperImage(wallpaperUri)
                promptWallpaperScope(wallpaperUri.toString(), "#06B6D4")
            }
        }
    }

    private fun showWallpaperPicker() {
        val wallpapers = arrayOf(
            "🌌 Deep Space Nebula",
            "⚡ Sci-Fi Cyberpunk",
            "⚡ Cyber Lightning",
            "🔮 Aurora Purple",
            "🌲 Emerald Forest",
            "🌅 Sunset Amber",
            "📷 Custom Gallery Photo"
        )
        val drawableNames = arrayOf(
            "banner_nebula",
            "banner_sci_fi",
            "banner_lightning",
            "bg_aura_premium",
            "bg_mesh_gradient",
            "bg_premium_card",
            "CUSTOM_GALLERY"
        )
        val presetThemeColors = arrayOf(
            "#A855F7",
            "#06B6D4",
            "#22D3EE",
            "#EC4899",
            "#10B981",
            "#FACC15"
        )

        val adapter = android.widget.ArrayAdapter(this, R.layout.item_dialog_choice, wallpapers)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Chat Wallpapers 🎨")
            .setAdapter(adapter) { _, which ->
                val selected = drawableNames[which]
                if (selected == "CUSTOM_GALLERY") {
                    pickWallpaperGalleryLauncher.launch("image/*")
                } else {
                    val presetColor = presetThemeColors.getOrNull(which) ?: "#A855F7"
                    // Instant visual update on screen!
                    setWallpaperImage(selected)
                    applyDynamicChatTheme(presetColor)
                    promptWallpaperScope(selected, presetColor)
                }
            }
            .show()
    }

    private fun listenForDuelStart() {
        val uid = auth.currentUser?.uid ?: return
        duelStartListener = db.collection("duels")
            .whereEqualTo("hostId", uid)
            .whereEqualTo("status", "started")
            .addSnapshotListener { snapshot, _ ->
                val doc = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                val duelId = doc.id
                val partnerId = doc.getString("receiverId") ?: ""
                val subject = doc.getString("subject") ?: "General"
                
                val intent = Intent(this, DuelQuizActivity::class.java).apply {
                    putExtra("duelId", duelId)
                    putExtra("subject", subject)
                    putExtra("isHost", true)
                    putExtra("partnerId", partnerId)
                }
                startActivity(intent)
                
                duelStartListener?.remove()
                duelStartListener = null
            }
    }

    private fun listenForChatRoomUpdates() {
        chatId?.let { id ->
            chatRoomListener = chatRepository.observeChatRoom(id) { room ->
                if (_binding == null || room == null) return@observeChatRoom
                
                if (!isGroup && receiverId != null) {
                    val isReceiverTyping = room.typingStatus[receiverId] == true
                    binding.typingIndicator.visibility = if (isReceiverTyping) View.VISIBLE else View.GONE
                }

                if (!room.chatWallpaperUrl.isNullOrEmpty()) {
                    setWallpaperImage(room.chatWallpaperUrl)
                    if (!room.chatThemeColor.isNullOrEmpty()) {
                        applyDynamicChatTheme(room.chatThemeColor)
                    }
                } else {
                    loadPremiumWallpaper()
                }
            }
        }
    }

    private fun loadPremiumWallpaper() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("unilink_wallpaper_prefs", MODE_PRIVATE)
        val localUriString = prefs.getString("local_wallpaper_$uid", null)
        val localThemeColor = prefs.getString("local_theme_color_$uid", null)

        if (!localUriString.isNullOrEmpty()) {
            setWallpaperImage(localUriString)
            if (!localThemeColor.isNullOrEmpty()) {
                applyDynamicChatTheme(localThemeColor)
            }
            return
        }

        userRepository.getCurrentUser { user ->
            if (isFinishing || isDestroyed || _binding == null) return@getCurrentUser
            val wallpaper = user?.chatWallpaperUrl ?: user?.defaultWallpaperUrl
            val themeColor = user?.defaultThemeColor
            if (!wallpaper.isNullOrEmpty()) {
                setWallpaperImage(wallpaper)
                if (!themeColor.isNullOrEmpty()) {
                    applyDynamicChatTheme(themeColor)
                }
            } else {
                binding.ivChatWallpaper.visibility = View.GONE
            }
        }
    }

    private fun setupToolbar(name: String?) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (isGroup) {
            binding.tvChatReceiverStatus.text = "Group Chat"
            binding.ivChatReceiverAvatar.setImageResource(R.drawable.ic_profile_placeholder)
            db.collection("chats").document(chatId ?: "").get().addOnSuccessListener { doc ->
                val gName = doc.getString("groupName") ?: name
                binding.tvChatReceiverName.text = gName ?: "Group Chat"
            }
        } else if (receiverId != null) {
            userRepository.observeUser(receiverId!!) { user ->
                if (_binding == null) return@observeUser
                val displayName = user?.name?.ifEmpty { user.username } ?: name ?: "Student"
                binding.tvChatReceiverName.text = displayName
                binding.tvChatReceiverStatus.text = if (user?.isOnline == true) "Online" else "Offline"
                binding.tvChatReceiverStatus.setTextColor(
                    if (user?.isOnline == true) android.graphics.Color.parseColor("#22C55E")
                    else android.graphics.Color.GRAY
                )

                com.example.unilink.utils.ImageLoaderUtils.loadSmartImage(
                    binding.ivChatReceiverAvatar,
                    user?.profileImageUrl,
                    R.drawable.ic_profile_placeholder,
                    isCircle = true,
                    fallbackName = user?.name
                )
            }
        } else {
            binding.tvChatReceiverName.text = name ?: "Student"
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            currentUserId = auth.currentUser?.uid ?: "",
            chatId = chatId ?: "",
            onMessageClick = { msg, _ -> showMessageActions(msg) },
            onMessageDoubleTap = { msg -> chatRepository.toggleReaction(chatId!!, msg.messageId, "❤️") },
            onPollVote = { _, _ -> },
            onDuelAccept = { msg -> acceptDuelChallenge(msg) }
        )
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.adapter = adapter
        binding.rvMessages.setHasFixedSize(false)
        binding.rvMessages.itemAnimator = null

        val swipeCallback = SwipeReplyCallback(this) { position ->
            adapter.getMessageAt(position)?.let { enterReplyMode(it) }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvMessages)
    }

    private fun acceptDuelChallenge(msg: ChatMessage) {
        val currentUid = auth.currentUser?.uid ?: return
        val cid = chatId ?: return
        
        userRepository.getUserById(currentUid) { user ->
            if ((user?.uniCoins ?: 0) < 50) {
                Toast.makeText(this, "Insufficient UniCoins! Need 50 to accept duel.", Toast.LENGTH_LONG).show()
                return@getUserById
            }
            
            chatRepository.updateDuelStatus(cid, msg.messageId, "active")
            val intent = Intent(this, DuelQuizActivity::class.java).apply {
                putExtra("chatId", cid)
                putExtra("messageId", msg.messageId)
                putExtra("subject", msg.duelSubject ?: "General Knowledge")
                putExtra("wager", msg.duelWager ?: 25)
                putExtra("partnerId", msg.senderId)
                putExtra("isHost", false)
            }
            startActivity(intent)
        }
    }

    private fun showMessageActions(msg: ChatMessage) {
        val actions = mutableListOf("Copy", "Forward")
        if (msg.senderId == auth.currentUser?.uid) {
            actions.add("Edit")
            actions.add("Delete")
        }

        val adapter = android.widget.ArrayAdapter(this, R.layout.item_dialog_choice, actions)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setAdapter(adapter) { _, which ->
                when (actions[which]) {
                    "Copy" -> {
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val decrypted = EncryptionUtils.decrypt(msg.messageText, chatId ?: "")
                        val clip = android.content.ClipData.newPlainText("UniLink Message", decrypted)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
                    }
                    "Forward" -> showForwardDialog(msg)
                    "Edit" -> enterEditMode(msg)
                    "Delete" -> {
                        chatId?.let { id ->
                            chatRepository.deleteMessage(id, msg.messageId, isGroup) { success ->
                                if (success) Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }.show()
    }

    private fun showForwardDialog(msg: ChatMessage) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogBinding = com.example.unilink.databinding.BottomSheetForwardBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val decryptedText = EncryptionUtils.decrypt(msg.messageText, chatId ?: "")

        chatRepository.getRecentChats { rooms ->
            val adapter = com.example.unilink.adapters.ChatRoomAdapter(rooms, auth.currentUser?.uid ?: "") { room ->
                chatRepository.forwardMessage(room.chatId, decryptedText, currentUsername, room.isGroup, if (room.isGroup) null else room.participants.firstOrNull { it != auth.currentUser?.uid }) { success ->
                    if (success) {
                        Toast.makeText(this, "Message forwarded to ${if (room.isGroup) room.groupName else "student"}", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
            dialogBinding.rvForwardList.layoutManager = LinearLayoutManager(this)
            dialogBinding.rvForwardList.adapter = adapter
        }
        dialog.show()
    }

    private fun enterReplyMode(msg: ChatMessage) {
        replyToMessage = msg
        binding.replyPreview.visibility = View.VISIBLE
        binding.tvReplyTarget.text = EncryptionUtils.decrypt(msg.messageText, chatId ?: "")
        binding.etMessage.requestFocus()
    }

    private fun enterEditMode(msg: ChatMessage) {
        editingMessage = msg
        binding.etMessage.setText(EncryptionUtils.decrypt(msg.messageText, chatId ?: ""))
        binding.btnSend.setImageResource(android.R.drawable.ic_menu_edit)
        binding.etMessage.requestFocus()
    }

    private fun cancelReply() {
        replyToMessage = null
        binding.replyPreview.visibility = View.GONE
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val cid = chatId ?: return
        val rid = receiverId ?: ""

        if (editingMessage != null) {
            chatRepository.editMessage(cid, editingMessage!!.messageId, text, isGroup) {
                editingMessage = null
                binding.btnSend.setImageResource(android.R.drawable.ic_menu_send)
            }
        } else {
            val replyTextDecrypted = replyToMessage?.let { EncryptionUtils.decrypt(it.messageText, cid) }
            chatRepository.sendMessage(cid, text, currentUsername, isGroup, rid, replyToMessage?.messageId, replyTextDecrypted, isProjectSync) {
                if (it) {
                    cancelReply()
                    playSendSound()
                    HapticUtils.playSuccess(binding.btnSend)
                }
            }
        }
        binding.etMessage.setText("")
    }

    private fun playSendSound() {
        try {
            binding.root.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        } catch (_: Exception) {}
    }

    private var typingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var stopTypingRunnable = Runnable {
        chatId?.let { id -> chatRepository.setTypingStatus(id, false) }
    }

    private fun setupInputLogic() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.btnSend.visibility = if (hasText) View.VISIBLE else View.GONE
                binding.btnVoice.visibility = if (hasText) View.GONE else View.VISIBLE
                
                chatId?.let { id ->
                    if (hasText) {
                        chatRepository.setTypingStatus(id, true)
                        typingHandler.removeCallbacks(stopTypingRunnable)
                        typingHandler.postDelayed(stopTypingRunnable, 2500)
                    } else {
                        typingHandler.removeCallbacks(stopTypingRunnable)
                        chatRepository.setTypingStatus(id, false)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnVoice.setOnClickListener { 
            userRepository.getCurrentUser { user ->
                if (user?.isPremium == true) {
                    Toast.makeText(this, "🎙️ Sending voice message...", Toast.LENGTH_SHORT).show()
                    chatId?.let { id ->
                        receiverId?.let { rid ->
                            chatRepository.sendVoiceMessage(id, rid) { success ->
                                if (success) Toast.makeText(this, "Voice message sent!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    com.example.unilink.utils.PremiumDialogHelper.showPremiumPurchaseDialog(this)
                }
            }
        }
        binding.btnCloseReply.setOnClickListener { cancelReply() }
        
        binding.btnAttach.setOnClickListener { 
            pickImageLauncher.launch("image/*")
        }
    }

    private fun setupQuickActions() {
        if (!isGroup) {
            binding.qaPoll.visibility = View.GONE
        } else {
            binding.qaPoll.visibility = View.VISIBLE
        }

        binding.qaPoll.setOnClickListener { showPollDialog() }
        binding.qaDoubt.setOnClickListener { showDoubtDialog() }
        binding.qaToken.setOnClickListener { 
            if (isGroup) {
                Toast.makeText(this, "Token transfers available in 1-on-1 private chats", Toast.LENGTH_SHORT).show()
            } else {
                showTokenTransferDialog()
            }
        }
        binding.qaCode.setOnClickListener { showCodeDialog() }
        binding.qaDuel.setOnClickListener {
            if (isGroup) {
                Toast.makeText(this, "Duels available in 1-on-1 private chats", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showDuelRequestDialog()
        }
    }

    private fun showPollDialog() {
        if (!isGroup) {
            Toast.makeText(this, "Polls are only available in group chats!", Toast.LENGTH_SHORT).show()
            return
        }
        val etQuestion = android.widget.EditText(this).apply {
            hint = "Enter poll question (e.g. When is the project deadline?)"
            setPadding(30, 20, 30, 20)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Create Group Poll 📊")
            .setView(etQuestion)
            .setPositiveButton("Create Poll") { _, _ ->
                val question = etQuestion.text.toString().trim()
                if (question.isNotEmpty()) {
                    val cid = chatId ?: return@setPositiveButton
                    val options = mapOf("Option A" to 0, "Option B" to 0, "Option C" to 0)
                    chatRepository.sendPollMessage(cid, question, options, currentUsername) { success ->
                        if (success) Toast.makeText(this, "Poll created! 📊", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDoubtDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_token_transfer, null)
        val etQuestion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTokenAmount)
        val etReward = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTokenNote)

        etQuestion.hint = "Enter doubt question..."
        etReward.hint = "UniCoins Reward Bounty (e.g. 15)"

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Ask Academic Doubt 🧠")
            .setView(dialogView)
            .setPositiveButton("Post Doubt 🚀") { _, _ ->
                val question = etQuestion.text.toString().trim()
                val reward = etReward.text.toString().toIntOrNull() ?: 15
                if (question.isNotEmpty()) {
                    val cid = chatId ?: return@setPositiveButton
                    val rid = receiverId ?: ""
                    chatRepository.sendDoubtMessage(cid, question, reward, currentUsername, isGroup, rid) { success ->
                        if (success) Toast.makeText(this, "Doubt posted! UniAI is solving... 🧠", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCodeDialog() {
        val etCode = android.widget.EditText(this).apply {
            hint = "Paste or write code snippet here..."
            minLines = 4
            setPadding(30, 20, 30, 20)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Share Code Snippet 💻")
            .setView(etCode)
            .setPositiveButton("Share Code") { _, _ ->
                val code = etCode.text.toString().trim()
                if (code.isNotEmpty()) {
                    val cid = chatId ?: return@setPositiveButton
                    val formattedText = "```kotlin\n$code\n```"
                    chatRepository.sendMessage(cid, formattedText, currentUsername, isGroup, receiverId) {
                        Toast.makeText(this, "Code snippet shared! 💻", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDuelRequestDialog() {
        if (isGroup) {
            Toast.makeText(this, "Quiz duels are available in 1-on-1 private chats!", Toast.LENGTH_SHORT).show()
            return
        }

        val subjects = arrayOf("Computer Science & Algorithms 💻", "AI & Machine Learning 🧠", "Web3 & Blockchain 🔗", "General Knowledge 🌍", "Physics & Math 📐")

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Challenge Partner to Quiz Duel ⚔️")
            .setItems(subjects) { _, which ->
                val selectedSubject = subjects[which].split(" ")[0]
                val cid = chatId ?: return@setItems
                val rid = receiverId ?: return@setItems

                chatRepository.sendDuelRequest(cid, rid, 25, selectedSubject) { success ->
                    if (success) {
                        Toast.makeText(this, "⚔️ Challenge sent! Waiting for partner to accept...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to send duel challenge", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTokenTransferDialog() {
        val cid = chatId ?: return
        val rid = receiverId ?: return
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_token_transfer, null)
        val etAmount = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTokenAmount)
        val etNote = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTokenNote)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val amount = etAmount.text.toString().toIntOrNull() ?: 0
                val note = etNote.text.toString().trim()
                if (amount > 0) {
                    chatRepository.sendTokenTransfer(cid, rid, amount, note) { success ->
                        if (success) Toast.makeText(this, "Tokens Sent! 💰", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(this, "Insufficient balance or error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchCurrentUsername() {
        userRepository.getCurrentUser { user ->
            currentUsername = user?.name ?: user?.username ?: auth.currentUser?.displayName ?: "Student"
        }
    }

    private fun listenForMessages() {
        chatId?.let { id ->
            messageListener = chatRepository.listenForMessages(id, isProjectSync) { messages ->
                if (_binding == null) return@listenForMessages
                adapter.updateMessages(messages) {
                    if (messages.isNotEmpty()) {
                        binding.rvMessages.post {
                            binding.rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        chatId?.let { id ->
            typingHandler.removeCallbacks(stopTypingRunnable)
            chatRepository.setTypingStatus(id, false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatRoomListener?.remove()
        messageListener?.remove()
        duelStartListener?.remove()
        _binding = null
    }
}
