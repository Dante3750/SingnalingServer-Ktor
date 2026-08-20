package com.example.WEB.webrtc

import android.content.Context
import org.webrtc.*
import timber.log.Timber

class WebRtcSessionManager(
    private val context: Context,
    private val onSendOffer: (SessionDescription) -> Unit,
    private val onSendAnswer: (SessionDescription) -> Unit,
    private val onSendIceCandidate: (IceCandidate) -> Unit
) {
    private var rootEglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null

    private fun getEglBase(): EglBase {
        return rootEglBase ?: EglBase.create().also { rootEglBase = it }
    }

    private fun getFactory(): PeerConnectionFactory {
        return peerConnectionFactory ?: run {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )
            
            val options = PeerConnectionFactory.Options()
            val encoderFactory = DefaultVideoEncoderFactory(getEglBase().eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(getEglBase().eglBaseContext)
            
            PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
                .also { peerConnectionFactory = it }
        }
    }

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoView: SurfaceViewRenderer? = null
    private var remoteVideoView: SurfaceViewRenderer? = null
    private var isInitialized = false
    private var makingOffer = false
    private var isSettingRemoteAnswerPending = false
    var isPolite = false // Determined by ViewModel based on User IDs

    private var dataChannel: DataChannel? = null
    private val dataChannelObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(p0: Long) {}
        override fun onStateChange() {
            Timber.tag("WebRTC-Data").d("DataChannel state changed: ${dataChannel?.state()}")
        }
        override fun onMessage(buffer: DataChannel.Buffer) {
            val data = buffer.data
            val bytes = ByteArray(data.remaining())
            data.get(bytes)
            val message = String(bytes)
            Timber.tag("WebRTC-Data").d("Received message: $message")
            onDataChannelMessageReceived?.invoke(message)
        }
    }

    var onDataChannelMessageReceived: ((String) -> Unit)? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    fun setupViews(local: SurfaceViewRenderer, remote: SurfaceViewRenderer) {
        if (this.localVideoView == local && this.remoteVideoView == remote) return
        
        this.localVideoView = local
        this.remoteVideoView = remote
        
        val eglContext = getEglBase().eglBaseContext
        
        try {
            local.init(eglContext, null)
            local.setEnableHardwareScaler(true)
            local.setMirror(true)
        } catch (e: Exception) {
            Timber.tag("WebRTC-Session").d("Local view init skip: ${e.message}")
        }
        
        try {
            remote.init(eglContext, null)
            remote.setEnableHardwareScaler(true)
        } catch (e: Exception) {
            Timber.tag("WebRTC-Session").d("Remote view init skip: ${e.message}")
        }
    }

    fun startCall() {
        if (isInitialized) return
        startLocalStreaming()
        createPeerConnection()
        isInitialized = true
    }

    private fun startLocalStreaming() {
        val videoSource = getFactory().createVideoSource(false)
        val videoCapturer = createVideoCapturer()
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", getEglBase().eglBaseContext)
        
        videoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = getFactory().createVideoTrack("VIDEO_TRACK_ID", videoSource)
        localVideoView?.let { localVideoTrack?.addSink(it) }

        val audioSource = getFactory().createAudioSource(MediaConstraints())
        localAudioTrack = getFactory().createAudioTrack("AUDIO_TRACK_ID", audioSource)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        return enumerator.deviceNames.find { enumerator.isFrontFacing(it) }
            ?.let { enumerator.createCapturer(it, null) }
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = getFactory().createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) { onSendIceCandidate(candidate) }
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    Timber.tag("WebRTC").d("Peer disconnected")
                }
            }
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                val track = receiver.track()
                if (track is VideoTrack) {
                    remoteVideoView?.let { track.addSink(it) }
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel) {
                dataChannel = dc
                dc.registerObserver(dataChannelObserver)
            }
            override fun onRenegotiationNeeded() {
                try {
                    makingOffer = true
                    createOffer()
                } catch (e: Exception) {
                    Timber.tag("WebRTC-Session").e(e, "Error during renegotiation")
                } finally {
                    makingOffer = false
                }
            }
        })
        
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("STREAM_ID")) }
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("STREAM_ID")) }
    }

    fun createOffer() {
        // Create Data Channel (Offerer side)
        val dcInit = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("messaging", dcInit)
        dataChannel?.registerObserver(dataChannelObserver)

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { onSendOffer(desc) }
                    override fun onSetFailure(p0: String?) {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
                applyMediaOptimizations() // Apply bitrate and quality optimizations
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun onOfferReceived(description: SessionDescription) {
        val pc = peerConnection
        if (pc == null) {
            Timber.tag("WebRTC-Session").e("PeerConnection is NULL in onOfferReceived")
            return
        }

        // Perfect Negotiation: Handle collision (Glare)
        val readyForOffer = !makingOffer && (pc.signalingState() == PeerConnection.SignalingState.STABLE || isPolite)
        if (!readyForOffer) {
            Timber.tag("WebRTC-Session").w("Glare detected! Ignoring offer because we are impolite or already making an offer.")
            return
        }

        Timber.tag("WebRTC-Session").d("Setting Remote Offer...")
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { 
                Timber.tag("WebRTC-Session").d("Remote Offer Set Success - Creating Answer")
                createAnswer() 
            }
            override fun onSetFailure(p0: String?) { Timber.tag("WebRTC-Session").e("Remote Offer Set Failure: $p0") }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, description)
    }

    private fun createAnswer() {
        val pc = peerConnection
        if (pc == null) {
            Timber.tag("WebRTC-Session").e("PeerConnection is NULL in createAnswer")
            return
        }
        Timber.tag("WebRTC-Session").d("Creating Answer...")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Timber.tag("WebRTC-Session").d("Answer onCreateSuccess")
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { 
                        Timber.tag("WebRTC-Session").d("Local Answer Set Success - Sending Answer")
                        onSendAnswer(desc) 
                    }
                    override fun onSetFailure(p0: String?) { Timber.tag("WebRTC-Session").e("Local Answer Set Failure: $p0") }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
                applyMediaOptimizations() // Apply bitrate and quality optimizations
            }
            override fun onCreateFailure(p0: String?) { Timber.tag("WebRTC-Session").e("Answer onCreateFailure: $p0") }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun onAnswerReceived(description: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, description)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    /**
     * Advanced: Applies Quality of Service (QoS) settings.
     * Sets a 2Mbps bitrate cap and prioritizes framerate over resolution.
     */
    private fun applyMediaOptimizations() {
        val pc = peerConnection ?: return
        val senders = pc.senders
        
        for (sender in senders) {
            if (sender.track() is VideoTrack) {
                val parameters = sender.parameters
                
                // 1. Bitrate Cap (2000 kbps is ideal for 720p mobile calls)
                for (encoding in parameters.encodings) {
                    encoding.maxBitrateBps = 2000 * 1000
                }
                
                // 2. Stutter Prevention (Keep video fluid even if it gets slightly blurry)
                parameters.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
                
                sender.parameters = parameters
                Timber.tag("WebRTC-Quality").d("Applied 2Mbps cap and MAINTAIN_FRAMERATE")
            }
        }
    }

    fun sendMessage(message: String) {
        val dc = dataChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val buffer = DataChannel.Buffer(
                java.nio.ByteBuffer.wrap(message.toByteArray()),
                false
            )
            dc.send(buffer)
        }
    }

    fun release() {
        try {
            dataChannel?.unregisterObserver()
            dataChannel?.dispose()
            dataChannel = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
            
            localVideoTrack?.removeSink(localVideoView)
            localVideoTrack?.dispose()
            localVideoTrack = null
            
            localAudioTrack?.dispose()
            localAudioTrack = null
            
            localVideoView?.release()
            remoteVideoView?.release()
            localVideoView = null
            remoteVideoView = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            rootEglBase?.release()
            rootEglBase = null

            isInitialized = false
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
