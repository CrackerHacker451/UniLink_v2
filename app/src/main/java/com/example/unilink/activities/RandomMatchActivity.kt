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
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.R
import com.example.unilink.databinding.ActivityRandomMatchBinding
import com.example.unilink.utils.ThemeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class RandomMatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRandomMatchBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userRepository = com.example.unilink.repository.UserRepository()

    private val handler = Handler(Looper.getMainLooper())
    private var isMatching = false
    private var searchAttempts = 0
    private var matchListener: ListenerRegistration? = null
    private var myName: String = "Student"
    private val skippedUserIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityRandomMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val skipPartnerId = intent.getStringExtra("skipPartnerId")
        if (!skipPartnerId.isNullOrEmpty()) {
            skippedUserIds.add(skipPartnerId)
        }

        binding.btnStartMatch.setOnClickListener {
            if (!isMatching) startMatching()
        }

        binding.btnSkipUser.setOnClickListener {
            com.example.unilink.utils.HapticUtils.playSelection(it)
            skipCurrentPeer()
        }

        binding.btnCancelMatch.setOnClickListener {
            stopMatching()
        }

        userRepository.getCurrentUser { user ->
            myName = user?.name ?: "Student"
            user?.profileImageUrl?.let {
                binding.ivMyAvatar.load(it) {
                    transformations(CircleCropTransformation())
                }
            }
        }

        listenForMatch()

        if (!skipPartnerId.isNullOrEmpty()) {
            startMatching()
        }
    }

    private fun listenForMatch() {
        val uid = auth.currentUser?.uid ?: return
        matchListener = db.collection("random_matches").document(uid).addSnapshotListener { snapshot, e ->
            if (isFinishing || isDestroyed) return@addSnapshotListener
            if (e != null) {
                Log.e("RandomMatch", "Listen failed", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists() && isMatching) {
                val hostId = snapshot.getString("hostId") ?: return@addSnapshotListener
                val hostName = snapshot.getString("hostName") ?: "Student"
                val sessionCallId = snapshot.getString("callId") ?: listOf(hostId, uid).sorted().joinToString("_vs_")

                Log.d("RandomMatch", "Received match request from $hostName ($hostId) with session $sessionCallId")

                db.collection("random_matches").document(uid).delete()

                val isCallerRole = (uid < hostId)
                finalizeMatch(hostId, hostName, isCallerRole, sessionCallId)
            }
        }
    }

    private fun startMatching() {
        val uid = auth.currentUser?.uid ?: return
        isMatching = true
        searchAttempts = 0

        binding.btnStartMatch.visibility = View.GONE
        binding.btnSkipUser.visibility = View.VISIBLE
        binding.btnCancelMatch.visibility = View.VISIBLE
        binding.tvStatus.text = "Initializing radar..."
        binding.viewPulse.visibility = View.VISIBLE

        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.viewPulse.startAnimation(pulse)

        db.collection("users").document(uid).update(
            "isLookingForRandomMatch", true,
            "isOnline", true
        ).addOnCompleteListener {
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            binding.tvStatus.text = "Searching for online students..."
            findMatch()
        }
    }

    private fun skipCurrentPeer() {
        if (!isMatching) {
            startMatching()
            return
        }
        handler.removeCallbacksAndMessages(null)
        binding.tvStatus.text = "Skipping user... Searching next peer ⏩"
        binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        findMatch()
    }

    private fun attemptTransactionMatch(targetUid: String, targetName: String) {
        val myUid = auth.currentUser?.uid ?: return

        val myUserRef = db.collection("users").document(myUid)
        val targetUserRef = db.collection("users").document(targetUid)

        db.runTransaction { transaction ->
            val targetSnap = transaction.get(targetUserRef)

            val isTargetInCall = targetSnap.getBoolean("inCall") == true
            val isTargetBusy = targetSnap.getString("currentMatchId") != null

            if (isTargetInCall || isTargetBusy) {
                throw com.google.firebase.firestore.FirebaseFirestoreException(
                    "Target peer is busy in another call",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                )
            }

            val deterministicSessionId = "session_${listOf(myUid, targetUid).sorted().joinToString("_vs_")}_${System.currentTimeMillis() / 60000}"

            transaction.update(myUserRef, mapOf("inCall" to true, "currentMatchId" to targetUid))
            transaction.update(targetUserRef, mapOf("inCall" to true, "currentMatchId" to myUid))

            val matchData = mapOf(
                "hostId" to myUid,
                "hostName" to myName,
                "receiverId" to targetUid,
                "receiverName" to targetName,
                "callId" to deterministicSessionId,
                "status" to "active",
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            transaction.set(db.collection("random_matches").document(targetUid), matchData)

            deterministicSessionId
        }.addOnSuccessListener { sessionId ->
            if (!isMatching || isFinishing || isDestroyed) return@addOnSuccessListener
            skippedUserIds.add(targetUid)
            val isCallerRole = (myUid < targetUid)
            finalizeMatch(targetUid, targetName, isCallerRole, sessionId)
        }.addOnFailureListener {
            Log.d("RandomMatch", "Atomic reservation aborted for $targetName, trying next peer...")
            findMatch()
        }
    }

    private fun findMatch() {
        if (!isMatching || isFinishing || isDestroyed) return
        val myUid = auth.currentUser?.uid ?: return

        searchAttempts++

        if (searchAttempts > 5) {
            isMatching = false
            binding.btnStartMatch.visibility = View.VISIBLE
            binding.btnStartMatch.text = "🔄 Retry Radar Scan"
            binding.btnSkipUser.visibility = View.GONE
            binding.btnCancelMatch.visibility = View.GONE
            binding.viewPulse.visibility = View.GONE
            binding.tvStatus.text = "No eligible peers available right now"
            binding.tvSubStatus.text = "Tap 'Retry Radar Scan' to check campus radar again!"
            return
        }

        val query = if (searchAttempts <= 2) {
            db.collection("users")
                .whereEqualTo("isOnline", true)
                .whereEqualTo("isLookingForRandomMatch", true)
                .limit(10)
        } else {
            db.collection("users")
                .whereEqualTo("isOnline", true)
                .limit(15)
        }

        query.get().addOnSuccessListener { snapshots ->
            if (!isMatching) return@addOnSuccessListener

            val potentialMatches = snapshots.documents.filter {
                val uid = it.id
                val inCall = it.getBoolean("inCall") == true
                val lastSeen = it.getTimestamp("lastSeenTimestamp")?.toDate()?.time ?: System.currentTimeMillis()
                val isRecent = (System.currentTimeMillis() - lastSeen) < 120000

                uid != myUid &&
                !skippedUserIds.contains(uid) &&
                !inCall &&
                isRecent
            }

            if (potentialMatches.isNotEmpty()) {
                val candidate = potentialMatches.random()
                val matchId = candidate.id
                val matchName = candidate.getString("name") ?: "Campus Peer"
                attemptTransactionMatch(matchId, matchName)
            } else {
                val statusMessages = arrayOf(
                    "Searching for online peers...",
                    "Scanning active students...",
                    "Checking campus radar...",
                    "Reserving eligible peer..."
                )
                binding.tvStatus.text = statusMessages[searchAttempts % statusMessages.size]
                handler.postDelayed({ findMatch() }, 1500)
            }
        }.addOnFailureListener {
            if (isMatching) {
                handler.postDelayed({ findMatch() }, 2000)
            }
        }
    }

    private fun finalizeMatch(partnerUid: String, partnerName: String, isOffer: Boolean, callId: String) {
        if (!isMatching) return

        isMatching = false
        handler.removeCallbacksAndMessages(null)

        val myUid = auth.currentUser?.uid
        if (myUid != null) {
            db.collection("users").document(myUid).update("isLookingForRandomMatch", false)
        }

        if (!isFinishing && !isDestroyed) {
            binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            Toast.makeText(this, "Matched with $partnerName!", Toast.LENGTH_SHORT).show()
        }

        val intent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra("partnerId", partnerUid)
            putExtra("partnerName", partnerName)
            putExtra("channelId", callId)
            putExtra("isOffer", isOffer)
        }
        startActivity(intent)
        finish()
    }

    private fun stopMatching() {
        val uid = auth.currentUser?.uid
        isMatching = false
        binding.btnStartMatch.visibility = View.VISIBLE
        binding.btnSkipUser.visibility = View.GONE
        binding.btnCancelMatch.visibility = View.GONE
        binding.tvStatus.text = "Ready to meet someone new?"
        binding.viewPulse.clearAnimation()
        binding.viewPulse.visibility = View.GONE

        if (uid != null) {
            db.collection("users").document(uid).update("isLookingForRandomMatch", false)
        }
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
