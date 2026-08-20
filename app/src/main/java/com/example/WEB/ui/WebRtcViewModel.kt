package com.example.WEB.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WEB.signaling.SignalingClient
import com.example.WEB.signaling.SignalingEvent
import com.example.WEB.signaling.WebSocketSignalingClient
import com.example.WEB.signaling.model.IceCandidatePayload
import com.example.WEB.signaling.model.SdpPayload
import com.example.WEB.webrtc.WebRtcSessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import timber.log.Timber

sealed class WebRtcUiState {
    object Idle : WebRtcUiState()
    object Calling : WebRtcUiState()
    object IncomingCall : WebRtcUiState()
    object Active : WebRtcUiState()
}

class WebRtcViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<WebRtcUiState>(WebRtcUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _isSignalingConnected = MutableStateFlow(false)
    val isSignalingConnected = _isSignalingConnected.asStateFlow()

    private val _currentIp = MutableStateFlow("192.168.1.6")
    val currentIp = _currentIp.asStateFlow()

    private val _currentUserId = MutableStateFlow("user-" + java.util.UUID.randomUUID().toString().substring(0, 4))
    val currentUserId = _currentUserId.asStateFlow()

    private val _receivedEmoji = MutableStateFlow<String?>(null)
    val receivedEmoji = _receivedEmoji.asStateFlow()

    private var signalingClient: SignalingClient? = null
    private var signalingJob: kotlinx.coroutines.Job? = null
    private var pendingOffer: SessionDescription? = null
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    private val sessionManager = WebRtcSessionManager(
        context = application,
        onSendOffer = { sdp -> signalingClient?.sendOffer(SdpPayload("offer", sdp.description)) },
        onSendAnswer = { sdp -> signalingClient?.sendAnswer(SdpPayload("answer", sdp.description)) },
        onSendIceCandidate = { candidate ->
            signalingClient?.sendIceCandidate(IceCandidatePayload(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex))
        }
    ).apply {
        onDataChannelMessageReceived = { message ->
            if (message == "hangup") {
                onHangup(sendSignal = false)
            } else if (message.length <= 2) {
                onEmojiReceived(message)
            }
        }
    }

    init {
        updateConfig(_currentIp.value, "test-room", _currentUserId.value)
    }

    fun updateConfig(ip: String, room: String, userId: String) {
        _currentIp.value = ip
        _currentUserId.value = userId
        signalingJob?.cancel()
        signalingClient?.disconnect()
        
        signalingClient = WebSocketSignalingClient(serverUrl = "ws://$ip:8887", userId = userId, roomId = room)
        
        sessionManager.isPolite = false

        signalingJob = viewModelScope.launch {
            signalingClient?.events?.collect { event ->
                when (event) {
                    is SignalingEvent.ConnectionStateChange -> {
                        _isSignalingConnected.value = event.isConnected
                        if (!event.isConnected && _uiState.value != WebRtcUiState.Idle) {
                            Timber.tag("WebRTC-ViewModel").w("Signaling connection lost during call!")
                        }
                    }
                    is SignalingEvent.PeerJoined -> {
                        sessionManager.isPolite = userId < event.userId
                        Timber.tag("WebRTC-ViewModel").d("Peer joined. Am I polite? ${sessionManager.isPolite}")
                    }
                    is SignalingEvent.OfferReceived -> {
                        if (_uiState.value == WebRtcUiState.Idle) {
                            // Initial call: Show the popup
                            pendingOffer = SessionDescription(SessionDescription.Type.OFFER, event.payload.sdp)
                            _uiState.value = WebRtcUiState.IncomingCall
                        } else {
                            // Renegotiation: Handle it automatically in the background
                            Timber.tag("WebRTC-ViewModel").d("Silent Offer Received (Renegotiation)")
                            sessionManager.onOfferReceived(SessionDescription(SessionDescription.Type.OFFER, event.payload.sdp))
                        }
                    }
                    is SignalingEvent.AnswerReceived -> {
                        sessionManager.onAnswerReceived(SessionDescription(SessionDescription.Type.ANSWER, event.payload.sdp))
                        _uiState.value = WebRtcUiState.Active
                    }
                    is SignalingEvent.IceCandidateReceived -> {
                        val candidate = IceCandidate(event.payload.sdpMid, event.payload.sdpMLineIndex, event.payload.sdp)
                        if (_uiState.value != WebRtcUiState.Active) {
                            pendingIceCandidates.add(candidate)
                        } else {
                            sessionManager.addIceCandidate(candidate)
                        }
                    }
                    is SignalingEvent.PeerJoined -> {}
                    is SignalingEvent.PeerLeft -> { onHangup(sendSignal = false) }
                    is SignalingEvent.ErrorReceived -> { Timber.e(event.message) }
                    is SignalingEvent.HangupReceived -> { onHangup(sendSignal = false) }
                }
            }
        }
    }

    fun prepareSessionViews(local: SurfaceViewRenderer, remote: SurfaceViewRenderer) {
        try {
            sessionManager.setupViews(local, remote)
            
            if (_uiState.value != WebRtcUiState.Idle) {
                sessionManager.startCall()
                
                // If we are the caller, create the offer now that views are ready
                if (_uiState.value == WebRtcUiState.Calling) {
                    sessionManager.createOffer()
                }
                
                // If we are the receiver and have accepted, apply the offer
                if (_uiState.value == WebRtcUiState.Active) {
                    pendingOffer?.let { 
                        sessionManager.onOfferReceived(it)
                        pendingOffer = null 
                    }
                    pendingIceCandidates.forEach { sessionManager.addIceCandidate(it) }
                    pendingIceCandidates.clear()
                }
            }
        } catch (e: Exception) {
            Timber.tag("WebRTC-ViewModel").e(e, "Error in prepareSessionViews")
        }
    }

    fun onStartCall() {
        _uiState.value = WebRtcUiState.Calling
    }

    fun onAcceptCall() {
        _uiState.value = WebRtcUiState.Active
    }

    fun onHangup(sendSignal: Boolean = true) {
        if (sendSignal) {
            sessionManager.sendMessage("hangup")
            signalingClient?.sendHangup()
        }
        sessionManager.release()
        _uiState.value = WebRtcUiState.Idle
    }

    fun sendEmoji(emoji: String) {
        sessionManager.sendMessage(emoji)
        onEmojiReceived(emoji)
    }

    private fun onEmojiReceived(emoji: String) {
        _receivedEmoji.value = emoji
        viewModelScope.launch {
            delay(2000)
            _receivedEmoji.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionManager.release()
        signalingClient?.disconnect()
    }
}
