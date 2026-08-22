package com.example.unilink.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.adapters.ChatAdapter
import com.example.unilink.databinding.ActivityChatBinding
import com.example.unilink.models.ChatMessage
import com.example.unilink.repository.ChatRepository
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.ThemeUtils
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.SwipeReplyCallback
import androidx.recyclerview.widget.ItemTouchHelper
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
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

        // Ultra-Secure: Block Screenshots & Screen Recording
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.toolbar.updatePadding(top = systemBars.top)
            binding.inputArea.updatePadding(bottom = if (ime.bottom > 0) ime.bottom else systemBars.bottom)
            insets
        }

        chatId = intent.getStringExtra("chatId")
        receiverId = intent.getStringExtra("receiverId")
        isGroup = intent.getBooleanExtra("isGroup", false)
        val receiverName = intent.getStringExtra("receiverName") ?: intent.getStringExtra("chatName")

        setupToolbar(receiverName)
        setupRecyclerView()
        setupInputLogic()
        setupQuickActions()
        fetchCurrentUsername()
        listenForMessages()
        listenForChatRoomUpdates()
        listenForDuelStart()
        loadPremiumWallpaper()
        setupToolbarActions()
        
        chatId?.let { 
            chatRepository.markChatAsRead(it)
            com.example.unilink.repository.NotificationRepository().markNotificationsByTargetAsRead(it)
        }
    }

    private fun setupToolbarActions() {
        binding.btnViewProfile.setOnClickListener {
            receiverId?.let { rid ->
                val intent = Intent(this, ProfileActivity::class.java).apply {
                    putExtra("userId", rid)
                }
                startActivity(intent)
            }
        }

        binding.btnChatSettings.setOnClickListener {
            Toast.makeText(this, "Chat Customization coming soon", Toast.LENGTH_SHORT).show()
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
        
        userRepository.getCurrentUser { user ->
            if (user?.isPremium == true) {
                popup.menu.add("Change Wallpaper")
            }
        }

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
                else -> Toast.makeText(this, "Action not yet available", Toast.LENGTH_SHORT).show()
            }
            true
        }
        popup.show()
    }

    private fun showWallpaperPicker() {
        val wallpapers = arrayOf("Nebula", "Sci-Fi", "Lightning", "Aurora")
        val drawableNames = arrayOf("banner_nebula", "banner_sci_fi", "banner_lightning", "bg_aura_premium")
        
        AlertDialog.Builder(this)
            .setTitle("Choose Pro Wallpaper")
            .setItems(wallpapers) { _, which ->
                val selected = drawableNames[which]
                userRepository.updateUserPremiumSettings(wallpaperResId = null, bannerName = null, appTheme = null, profilePicUrl = null) {
                    // Update only wallpaper
                    db.collection("users").document(auth.currentUser?.uid ?: "").update("chatWallpaperUrl", selected)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Wallpaper updated!", Toast.LENGTH_SHORT).show()
                            loadPremiumWallpaper()
                        }
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
                
                // Join as Host
                val intent = Intent(this, DuelQuizActivity::class.java).apply {
                    putExtra("duelId", duelId)
                    putExtra("subject", subject)
                    putExtra("isHost", true)
                    putExtra("partnerId", partnerId)
                }
                startActivity(intent)
                
                // Clean up listener
                duelStartListener?.remove()
                duelStartListener = null
            }
    }

    private fun listenForChatRoomUpdates() {
        chatId?.let { id ->
            chatRoomListener = chatRepository.observeChatRoom(id) { room ->
                if (_binding == null || room == null) return@observeChatRoom
                
                // Show typing status
                if (!isGroup && receiverId != null) {
                    val isReceiverTyping = room.typingStatus[receiverId] == true
                    binding.typingIndicator.visibility = if (isReceiverTyping) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun loadPremiumWallpaper() {
        userRepository.getCurrentUser { user ->
            if (user?.isPremium == true && !user.chatWallpaperUrl.isNullOrEmpty()) {
                binding.ivChatWallpaper.visibility = View.VISIBLE
                val resId = ThemeUtils.getDrawableIdByName(this, user.chatWallpaperUrl)
                if (resId != 0) {
                    binding.ivChatWallpaper.setImageResource(resId)
                } else {
                    binding.ivChatWallpaper.load(user.chatWallpaperUrl)
                }
            }
        }
    }

    private fun setupToolbar(name: String?) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.tvChatReceiverName.text = name ?: "Chat"
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        if (receiverId != null) {
            userRepository.observeUser(receiverId!!) { user ->
                if (_binding == null) return@observeUser
                binding.tvChatReceiverStatus.text = if (user?.isOnline == true) "Online" else "Offline"
                binding.tvChatReceiverStatus.setTextColor(
                    if (user?.isOnline == true) android.graphics.Color.parseColor("#22C55E") 
                    else android.graphics.Color.GRAY
                )
                
                binding.ivChatReceiverAvatar.load(user?.profileImageUrl ?: R.drawable.ic_profile_placeholder) {
                    transformations(CircleCropTransformation())
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(emptyList(), auth.currentUser?.uid ?: "", chatId ?: "",
            { msg, _ -> showMessageActions(msg) },
            { msg -> chatRepository.toggleReaction(chatId!!, msg.messageId, "❤️") },
            { _, _ -> /* Poll voting logic if implemented in repository */ },
            { msg -> acceptDuelChallenge(msg) }
        )
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.adapter = adapter

        val swipeCallback = SwipeReplyCallback(this) { position ->
            adapter.getMessageAt(position)?.let { enterReplyMode(it) }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvMessages)
    }

    private fun acceptDuelChallenge(msg: ChatMessage) {
        val currentUid = auth.currentUser?.uid ?: return
        val cid = chatId ?: return
        
        // 1. Check if user has enough coins (need 50)
        userRepository.getUserById(currentUid) { user ->
            if ((user?.uniCoins ?: 0) < 50) {
                Toast.makeText(this, "Insufficient UniCoins! Need 50 to accept duel.", Toast.LENGTH_LONG).show()
                return@getUserById
            }

            // 2. Create a Duel Session in Firestore
            val duelId = "duel_${msg.messageId}"
            val duelSession = hashMapOf(
                "duelId" to duelId,
                "hostId" to msg.senderId,
                "receiverId" to currentUid,
                "chatId" to cid,
                "messageId" to msg.messageId,
                "subject" to (msg.duelSubject ?: "General"),
                "status" to "started",
                "startTime" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            db.collection("duels").document(duelId).set(duelSession)
                .addOnSuccessListener {
                    // Update original message status
                    chatRepository.updateDuelStatus(cid, msg.messageId, "active")

                    // 3. Start Quiz Activity
                    val intent = Intent(this, DuelQuizActivity::class.java).apply {
                        putExtra("duelId", duelId)
                        putExtra("subject", msg.duelSubject)
                        putExtra("isHost", false)
                        putExtra("partnerId", msg.senderId)
                        putExtra("chatId", cid)
                        putExtra("messageId", msg.messageId)
                    }
                    startActivity(intent)
                }
        }
    }

    private fun setupInputLogic() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.btnSend.visibility = if (hasText) View.VISIBLE else View.GONE
                binding.btnVoice.visibility = if (hasText) View.GONE else View.VISIBLE
                
                chatId?.let { id ->
                    chatRepository.setTypingStatus(id, hasText)
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
                    Toast.makeText(this, "Voice messages are a Premium feature! ✨", Toast.LENGTH_LONG).show()
                }
            }
        }
        binding.btnCloseReply.setOnClickListener { cancelReply() }
        
        binding.btnAttach.setOnClickListener { 
            pickImageLauncher.launch("image/*")
        }
    }

    private fun setupQuickActions() {
        binding.qaPoll.setOnClickListener { /* Open Poll Dialog */ }
        binding.qaDoubt.setOnClickListener { /* Open Doubt Dialog */ }
        binding.qaToken.setOnClickListener { 
            if (isGroup) {
                Toast.makeText(this, "Token transfers only in private chats", Toast.LENGTH_SHORT).show()
            } else {
                showTokenTransferDialog()
            }
        }
        binding.qaCode.setOnClickListener { /* Open Code Dialog */ }
        binding.qaDuel.setOnClickListener {
            if (isGroup) {
                Toast.makeText(this, "Duels only available in private chats", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showDuelRequestDialog()
        }
    }

    private fun showTokenTransferDialog() {
        val cid = chatId ?: return
        val rid = receiverId ?: return
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_token_transfer, null)
        val etAmount = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTokenAmount)
        val etNote = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTokenNote)
        
        AlertDialog.Builder(this)
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

    private fun showDuelRequestDialog() {
        val currentUid = auth.currentUser?.uid ?: return
        userRepository.getUserById(currentUid) { user ->
            if ((user?.uniCoins ?: 0) < 50) {
                Toast.makeText(this, "Need at least 50 UniCoins to start a duel!", Toast.LENGTH_LONG).show()
                return@getUserById
            }
            
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("Challenge to Duel")
            val subjects = arrayOf("Kotlin", "UI Design", "Data Structures", "Web Dev", "General")
            builder.setItems(subjects) { _, which ->
                val subject = subjects[which]
                chatId?.let { cid ->
                    receiverId?.let { rid ->
                        chatRepository.sendDuelRequest(cid, rid, 10, subject) { success ->
                            if (success) Toast.makeText(this, "Duel challenge sent!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            builder.setNegativeButton("Cancel", null)
            builder.show()
        }
    }

    private fun showMessageActions(msg: ChatMessage) {
        val options = mutableListOf("Reply", "Copy", "Forward")
        if (msg.senderId == auth.currentUser?.uid) {
            options.add("Edit")
            options.add("Delete")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Reply" -> enterReplyMode(msg)
                    "Copy" -> {
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("UniLink Message", msg.messageText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
                    }
                    "Forward" -> showForwardDialog(msg)
                    "Edit" -> enterEditMode(msg)
                    "Delete" -> {
                        chatId?.let { id ->
                            chatRepository.deleteMessage(id, msg.messageId) { success ->
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

        // Decrypt before forwarding
        val decryptedText = com.example.unilink.utils.EncryptionUtils.decrypt(msg.messageText, chatId!!)

        chatRepository.getRecentChats { rooms ->
            val adapter = com.example.unilink.adapters.ChatRoomAdapter(rooms, auth.currentUser?.uid ?: "") { room ->
                chatRepository.forwardMessage(room.chatId, decryptedText, currentUsername, room.isGroup, if (room.isGroup) null else room.participants.firstOrNull { it != auth.currentUser?.uid }) { success ->
                    if (success) {
                        Toast.makeText(this, "Message forwarded to ${if (room.isGroup) room.groupName else "student"}", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
            dialogBinding.rvForwardList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            dialogBinding.rvForwardList.adapter = adapter
        }
        dialog.show()
    }

    private fun enterReplyMode(msg: ChatMessage) {
        replyToMessage = msg
        binding.replyPreview.visibility = View.VISIBLE
        binding.tvReplyTarget.text = msg.messageText
        binding.etMessage.requestFocus()
    }

    private fun enterEditMode(msg: ChatMessage) {
        editingMessage = msg
        binding.etMessage.setText(msg.messageText)
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
            chatRepository.sendMessage(cid, text, currentUsername, isGroup, rid, replyToMessage?.messageId, replyToMessage?.messageText) {
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

    private fun listenForMessages() {
        chatId?.let { id ->
            messageListener = chatRepository.listenForMessages(id) { messages ->
                if (_binding == null) return@listenForMessages
                val wasAtBottom = !binding.rvMessages.canScrollVertically(1)
                adapter.updateMessages(messages)
                if (wasAtBottom && adapter.itemCount > 0) {
                    binding.rvMessages.post {
                        binding.rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
        }
    }

    private fun fetchCurrentUsername() {
        userRepository.getCurrentUser { user ->
            currentUsername = user?.username ?: "User"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageListener?.remove()
        chatRoomListener?.remove()
        _binding = null
    }
}
