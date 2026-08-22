package com.example.unilink.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unilink.adapters.UserAdapter
import com.example.unilink.databinding.ActivityCreateGroupBinding
import com.example.unilink.models.User
import com.example.unilink.repository.ChatRepository
import com.example.unilink.repository.UserRepository

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private val userRepository = UserRepository()
    private val chatRepository = ChatRepository()
    private val selectedMembers = mutableListOf<String>()
    private var userList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        fetchUsers()

        binding.btnCreateGroup.setOnClickListener {
            createGroup()
        }
    }

    private fun setupRecyclerView() {
        val adapter = UserAdapter(userList) { user ->
            if (selectedMembers.contains(user.uid)) {
                selectedMembers.remove(user.uid)
                Toast.makeText(this, "${user.name} removed", Toast.LENGTH_SHORT).show()
            } else {
                selectedMembers.add(user.uid)
                Toast.makeText(this, "${user.name} added", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = adapter
    }

    private fun fetchUsers() {
        userRepository.getAllUsers { users ->
            if (isFinishing || isDestroyed) return@getAllUsers
            userList.clear()
            userList.addAll(users)
            binding.rvMembers.adapter?.notifyDataSetChanged()
        }
    }

    private fun createGroup() {
        val groupName = binding.etGroupName.text.toString().trim()
        if (groupName.isEmpty()) {
            binding.etGroupName.error = "Enter group name"
            return
        }
        if (selectedMembers.isEmpty()) {
            Toast.makeText(this, "Select at least one member", Toast.LENGTH_SHORT).show()
            return
        }

        chatRepository.createGroup(groupName, "", selectedMembers) { success, groupId ->
            if (isFinishing || isDestroyed) return@createGroup
            if (success) {
                Toast.makeText(this, "Group created successfully", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Failed to create group", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
