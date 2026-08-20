package com.example.WEB.signaling

import com.example.WEB.signaling.model.IceCandidatePayload
import com.example.WEB.signaling.model.SdpPayload
import kotlinx.coroutines.flow.SharedFlow

interface SignalingClient {
    val events: SharedFlow<SignalingEvent>
    
    fun sendOffer(payload: SdpPayload)
    fun sendAnswer(payload: SdpPayload)
    fun sendIceCandidate(payload: IceCandidatePayload)
    fun sendHangup()
    fun disconnect()
}

sealed class SignalingEvent {
    data class ConnectionStateChange(val isConnected: Boolean) : SignalingEvent()
    data class OfferReceived(val payload: SdpPayload) : SignalingEvent()
    data class AnswerReceived(val payload: SdpPayload) : SignalingEvent()
    data class IceCandidateReceived(val payload: IceCandidatePayload) : SignalingEvent()
    object HangupReceived : SignalingEvent()
    data class PeerJoined(val userId: String) : SignalingEvent()
    data class PeerLeft(val userId: String) : SignalingEvent()
    data class ErrorReceived(val message: String) : SignalingEvent()
}
