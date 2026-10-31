package com.example.unilink.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.load
import coil.transform.CircleCropTransformation
import com.example.unilink.databinding.ActivityVideoCallBinding
import com.example.unilink.webrtc.WebRtcClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

class VideoCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoCallBinding
    private var webRtcClient: WebRtcClient? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var channelId = ""
    private var isOffer = false
    private var partnerId = ""
    private var isRemoteSdpSet = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    private var callDurationSeconds = 0
    private val durationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val durationRunnable = object : Runnable {
        override fun run() {
            callDurationSeconds++
            val mins = callDurationSeconds / 60
            val secs = callDurationSeconds % 60
            binding.tvDuration.text = String.format("%02d:%02d", mins, secs)
            durationHandler.postDelayed(this, 1000)
        }
    }

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
                    "callerId" to auth.currentUser?.uid,
                    "status" to "active"
                )
            } else {
                mapOf(
                    "answer" to mapOf(
                        "type" to sdp.type.canonicalForm(),
                        "description" to sdp.description
                    ),
                    "calleeId" to auth.currentUser?.uid,
                    "status" to "active"
                )
            }
            if (channelId.isNotEmpty()) {
                db.collection("calls").document(channelId).set(data, SetOptions.merge())
            }
        }

        override fun onSetSuccess() {
            Log.d("WebRTC", "SDP Set Success")
            runOnUiThread {
                if (isRemoteSdpSet && pendingIceCandidates.isNotEmpty()) {
                    pendingIceCandidates.forEach { webRtcClient?.addIceCandidate(it) }
                    pendingIceCandidates.clear()
                }
            }
        }

        override fun onCreateFailure(s: String?) {
            Log.e("WebRTC", "SDP Create Failure: $s")
        }

        override fun onSetFailure(s: String?) {
            Log.e("WebRTC", "SDP Set Failure: $s")
        }
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            val myUid = auth.currentUser?.uid ?: return
            if (isFinishing || isDestroyed || channelId.isEmpty()) return

            val candidateData = hashMapOf(
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "candidate" to candidate.sdp,
                "senderId" to myUid
            )

            db.collection("calls").document(channelId)
                .collection("candidates").add(candidateData)
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            super.onTrack(transceiver)
            val track = transceiver?.receiver?.track()
            if (track is AudioTrack) {
                try {
                    track.setEnabled(true)
                    track.setVolume(1.0)
                } catch (e: Exception) {
                    Log.e("VideoCall", "Error enabling remote audio track", e)
                }
            }
            if (track is VideoTrack) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    track.addSink(binding.remoteVideoView)
                    setConnectedState()
                }
            }
        }

        override fun onAddStream(stream: MediaStream) {
            if (stream.audioTracks.isNotEmpty()) {
                try {
                    stream.audioTracks[0].setEnabled(true)
                    stream.audioTracks[0].setVolume(1.0)
                } catch (e: Exception) {
                    Log.e("VideoCall", "Error enabling remote audio stream", e)
                }
            }
            if (stream.videoTracks.isNotEmpty()) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    stream.videoTracks[0].addSink(binding.remoteVideoView)
                    setConnectedState()
                }
            }
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
            runOnUiThread {
                when (p0) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        setConnectedState()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.CLOSED,
                    PeerConnection.IceConnectionState.FAILED -> {
                        binding.tvCallStatus.text = "Disconnected"
                        binding.tvCallStatus.setTextColor(android.graphics.Color.RED)
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

        com.example.unilink.utils.NotificationHelper.cancelCallNotification(this)
        setupAudioManager()

        channelId = intent.getStringExtra("channelId") ?: ""
        isOffer = intent.getBooleanExtra("isOffer", false)
        val partnerName = intent.getStringExtra("partnerName") ?: "Campus Peer"
        partnerId = intent.getStringExtra("partnerId") ?: ""

        binding.tvPartnerName.text = partnerName
        if (partnerId.isNotEmpty()) {
            com.example.unilink.repository.UserRepository().getUserById(partnerId) { user ->
                if (!isFinishing && !isDestroyed) {
                    user?.profileImageUrl?.let {
                        binding.ivPartnerAvatar.load(it) {
                            transformations(CircleCropTransformation())
                        }
                    }
                }
            }
        }

        setupClickListeners()

        if (checkPermissions()) {
            initWebRtc()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    private fun setConnectedState() {
        binding.tvCallStatus.text = "Live Stream"
        binding.tvCallStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
        binding.tvDuration.visibility = android.view.View.VISIBLE
        startTimer()
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
            Toast.makeText(this, "Camera Switched 🔄", Toast.LENGTH_SHORT).show()
        }
        binding.btnSkipPeer.setOnClickListener {
            com.example.unilink.utils.HapticUtils.playSelection(it)
            durationHandler.removeCallbacks(durationRunnable)
            if (channelId.isNotEmpty()) {
                db.collection("calls").document(channelId).update("status", "skipped")
            }
            Toast.makeText(this, "Skipping to next peer... ⏭️", Toast.LENGTH_SHORT).show()
            val intent = android.content.Intent(this, RandomMatchActivity::class.java).apply {
                putExtra("skipPartnerId", partnerId)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun startTimer() {
        durationHandler.removeCallbacks(durationRunnable)
        durationHandler.postDelayed(durationRunnable, 1000)
    }

    private fun endCall() {
        durationHandler.removeCallbacks(durationRunnable)
        if (channelId.isNotEmpty()) {
            db.collection("calls").document(channelId).update("status", "ended_by_user")
        }
        Toast.makeText(this, "Call Ended", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun initWebRtc() {
        webRtcClient = WebRtcClient(this, peerConnectionObserver)
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )
        webRtcClient?.createPeerConnection(iceServers)

        try {
            binding.remoteVideoView.init(webRtcClient?.getEglContext(), null)
            binding.remoteVideoView.setEnableHardwareScaler(true)
            binding.remoteVideoView.setZOrderMediaOverlay(false)

            binding.localVideoView.init(webRtcClient?.getEglContext(), null)
            binding.localVideoView.setEnableHardwareScaler(true)
            binding.localVideoView.setZOrderMediaOverlay(true)
            binding.localVideoView.setMirror(true)
        } catch (e: Exception) {
            Log.e("VideoCall", "Error initializing SurfaceViewRenderers", e)
        }

        webRtcClient?.startLocalVideo(binding.localVideoView)

        if (isOffer) {
            webRtcClient?.createOffer(sdpObserver)
        }

        listenForRemoteSdp()
        listenForIceCandidates()
    }

    private fun listenForRemoteSdp() {
        if (channelId.isEmpty()) return
        remoteSdpListener?.remove()
        remoteSdpListener = db.collection("calls").document(channelId).addSnapshotListener { snapshot, e ->
            if (isFinishing || isDestroyed) return@addSnapshotListener
            if (e != null) return@addSnapshotListener

            if (isRemoteSdpSet || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            if (isOffer) {
                val answer = snapshot.get("answer") as? Map<String, Any>
                if (answer != null) {
                    val description = answer["description"] as? String ?: return@addSnapshotListener
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, description)
                    webRtcClient?.setRemoteDescription(sdp, sdpObserver)
                    isRemoteSdpSet = true
                }
            } else {
                val offer = snapshot.get("offer") as? Map<String, Any>
                if (offer != null) {
                    val description = offer["description"] as? String ?: return@addSnapshotListener
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
        val myUid = auth.currentUser?.uid ?: ""
        iceCandidatesListener?.remove()

        iceCandidatesListener = db.collection("calls").document(channelId)
            .collection("candidates").addSnapshotListener { snapshot, e ->
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (e != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val senderId = data["senderId"] as? String
                        if (senderId != myUid) {
                            try {
                                val candidate = IceCandidate(
                                    data["sdpMid"] as String,
                                    (data["sdpMLineIndex"] as Long).toInt(),
                                    data["candidate"] as String
                                )
                                if (isRemoteSdpSet) {
                                    webRtcClient?.addIceCandidate(candidate)
                                } else {
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

    @Deprecated("Deprecated in Java")
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

    private fun setupAudioManager() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = true

            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)
        } catch (e: Exception) {
            Log.e("VideoCall", "Error configuring AudioManager for voice", e)
        }
    }

    private fun resetAudioManager() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        resetAudioManager()
        durationHandler.removeCallbacks(durationRunnable)
        remoteSdpListener?.remove()
        iceCandidatesListener?.remove()
        try {
            webRtcClient?.close()
        } catch (e: Exception) {
            Log.e("VideoCall", "Error in onDestroy", e)
        }
    }
}
