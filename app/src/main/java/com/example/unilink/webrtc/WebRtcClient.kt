package com.example.unilink.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*

class WebRtcClient(
    private val context: Context,
    private val observer: PeerConnection.Observer
) {
    private val rootEglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var isMuted = false

    init {
        try {
            initPeerConnectionFactory(context)
            peerConnectionFactory = createPeerConnectionFactory()
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error initializing PeerConnectionFactory", e)
        }
    }

    private fun initPeerConnectionFactory(context: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        try {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error creating PeerConnection", e)
        }
    }

    private fun createAudioConstraints(): MediaConstraints {
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
        return audioConstraints
    }

    fun startLocalVideo(surfaceView: SurfaceViewRenderer, onVideoStarted: (() -> Unit)? = null) {
        try {
            surfaceView.setEnableHardwareScaler(false)
            surfaceView.setMirror(true)

            if (localVideoTrack != null) {
                try { localVideoTrack?.removeSink(surfaceView) } catch (_: Exception) {}
                localVideoTrack?.addSink(surfaceView)
                onVideoStarted?.invoke()
                return
            }

            val capturer = createVideoCapturer()
            this.videoCapturer = capturer
            localVideoSource = peerConnectionFactory?.createVideoSource(false)

            capturer?.initialize(
                SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext),
                context,
                localVideoSource?.capturerObserver
            )

            // Full High Definition 1080p @ 30 FPS Capture
            try {
                capturer?.startCapture(1920, 1080, 30)
            } catch (e1: Exception) {
                try {
                    capturer?.startCapture(1280, 720, 30)
                } catch (e2: Exception) {
                    try {
                        capturer?.startCapture(640, 480, 30)
                    } catch (e3: Exception) {
                        Log.e("WebRtcClient", "Failed to start camera capture", e3)
                    }
                }
            }

            localVideoTrack = peerConnectionFactory?.createVideoTrack("100", localVideoSource)
            localVideoTrack?.setEnabled(true)
            localVideoTrack?.addSink(surfaceView)

            localAudioSource = peerConnectionFactory?.createAudioSource(createAudioConstraints())
            localAudioTrack = peerConnectionFactory?.createAudioTrack("101", localAudioSource)
            localAudioTrack?.setEnabled(true)

            // Add tracks to PeerConnection using Unified Plan with SEND_RECV direction and 4.5 Mbps Full HD bitrate target
            if (localVideoTrack != null) {
                val transceiverInit = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, listOf("mediaStream"))
                val transceiver = peerConnection?.addTransceiver(localVideoTrack, transceiverInit)
                val rtpSender = transceiver?.sender ?: peerConnection?.addTrack(localVideoTrack, listOf("mediaStream"))
                try {
                    val params = rtpSender?.parameters
                    if (params != null && params.encodings.isNotEmpty()) {
                        params.encodings[0].maxBitrateBps = 4500 * 1000
                        params.encodings[0].minBitrateBps = 1500 * 1000
                        rtpSender.parameters = params
                    }
                } catch (_: Exception) {}
            }
            if (localAudioTrack != null) {
                val transceiverInit = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, listOf("mediaStream"))
                peerConnection?.addTransceiver(localAudioTrack, transceiverInit)
            }

            onVideoStarted?.invoke()
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error starting local video", e)
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return try {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            for (deviceName in deviceNames) {
                if (enumerator.isFrontFacing(deviceName)) {
                    val capturer = enumerator.createCapturer(deviceName, null)
                    if (capturer != null) return capturer
                }
            }
            for (deviceName in deviceNames) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
            null
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Failed to create video capturer", e)
            null
        }
    }

    fun createOffer(sdpObserver: SdpObserver) {
        try {
            val constraints = MediaConstraints()
            constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            peerConnection?.createOffer(sdpObserver, constraints)
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error creating offer", e)
        }
    }

    fun createAnswer(sdpObserver: SdpObserver) {
        try {
            val constraints = MediaConstraints()
            constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            peerConnection?.createAnswer(sdpObserver, constraints)
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error creating answer", e)
        }
    }

    fun setRemoteDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        try {
            peerConnection?.setRemoteDescription(sdpObserver, sdp)
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error setting remote description", e)
        }
    }

    fun setLocalDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        try {
            peerConnection?.setLocalDescription(sdpObserver, sdp)
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error setting local description", e)
        }
    }

    fun addIceCandidate(candidate: IceCandidate) {
        try {
            peerConnection?.addIceCandidate(candidate)
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error adding ice candidate", e)
        }
    }

    fun toggleAudio() {
        isMuted = !isMuted
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun isAudioMuted(): Boolean = isMuted

    fun switchCamera() {
        if (videoCapturer is CameraVideoCapturer) {
            (videoCapturer as CameraVideoCapturer).switchCamera(null)
        }
    }

    fun getEglContext(): EglBase.Context = rootEglBase.eglBaseContext

    fun close() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            localVideoTrack?.dispose()
            localAudioTrack?.dispose()
            localVideoSource?.dispose()
            localAudioSource?.dispose()
            peerConnection?.close()
            peerConnectionFactory?.dispose()
            rootEglBase.release()
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error during close", e)
        }
    }
}
