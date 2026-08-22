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
        initPeerConnectionFactory(context)
        peerConnectionFactory = createPeerConnectionFactory()
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
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
    }

    fun startLocalVideo(surfaceView: SurfaceViewRenderer) {
        surfaceView.init(rootEglBase.eglBaseContext, null)
        surfaceView.setEnableHardwareScaler(true)
        surfaceView.setMirror(true)

        val videoCapturer = createVideoCapturer()
        this.videoCapturer = videoCapturer
        localVideoSource = peerConnectionFactory?.createVideoSource(false)
        videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext), context, localVideoSource?.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory?.createVideoTrack("100", localVideoSource)
        localVideoTrack?.addSink(surfaceView)

        localAudioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("101", localAudioSource)

        val stream = peerConnectionFactory?.createLocalMediaStream("mediaStream")
        stream?.addTrack(localVideoTrack)
        stream?.addTrack(localAudioTrack)
        peerConnection?.addStream(stream)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun createOffer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(sdpObserver, constraints)
    }

    fun createAnswer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(sdpObserver, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        peerConnection?.setRemoteDescription(sdpObserver, sdp)
    }

    fun setLocalDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        peerConnection?.setLocalDescription(sdpObserver, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
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
