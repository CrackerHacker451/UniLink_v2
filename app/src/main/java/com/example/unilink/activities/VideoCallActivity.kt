package com.example.unilink.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.unilink.databinding.ActivityVideoCallBinding
import com.example.unilink.webrtc.WebRtcClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.webrtc.*

class VideoCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoCallBinding
    private var webRtcClient: WebRtcClient? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var channelId = ""
    private var isOffer = false
    private var isRemoteSdpSet = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    private var remoteSdpListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var iceCandidatesListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {
            webRtcClient?.setLocalDescription(sdp, this)
            val data = if (isOffer) {
                mapOf(
                    "offer" to mapOf(
                        "type" to sdp.type.canonicalForm(),
                        "description" to sdp.description
                    ),
                    "hostId" to auth.currentUser?.uid
                )
            } else {
                mapOf(
                    "answer" to mapOf(
                        "type" to sdp.type.canonicalForm(),
                        "description" to sdp.description
                    ),
                    "receiverId" to auth.currentUser?.uid
                )
            }
            db.collection("calls").document(channelId).set(data, com.google.firebase.firestore.SetOptions.merge())
        }

        override fun onSetSuccess() {
            Log.d("WebRTC", "SDP Set Success")
            runOnUiThread {
                if (isRemoteSdpSet && pendingIceCandidates.isNotEmpty()) {
                    Log.d("WebRTC", "Adding ${pendingIceCandidates.size} buffered remote ICE candidates")
                    pendingIceCandidates.forEach { webRtcClient?.addIceCandidate(it) }
                    pendingIceCandidates.clear()
                }
            }
        }

        override fun onCreateFailure(s: String?) {}
        override fun onSetFailure(s: String?) {}
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            if (isFinishing || isDestroyed) return
            val candidateData = hashMapOf(
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "candidate" to candidate.sdp,
                "senderId" to auth.currentUser?.uid
            )
            db.collection("calls").document(channelId)
                .collection("candidates").add(candidateData)
        }

        override fun onAddStream(stream: MediaStream) {
            Log.d("WebRTC", "Remote stream added: ${stream.id}")
            if (stream.videoTracks.isNotEmpty()) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    binding.tvCallStatus.visibility = android.view.View.GONE
                    stream.videoTracks[0].addSink(binding.remoteVideoView)
                }
            }
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
            Log.d("WebRTC", "ICE Connection Change: $p0")
            runOnUiThread {
                when (p0) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        binding.tvCallStatus.text = "Live"
                        Toast.makeText(this@VideoCallActivity, "Connection Established", Toast.LENGTH_SHORT).show()
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        Toast.makeText(this@VideoCallActivity, "Call failed to connect", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        finish()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Log.d("WebRTC", "ICE Connection Disconnected - waiting for recovery")
                    }
                    else -> {}
                }
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        channelId = intent.getStringExtra("channelId") ?: ""
        isOffer = intent.getBooleanExtra("isOffer", false)

        if (checkPermissions()) {
            initWebRtc()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 101)
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnEndCall.setOnClickListener { endCall() }
        binding.btnMute.setOnClickListener {
            webRtcClient?.toggleAudio()
            val isMuted = webRtcClient?.isAudioMuted() ?: false
            binding.btnMute.setImageResource(if (isMuted) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_btn_speak_now)
            Toast.makeText(this, if (isMuted) "Audio Muted" else "Audio Unmuted", Toast.LENGTH_SHORT).show()
        }
        binding.btnSwitchCamera.setOnClickListener {
            webRtcClient?.switchCamera()
        }
    }

    private fun endCall() {
        db.collection("calls").document(channelId).update("status", "ended")
            .addOnCompleteListener {
                finish()
            }
    }

    private fun initWebRtc() {
        webRtcClient = WebRtcClient(this, peerConnectionObserver)
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        webRtcClient?.createPeerConnection(iceServers)
        
        // Initialize remote view
        binding.remoteVideoView.init(webRtcClient?.getEglContext(), null)
        binding.remoteVideoView.setEnableHardwareScaler(true)

        webRtcClient?.startLocalVideo(binding.localVideoView)

        if (isOffer) {
            webRtcClient?.createOffer(sdpObserver)
        }
        
        listenForRemoteSdp()
        listenForIceCandidates()
    }

    private fun listenForRemoteSdp() {
        if (channelId.isEmpty()) return
        Log.d("WebRTC", "Listening for SDP on $channelId")
        remoteSdpListener?.remove()
        remoteSdpListener = db.collection("calls").document(channelId).addSnapshotListener { snapshot, e ->
            if (isFinishing || isDestroyed) return@addSnapshotListener
            if (e != null) {
                Log.e("WebRTC", "SDP Listen failed", e)
                return@addSnapshotListener
            }
            
            if (snapshot == null || !snapshot.exists() || snapshot.getString("status") == "ended") {
                // If call was active but doc is gone or status is ended, it means the other side ended it
                if (isRemoteSdpSet || snapshot?.getString("status") == "ended") {
                    runOnUiThread {
                        Toast.makeText(this@VideoCallActivity, "Call Ended", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                return@addSnapshotListener
            }

            if (isRemoteSdpSet) return@addSnapshotListener

            if (isOffer) {
                // Host listens for answer
                val answer = snapshot.get("answer") as? Map<String, Any>
                if (answer != null) {
                    val description = answer["description"] as? String ?: return@addSnapshotListener
                    Log.d("WebRTC", "Received remote Answer")
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, description)
                    webRtcClient?.setRemoteDescription(sdp, sdpObserver)
                    isRemoteSdpSet = true
                }
            } else {
                // Receiver listens for offer
                val offer = snapshot.get("offer") as? Map<String, Any>
                if (offer != null) {
                    val description = offer["description"] as? String ?: return@addSnapshotListener
                    Log.d("WebRTC", "Received remote Offer")
                    val sdp = SessionDescription(SessionDescription.Type.OFFER, description)
                    webRtcClient?.setRemoteDescription(sdp, sdpObserver)
                    webRtcClient?.createAnswer(sdpObserver)
                    isRemoteSdpSet = true
                }
            }
        }
    }

    private fun listenForIceCandidates() {
        if (channelId.isEmpty()) return
        Log.d("WebRTC", "Listening for candidates on $channelId")
        iceCandidatesListener?.remove()
        iceCandidatesListener = db.collection("calls").document(channelId)
            .collection("candidates").addSnapshotListener { snapshot, e ->
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (e != null) {
                    Log.e("WebRTC", "Candidate Listen failed", e)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val senderId = data["senderId"] as? String
                        
                        if (senderId != null && senderId != auth.currentUser?.uid) {
                            Log.d("WebRTC", "Remote ICE candidate received from $senderId")
                            try {
                                val candidate = IceCandidate(
                                    data["sdpMid"] as String,
                                    (data["sdpMLineIndex"] as Long).toInt(),
                                    data["candidate"] as String
                                )
                                if (isRemoteSdpSet) {
                                    webRtcClient?.addIceCandidate(candidate)
                                } else {
                                    Log.d("WebRTC", "Buffering remote ICE candidate until SDP is set")
                                    pendingIceCandidates.add(candidate)
                                }
                            } catch (e: Exception) {
                                Log.e("WebRTC", "Error adding ICE candidate", e)
                            }
                        }
                    }
                }
            }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initWebRtc()
            } else {
                Toast.makeText(this, "Camera and Microphone permissions are required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        remoteSdpListener?.remove()
        iceCandidatesListener?.remove()
        try {
            webRtcClient?.close()
            // Cleanup: host can delete after a delay or just leave it for TTL (if implemented)
            // For now, let's just make sure we leave the room properly
        } catch (e: Exception) {
            Log.e("VideoCall", "Error in onDestroy", e)
        }
    }
}
