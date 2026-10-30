package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.unilink.databinding.ActivityFocusRoomBinding
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.NotificationHelper
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class FocusRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusRoomBinding
    private var timer: CountDownTimer? = null
    private var isTimerRunning = false

    private var totalSessionDurationMillis: Long = 1500000L // Default 25 mins
    private var timeLeftInMillis: Long = 1500000L
    private var targetEndTimeMillis: Long = 0L

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val bgHandler = Handler(Looper.getMainLooper())

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

        binding.progressTimer.setOnClickListener { showCustomizeTimerDialog() }
        binding.tvTimer.setOnClickListener { showCustomizeTimerDialog() }

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

        updateTimerText()
        updateUserStatus("Studying in $roomName")
    }

    private fun showCustomizeTimerDialog() {
        if (isTimerRunning) {
            Toast.makeText(this, "Pause session to customize timer", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf(
            "⏱️ 5 Minutes (Quick Focus)",
            "⏱️ 10 Minutes (Short Sprint)",
            "⏱️ 15 Minutes (Standard Focus)",
            "⏱️ 25 Minutes (Pomodoro Classic)",
            "⏱️ 45 Minutes (Deep Study)",
            "⏱️ 60 Minutes (Marathon)",
            "✏️ Custom Minutes..."
        )

        AlertDialog.Builder(this)
            .setTitle("Set Focus Timer Duration ⏱️")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setCustomMinutes(5)
                    1 -> setCustomMinutes(10)
                    2 -> setCustomMinutes(15)
                    3 -> setCustomMinutes(25)
                    4 -> setCustomMinutes(45)
                    5 -> setCustomMinutes(60)
                    6 -> promptCustomMinutesInput()
                }
            }
            .show()
    }

    private fun promptCustomMinutesInput() {
        val input = EditText(this).apply {
            hint = "Enter minutes (e.g. 30)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(this)
            .setTitle("Custom Timer (Minutes)")
            .setView(input)
            .setPositiveButton("Set Duration") { _, _ ->
                val mins = input.text.toString().toIntOrNull() ?: 25
                val clampedMins = mins.coerceIn(1, 180)
                setCustomMinutes(clampedMins)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setCustomMinutes(minutes: Int) {
        totalSessionDurationMillis = minutes * 60 * 1000L
        timeLeftInMillis = totalSessionDurationMillis
        updateTimerText()
        Toast.makeText(this, "Timer set to $minutes minutes!", Toast.LENGTH_SHORT).show()
    }

    private fun generateAiStudyPlan() {
        val input = EditText(this).apply {
            hint = "e.g. Data Structures, UI Design"
        }
        AlertDialog.Builder(this)
            .setTitle("What are you studying?")
            .setView(input)
            .setPositiveButton("Generate Plan") { _, _ ->
                val subject = input.text.toString().trim()
                if (subject.isNotEmpty()) {
                    binding.btnAiPlan.isEnabled = false
                    binding.btnAiPlan.text = "Consulting UniAI..."

                    lifecycleScope.launch {
                        val durationMins = (totalSessionDurationMillis / 60000).toInt()
                        val prompt = "Create a tight $durationMins-minute study plan for $subject. Format as 3 clear bullet points."
                        val response = com.example.unilink.repository.GeminiRepository().getAiHint(prompt, true)

                        AlertDialog.Builder(this@FocusRoomActivity)
                            .setTitle("UniAI Study Buddy 📚")
                            .setMessage(response ?: "Let's dive in and focus!")
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

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        } else {
            binding.btnZenMode.text = "Enter Zen Mode"
            binding.btnJoinChat.visibility = View.VISIBLE
            binding.tvRoomName.visibility = View.VISIBLE
            binding.tvStatus.visibility = View.VISIBLE
            binding.btnToggleTimer.visibility = View.VISIBLE

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun openRoomChat() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chatId", "focus_group_$roomId")
            putExtra("receiverName", "$roomName Chat")
            putExtra("isGroup", true)
            putExtra("receiverId", null as String?)
        }
        startActivity(intent)
    }

    private fun startTimer() {
        isTimerRunning = true
        binding.btnToggleTimer.text = "Pause Session"
        targetEndTimeMillis = System.currentTimeMillis() + timeLeftInMillis

        timer?.cancel()
        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()

                if (millisUntilFinished <= 10000 && millisUntilFinished > 0) {
                    HapticUtils.playSelection(binding.tvTimer)
                }
            }

            override fun onFinish() {
                onSessionComplete()
            }
        }.start()

        scheduleBackgroundCompletionCheck()
    }

    private fun scheduleBackgroundCompletionCheck() {
        bgHandler.removeCallbacksAndMessages(null)
        val delay = targetEndTimeMillis - System.currentTimeMillis()
        if (delay > 0) {
            bgHandler.postDelayed({
                if (isTimerRunning && System.currentTimeMillis() >= targetEndTimeMillis) {
                    onSessionComplete()
                }
            }, delay)
        }
    }

    private fun onSessionComplete() {
        isTimerRunning = false
        val mins = (totalSessionDurationMillis / 60000).toInt()

        HapticUtils.playSuccess(binding.tvTimer)
        NotificationHelper.showNotification(
            applicationContext,
            "Focus Session Complete! 🏆",
            "Awesome job! You completed your $mins-minute $roomName focus session and earned +10 UniCoins!"
        )

        Toast.makeText(this, "Session Complete! +10 UniCoins 🏆", Toast.LENGTH_LONG).show()
        rewardUser(10)

        timeLeftInMillis = totalSessionDurationMillis
        updateTimerText()
        binding.btnToggleTimer.text = "Start Session"
    }

    private fun stopTimer() {
        timer?.cancel()
        bgHandler.removeCallbacksAndMessages(null)
        isTimerRunning = false
        binding.btnToggleTimer.text = "Resume Session"
    }

    private fun updateTimerText() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)
        val progress = ((timeLeftInMillis.toDouble() / totalSessionDurationMillis.toDouble()) * 100).toInt()
        binding.progressTimer.progress = progress.coerceIn(0, 100)
    }

    private fun updateUserStatus(status: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("status", status)
    }

    private fun rewardUser(amount: Int) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update(
            "uniCoins", FieldValue.increment(amount.toLong()),
            "totalXp", FieldValue.increment(20.toLong())
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        bgHandler.removeCallbacksAndMessages(null)
        updateUserStatus("Open to Pair")
    }
}
