package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.unilink.R
import com.example.unilink.databinding.ActivityRandomMatchBinding
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import coil.load
import coil.transform.CircleCropTransformation

class RandomMatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRandomMatchBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userRepository = com.example.unilink.repository.UserRepository()
    
    private val handler = Handler(Looper.getMainLooper())
    private var isMatching = false
    private var matchListener: ListenerRegistration? = null
    private var myName: String = "Student"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityRandomMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnStartMatch.setOnClickListener {
            if (!isMatching) startMatching()
        }

        binding.btnCancelMatch.setOnClickListener {
            stopMatching()
        }

        // Pre-fetch my name for faster matching
        userRepository.getCurrentUser { user ->
            myName = user?.name ?: "Student"
            user?.profileImageUrl?.let {
                binding.ivMyAvatar.load(it) {
                    transformations(CircleCropTransformation())
                }
            }
        }

        listenForMatch()
    }

    private fun listenForMatch() {
        val uid = auth.currentUser?.uid ?: return
        // Listen for matches where I am the receiver (someone found me)
        matchListener = db.collection("random_matches").document(uid).addSnapshotListener { snapshot, e ->
            if (isFinishing || isDestroyed) return@addSnapshotListener
            if (e != null) {
                Log.e("RandomMatch", "Listen failed", e)
                return@addSnapshotListener
            }
            
            if (snapshot != null && snapshot.exists() && isMatching) {
                val hostId = snapshot.getString("hostId") ?: return@addSnapshotListener
                val hostName = snapshot.getString("hostName") ?: "Student"
                val callId = snapshot.getString("callId") ?: "${hostId}_$uid"
                
                Log.d("RandomMatch", "Received match request from $hostName ($hostId) with callId $callId")
                
                // Cleanup the match record
                db.collection("random_matches").document(uid).delete()
                
                finalizeMatch(hostId, hostName, false, callId)
            }
        }
    }

    private fun startMatching() {
        val uid = auth.currentUser?.uid ?: return
        isMatching = true
        
        binding.btnStartMatch.visibility = View.GONE
        binding.btnCancelMatch.visibility = View.VISIBLE
        binding.tvStatus.text = "Initializing radar..."
        binding.viewPulse.visibility = View.VISIBLE
        
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.viewPulse.startAnimation(pulse)

        db.collection("users").document(uid).update(
            "isLookingForRandomMatch", true
        ).addOnSuccessListener {
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            binding.tvStatus.text = "Searching for students..."
            findMatch()
        }
    }

    private fun findMatch() {
        if (!isMatching || isFinishing || isDestroyed) return
        val myUid = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereEqualTo("isLookingForRandomMatch", true)
            .limit(10)
            .get()
            .addOnSuccessListener { snapshots ->
                if (!isMatching) return@addOnSuccessListener
                
                val potentialMatches = snapshots.documents.filter { 
                    it.id != myUid && it.getBoolean("isLookingForRandomMatch") == true 
                }

                if (potentialMatches.isNotEmpty()) {
                    val match = potentialMatches.random()
                    val matchId = match.id
                    val matchName = match.getString("name") ?: "Student"
                    val callId = "call_${myUid}_${matchId}_${System.currentTimeMillis()}"
                    
                    Log.d("RandomMatch", "Attempting transaction match with $matchName")

                    val matchRef = db.collection("random_matches").document(matchId)
                    
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(matchRef)
                        if (!snapshot.exists()) {
                            val matchData = mapOf(
                                "hostId" to myUid,
                                "hostName" to myName,
                                "receiverId" to matchId,
                                "receiverName" to matchName,
                                "callId" to callId,
                                "status" to "pending",
                                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            transaction.set(matchRef, matchData)
                            true // Success
                        } else {
                            false // Already taken
                        }
                    }.addOnSuccessListener { success ->
                        if (success && isMatching) {
                            finalizeMatch(matchId, matchName, true, callId)
                        } else {
                            // Retry soon
                            handler.postDelayed({ findMatch() }, 1500)
                        }
                    }.addOnFailureListener {
                        handler.postDelayed({ findMatch() }, 2000)
                    }
                } else {
                    handler.postDelayed({ findMatch() }, 3000)
                }
            }
            .addOnFailureListener {
                handler.postDelayed({ findMatch() }, 5000)
            }
    }

    private fun finalizeMatch(partnerUid: String, partnerName: String, isOffer: Boolean, callId: String) {
        if (!isMatching) return
        
        isMatching = false
        handler.removeCallbacksAndMessages(null)

        val myUid = auth.currentUser?.uid ?: return
        
        // Turn off search status in background
        db.collection("users").document(myUid).update("isLookingForRandomMatch", false)
        
        if (!isFinishing && !isDestroyed) {
            binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            Toast.makeText(this, "Matched with $partnerName!", Toast.LENGTH_SHORT).show()
        }
        
        val intent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra("partnerId", partnerUid)
            putExtra("partnerName", partnerName)
            putExtra("channelId", callId) // Use the unique callId instead of predictable channelId
            putExtra("isOffer", isOffer)
        }
        startActivity(intent)
        finish()
    }

    private fun stopMatching() {
        val uid = auth.currentUser?.uid ?: return
        isMatching = false
        binding.btnStartMatch.visibility = View.VISIBLE
        binding.btnCancelMatch.visibility = View.GONE
        binding.tvStatus.text = "Ready to meet someone new?"
        binding.viewPulse.clearAnimation()
        binding.viewPulse.visibility = View.GONE
        
        db.collection("users").document(uid).update("isLookingForRandomMatch", false)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()
        stopMatching()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        matchListener?.remove()
        stopMatching()
    }
}
