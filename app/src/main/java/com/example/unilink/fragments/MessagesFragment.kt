package com.example.unilink.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.R
import com.example.unilink.activities.ChatActivity
import com.example.unilink.adapters.ChatRoomAdapter
import com.example.unilink.databinding.FragmentMessagesBinding
import com.example.unilink.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    private val chatRepository = ChatRepository()
    private val userRepository = com.example.unilink.repository.UserRepository()
    private val auth = FirebaseAuth.getInstance()

    private var chatsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var allChats = listOf<com.example.unilink.models.ChatRoom>()
    private lateinit var chatAdapter: ChatRoomAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updatePadding(top = systemBars.top)
            insets
        }

        setupRecyclerView()
        setupSearch()
        fetchChats()

        binding.btnNewChat.setOnClickListener {
            if (_binding == null) return@setOnClickListener
            // Navigate to Search to find users to chat with
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SearchFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupRecyclerView() {
        if (_binding == null) return
        chatAdapter = ChatRoomAdapter(emptyList(), auth.currentUser?.uid ?: "") { room ->
            if (_binding == null) return@ChatRoomAdapter
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("chatId", room.chatId)
            intent.putExtra("chatName", if (room.isGroup) room.groupName else "Chat")
            intent.putExtra("isGroup", room.isGroup)
            if (!room.isGroup) {
                val otherUserId = room.participants.firstOrNull { it != auth.currentUser?.uid }
                intent.putExtra("receiverId", otherUserId)
            }
            startActivity(intent)
        }
        binding.rvChats.layoutManager = LinearLayoutManager(context)
        binding.rvChats.adapter = chatAdapter
    }

    private fun setupSearch() {
        binding.etSearchMessages.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChats(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun filterChats(query: String) {
        val lowerQuery = query.lowercase().trim()
        val filtered = allChats.filter { room ->
            if (room.isGroup) {
                room.groupName.lowercase().contains(lowerQuery) || room.lastMessage.lowercase().contains(lowerQuery)
            } else {
                room.lastMessage.lowercase().contains(lowerQuery) || 
                // We'll search in participants IDs as a fallback, but we should really search by name
                // For now, let's assume names are somehow reachable or the user searches by msg content
                room.participants.any { it.lowercase().contains(lowerQuery) }
            }
        }
        updateUI(filtered)
    }

    private fun fetchChats() {
        chatsListener?.remove()
        chatsListener = chatRepository.getRecentChats { chats ->
            if (_binding == null) return@getRecentChats
            userRepository.getCurrentUser { currentUser ->
                if (_binding == null) return@getCurrentUser
                val blockedByMe = currentUser?.blockedUsers ?: emptyList()
                
                // Filter out chats where the other person is blocked by me
                allChats = chats.filter { room ->
                    if (room.isGroup) true
                    else {
                        val otherId = room.participants.firstOrNull { it != auth.currentUser?.uid }
                        !blockedByMe.contains(otherId)
                    }
                }
                
                filterChats(binding.etSearchMessages.text.toString())
            }
        }
    }

    private fun updateUI(chats: List<com.example.unilink.models.ChatRoom>) {
        if (_binding == null) return
        chatAdapter.updateList(chats)
        binding.tvEmptyChats.visibility = if (chats.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatsListener?.remove()
        _binding = null
    }
}
