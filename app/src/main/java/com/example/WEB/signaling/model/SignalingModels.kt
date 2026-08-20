package com.example.WEB.signaling.model

import com.google.gson.annotations.SerializedName

// We add the "type" field to every model so the server can route them correctly
// and the client can parse them easily.

data class Hello(
    val type: String = "hello",
    val sessionId: String,
    val userId: String,
    val displayName: String
)

data class Join(
    val type: String = "join",
    val roomId: String
)

data class Joined(
    val type: String = "joined",
    val roomId: String,
    val participants: List<Participant>
)

data class Participant(
    val userId: String,
    val displayName: String
)

data class PeerJoined(
    val type: String = "peer-joined",
    val roomId: String,
    val userId: String,
    val displayName: String
)

data class PeerLeft(
    val type: String = "peer-left",
    val roomId: String,
    val userId: String,
    val reason: String
)

data class Offer(
    val type: String = "offer",
    val roomId: String,
    val to: String,
    val from: String? = null,
    val sdp: String
)

data class Answer(
    val type: String = "answer",
    val roomId: String,
    val to: String,
    val from: String? = null,
    val sdp: String
)

data class IceCandidate(
    val type: String = "ice-candidate",
    val roomId: String,
    val to: String,
    val from: String? = null,
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int?
)

data class ErrorMsg(
    val type: String = "error",
    val code: String,
    val message: String
)

// Legacy payloads for WebRTC logic compatibility
data class SdpPayload(
    val type: String,
    val sdp: String
)

data class IceCandidatePayload(
    val sdp: String,
    val sdpMid: String,
    val sdpMLineIndex: Int
)
