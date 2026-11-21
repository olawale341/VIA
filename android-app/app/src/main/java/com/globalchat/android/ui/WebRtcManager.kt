package com.globalchat.android.ui

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import org.webrtc.*
import java.util.concurrent.Executors

class WebRtcManager(
    context: Context,
    private val onLocalSdpReady: (isOffer: Boolean, sdp: String) -> Unit,
    private val onIceCandidateReady: (IceCandidate) -> Unit,
    private val onConnected: () -> Unit,
    private val onClosed: () -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    val eglBase: EglBase = EglBase.create()

    @Volatile
    private var factoryInitialized = false

    private val factory: PeerConnectionFactory by lazy {
        if (!factoryInitialized) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
            )
            factoryInitialized = true
        }
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var remoteDescSet = false
    private val pendingIce = mutableListOf<IceCandidate>()

    private fun setupPeerConnection(isVideo: Boolean, speakerOn: Boolean) {
        closeInternal()
        remoteDescSet = false
        pendingIce.clear()
        setSpeakerphone(speakerOn)

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) {
                    mainHandler.post { onConnected() }
                } else if (state == PeerConnection.IceConnectionState.FAILED || state == PeerConnection.IceConnectionState.CLOSED) {
                    mainHandler.post { onClosed() }
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onIceCandidateReady(it) }
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    mainHandler.post {
                        remoteRenderer?.let { track.addSink(it) }
                    }
                }
            }
        })

        try {
            audioSource = factory.createAudioSource(MediaConstraints())
            localAudioTrack = factory.createAudioTrack("GLOBALCHAT_AUDIO", audioSource)
            peerConnection?.addTrack(localAudioTrack, listOf("GLOBALCHAT_STREAM"))
        } catch (_: Exception) { }

        if (isVideo) {
            try {
                val enumerator = Camera2Enumerator(appContext)
                val camName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                    ?: enumerator.deviceNames.firstOrNull()
                if (camName != null) {
                    val capturer = enumerator.createCapturer(camName, null)
                    val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                    val vSource = factory.createVideoSource(capturer.isScreencast)
                    capturer.initialize(helper, appContext, vSource.capturerObserver)
                    capturer.startCapture(640, 480, 24)

                    videoCapturer = capturer
                    surfaceTextureHelper = helper
                    videoSource = vSource
                    localVideoTrack = factory.createVideoTrack("GLOBALCHAT_VIDEO", vSource).also { track ->
                        mainHandler.post { localRenderer?.let { track.addSink(it) } }
                        peerConnection?.addTrack(track, listOf("GLOBALCHAT_STREAM"))
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun startOutgoingCall(isVideo: Boolean) {
        executor.execute {
            setupPeerConnection(isVideo = isVideo, speakerOn = isVideo)
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
            }
            peerConnection?.createOffer(SimpleSdpObserver(onCreated = { sdp ->
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                onLocalSdpReady(true, sdp.description)
            }), constraints)
        }
    }

    fun answerIncomingCall(offerSdp: String, isVideo: Boolean) {
        executor.execute {
            setupPeerConnection(isVideo = isVideo, speakerOn = isVideo)
            val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
            peerConnection?.setRemoteDescription(SimpleSdpObserver(onSet = {
                remoteDescSet = true
                flushPendingIce()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
                }
                peerConnection?.createAnswer(SimpleSdpObserver(onCreated = { sdp ->
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                    onLocalSdpReady(false, sdp.description)
                }), constraints)
            }), remoteOffer)
        }
    }

    fun onRemoteAnswer(answerSdp: String) {
        executor.execute {
            val remoteAnswer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
            peerConnection?.setRemoteDescription(SimpleSdpObserver(onSet = {
                remoteDescSet = true
                flushPendingIce()
            }), remoteAnswer)
        }
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        executor.execute {
            if (remoteDescSet) peerConnection?.addIceCandidate(candidate)
            else pendingIce.add(candidate)
        }
    }

    private fun flushPendingIce() {
        pendingIce.forEach { peerConnection?.addIceCandidate(it) }
        pendingIce.clear()
    }

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        mainHandler.post {
            localRenderer = local
            remoteRenderer = remote
            local?.let { localVideoTrack?.addSink(it) }
            remote?.let { remoteVideoTrack?.addSink(it) }
        }
    }

    fun detachRenderers() {
        mainHandler.post {
            localRenderer?.let { localVideoTrack?.removeSink(it); it.release() }
            remoteRenderer?.let { remoteVideoTrack?.removeSink(it); it.release() }
            localRenderer = null
            remoteRenderer = null
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setSpeakerphone(speakerOn: Boolean) {
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = speakerOn
        } catch (_: Exception) { }
    }

    fun close() {
        executor.execute { closeInternal() }
    }

    private fun closeInternal() {
        remoteDescSet = false
        pendingIce.clear()
        try { videoCapturer?.stopCapture() } catch (_: Exception) { }
        try { videoCapturer?.dispose() } catch (_: Exception) { }
        videoCapturer = null
        try { surfaceTextureHelper?.dispose() } catch (_: Exception) { }
        surfaceTextureHelper = null
        try { videoSource?.dispose() } catch (_: Exception) { }
        videoSource = null
        try { audioSource?.dispose() } catch (_: Exception) { }
        audioSource = null
        localVideoTrack = null
        remoteVideoTrack = null
        localAudioTrack = null
        try { peerConnection?.close(); peerConnection?.dispose() } catch (_: Exception) { }
        peerConnection = null
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        } catch (_: Exception) { }
    }

    private class SimpleSdpObserver(
        private val onCreated: ((SessionDescription) -> Unit)? = null,
        private val onSet: (() -> Unit)? = null
    ) : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) { if (sdp != null) onCreated?.invoke(sdp) }
        override fun onSetSuccess() { onSet?.invoke() }
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}