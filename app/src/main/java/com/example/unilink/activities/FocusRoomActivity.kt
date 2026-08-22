package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.unilink.databinding.ActivityFocusRoomBinding
import com.example.unilink.utils.ThemeUtils
import com.example.unilink.utils.HapticUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class FocusRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusRoomBinding
    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var timeLeftInMillis: Long = 1500000 // 25 mins
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var roomId: String? = null
    private var roomName: String? = null
    private var isZenMode = false
    private var isLofiPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityFocusRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomId = intent.getStringExtra("roomId") ?: "global_focus"
        roomName = intent.getStringExtra("roomName") ?: "Focus Room"
        
        binding.tvRoomName.text = roomName

        binding.btnToggleTimer.setOnClickListener {
            if (isTimerRunning) stopTimer() else startTimer()
        }
        
        binding.btnJoinChat.setOnClickListener {
            openRoomChat()
        }

        binding.btnZenMode.setOnClickListener {
            toggleZenMode()
        }

        binding.btnLofi.setOnClickListener {
            toggleLofi()
        }

        binding.btnAiPlan.setOnClickListener {
            generateAiStudyPlan()
        }
        
        updateUserStatus("Studying in $roomName")
    }

    private fun generateAiStudyPlan() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        input.hint = "e.g. Data Structures, UI Design"
        builder.setTitle("What are you studying?")
            .setView(input)
            .setPositiveButton("Generate Plan") { _, _ ->
                val subject = input.text.toString().trim()
                if (subject.isNotEmpty()) {
                    binding.btnAiPlan.isEnabled = false
                    binding.btnAiPlan.text = "Consulting UniAI..."
                    
                    lifecycleScope.launch {
                        val prompt = "Create a tight 25-minute study plan for $subject. Format as 3 clear bullet points."
                        val response = com.example.unilink.repository.GeminiRepository().getAiHint(prompt, true)
                        
                        androidx.appcompat.app.AlertDialog.Builder(this@FocusRoomActivity)
                            .setTitle("UniAI Study Buddy 📚")
                            .setMessage(response ?: "Let's just dive in and focus!")
                            .setPositiveButton("Start Timer") { _, _ -> startTimer() }
                            .show()
                        
                        binding.btnAiPlan.isEnabled = true
                        binding.btnAiPlan.text = "AI Study Plan"
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleLofi() {
        isLofiPlaying = !isLofiPlaying
        if (isLofiPlaying) {
            binding.btnLofi.text = "Lofi: On"
            binding.btnLofi.setIconResource(android.R.drawable.presence_audio_busy)
            Toast.makeText(this, "Lo-fi Beats started... 🎧", Toast.LENGTH_SHORT).show()
        } else {
            binding.btnLofi.text = "Lofi: Off"
            binding.btnLofi.setIconResource(android.R.drawable.presence_audio_online)
            Toast.makeText(this, "Lo-fi Beats stopped.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleZenMode() {
        isZenMode = !isZenMode
        if (isZenMode) {
            binding.btnZenMode.text = "Exit Zen Mode"
            binding.btnJoinChat.visibility = View.GONE
            binding.tvRoomName.visibility = View.GONE
            binding.tvStatus.visibility = View.GONE
            binding.btnToggleTimer.visibility = if (isTimerRunning) View.GONE else View.VISIBLE
            
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN 
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION 
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        } else {
            binding.btnZenMode.text = "Enter Zen Mode"
            binding.btnJoinChat.visibility = View.VISIBLE
            binding.tvRoomName.visibility = View.VISIBLE
            binding.tvStatus.visibility = View.VISIBLE
            binding.btnToggleTimer.visibility = View.VISIBLE
            
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun openRoomChat() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chatId", "focus_group_$roomId")
            putExtra("receiverName", "$roomName Chat")
            putExtra("isGroup", true)
            putExtra("receiverId", null as String?) // Explicitly ensure receiverId is null for group
        }
        startActivity(intent)
    }

    private fun startTimer() {
        isTimerRunning = true
        binding.btnToggleTimer.text = "Pause Session"
        
        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()
                
                // Haptic countdown for last 10 seconds
                if (millisUntilFinished <= 10000 && millisUntilFinished > 0) {
                    HapticUtils.playSelection(binding.tvTimer)
                }
            }

            override fun onFinish() {
                isTimerRunning = false
                HapticUtils.playSuccess(binding.tvTimer)
                Toast.makeText(this@FocusRoomActivity, "Session Complete! +5 UniCoins", Toast.LENGTH_LONG).show()
                rewardUser(5)
                timeLeftInMillis = 1500000
                updateTimerText()
                binding.btnToggleTimer.text = "Start Session"
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
        isTimerRunning = false
        binding.btnToggleTimer.text = "Resume Session"
    }

    private fun updateTimerText() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)
        binding.progressTimer.progress = ((timeLeftInMillis.toDouble() / 1500000.0) * 100).toInt()
    }

    private fun updateUserStatus(status: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("status", status)
    }

    private fun rewardUser(amount: Int) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("uniCoins", com.google.firebase.firestore.FieldValue.increment(amount.toLong()))
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        updateUserStatus("Open to Pair")
    }
}
