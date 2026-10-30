package com.example.unilink.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.example.unilink.repository.CampusLiveStream
import com.example.unilink.ui.LiveStreamScreen
import com.example.unilink.utils.ThemeUtils
import com.example.unilink.webrtc.WebRtcClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

class LiveStreamActivity : ComponentActivity() {

    private val webRtcClientState = mutableStateOf<WebRtcClient?>(null)
    private val remoteVideoTrackState = mutableStateOf<VideoTrack?>(null)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var channelId = "1159_midnight_mic"
    private var isHost = false
    private var isRemoteSdpSet = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    private var remoteSdpListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var iceCandidatesListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (cameraGranted && audioGranted) {
            initWebRtc()
        } else {
            Toast.makeText(this, "Camera and Microphone permissions are required for live streaming", Toast.LENGTH_LONG).show()
        }
    }

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {
            webRtcClientState.value?.setLocalDescription(sdp, this)
            val data = if (isHost) {
                mapOf(
                    "offer" to mapOf(
                        "type" to sdp.type.canonicalForm(),
                        "description" to sdp.description
                    ),
                    "hostId" to auth.currentUser?.uid,
                    "status" to "live"
                )
            } else {
                mapOf(
                    "answer" to mapOf(
                        "type" to sdp.type.canonicalForm(),
                        "description" to sdp.description
                    ),
                    "watcherId" to auth.currentUser?.uid,
                    "status" to "live"
                )
            }
            db.collection("calls").document(channelId).set(data, SetOptions.merge())
        }

        override fun onSetSuccess() {
            Log.d("LiveStream", "SDP Set Success")
            runOnUiThread {
                if (isRemoteSdpSet && pendingIceCandidates.isNotEmpty()) {
                    pendingIceCandidates.forEach { webRtcClientState.value?.addIceCandidate(it) }
                    pendingIceCandidates.clear()
                }
            }
        }

        override fun onCreateFailure(s: String?) {
            Log.e("LiveStream", "SDP Create Failure: $s")
        }

        override fun onSetFailure(s: String?) {
            Log.e("LiveStream", "SDP Set Failure: $s")
        }
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            val myUid = auth.currentUser?.uid ?: return
            val candidateData = hashMapOf(
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "candidate" to candidate.sdp,
                "senderId" to myUid
            )
            db.collection("calls").document(channelId).collection("candidates").add(candidateData)
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            super.onTrack(transceiver)
            val track = transceiver?.receiver?.track()
            if (track is VideoTrack) {
                runOnUiThread {
                    remoteVideoTrackState.value = track
                }
            }
        }

        override fun onAddStream(stream: MediaStream) {
            if (stream.videoTracks.isNotEmpty()) {
                runOnUiThread {
                    remoteVideoTrackState.value = stream.videoTracks[0]
                }
            }
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try { ThemeUtils.applyTheme(this) } catch (_: Exception) {}
        super.onCreate(savedInstanceState)

        setupAudioManager()

        val streamTitle = intent.getStringExtra("streamTitle") ?: "Campus Broadcast 🎙️"
        val broadcasterName = intent.getStringExtra("broadcasterName") ?: "Campus Host"
        val channelIdExtra = intent.getStringExtra("channelId")
        channelId = channelIdExtra ?: ""
        isHost = intent.getBooleanExtra("isHost", false)

        val initialStream = if (!channelIdExtra.isNullOrEmpty() && !isHost) {
            CampusLiveStream(
                streamId = channelIdExtra,
                title = streamTitle,
                hostName = broadcasterName,
                isLive = true
            )
        } else null

        if (!isHost && !channelIdExtra.isNullOrEmpty()) {
            initWebRtc()
        }

        val autoOpen = intent.getBooleanExtra("autoOpenCreate", false)

        setContent {
            LiveStreamScreen(
                streamTitle = streamTitle,
                broadcasterName = broadcasterName,
                webRtcClient = webRtcClientState.value,
                remoteVideoTrack = remoteVideoTrackState.value,
                isHost = isHost,
                initialStream = initialStream,
                initialShowCreateDialog = autoOpen,
                onNavigateBack = { finish() },
                onStartBroadcasting = { title, desc, isPrivate, newStreamId ->
                    isHost = true
                    channelId = newStreamId
                    if (checkPermissions()) {
                        initWebRtc()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                            )
                        )
                    }
                },
                onLocalVideoReady = {
                    if (isHost) {
                        webRtcClientState.value?.createOffer(sdpObserver)
                    }
                },
                onJoinStream = { stream ->
                    cleanupWebRtc()
                    channelId = stream.streamId
                    isHost = false
                    initWebRtc()
                },
                onWatchReplays = {
                    startActivity(Intent(this@LiveStreamActivity, VaultActivity::class.java))
                },
                onEndStream = {
                    cleanupWebRtc()
                    if (!channelIdExtra.isNullOrEmpty()) {
                        finish()
                    }
                },
            )
        }
    }

    private fun setupAudioManager() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.e("LiveStream", "Error setting audio manager", e)
        }
    }

    private fun resetAudioManager() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (_: Exception) {}
    }

    private fun initWebRtc() {
        try {
            if (webRtcClientState.value == null) {
                val client = WebRtcClient(this, peerConnectionObserver)
                val iceServers = listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                        .setUsername("openrelayproject")
                        .setPassword("openrelayproject")
                        .createIceServer(),
                    PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                        .setUsername("openrelayproject")
                        .setPassword("openrelayproject")
                        .createIceServer()
                )
                client.createPeerConnection(iceServers)
                webRtcClientState.value = client

                listenForRemoteSdp()
                listenForIceCandidates()
            }
        } catch (e: Exception) {
            Log.e("LiveStreamActivity", "Error initializing WebRTC", e)
        }
    }

    private fun listenForRemoteSdp() {
        if (channelId.isEmpty()) return
        remoteSdpListener?.remove()
        remoteSdpListener = db.collection("calls").document(channelId).addSnapshotListener { snapshot, e ->
            if (isFinishing || isDestroyed) return@addSnapshotListener
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            if (isRemoteSdpSet) return@addSnapshotListener

            if (isHost) {
                val answer = snapshot.get("answer") as? Map<String, Any>
                if (answer != null) {
                    val description = answer["description"] as? String ?: return@addSnapshotListener
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, description)
                    webRtcClientState.value?.setRemoteDescription(sdp, sdpObserver)
                    isRemoteSdpSet = true
                }
            } else {
                val offer = snapshot.get("offer") as? Map<String, Any>
                if (offer != null) {
                    val description = offer["description"] as? String ?: return@addSnapshotListener
                    val sdp = SessionDescription(SessionDescription.Type.OFFER, description)
                    webRtcClientState.value?.setRemoteDescription(sdp, sdpObserver)
                    webRtcClientState.value?.createAnswer(sdpObserver)
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
                                    webRtcClientState.value?.addIceCandidate(candidate)
                                } else {
                                    pendingIceCandidates.add(candidate)
                                }
                            } catch (e: Exception) {
                                Log.e("LiveStream", "Error adding ICE candidate", e)
                            }
                        }
                    }
                }
            }
    }

    private fun checkPermissions(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) &&
               (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    private fun cleanupWebRtc() {
        remoteSdpListener?.remove()
        remoteSdpListener = null
        iceCandidatesListener?.remove()
        iceCandidatesListener = null
        isRemoteSdpSet = false
        pendingIceCandidates.clear()
        remoteVideoTrackState.value = null
        try {
            webRtcClientState.value?.close()
        } catch (e: Exception) {
            Log.e("LiveStreamActivity", "Error closing WebRtcClient", e)
        }
        webRtcClientState.value = null
    }

    override fun onDestroy() {
        super.onDestroy()
        resetAudioManager()
        cleanupWebRtc()
    }
}
