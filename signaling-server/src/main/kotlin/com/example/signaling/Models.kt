package com.example.signaling

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import java.time.Instant

// --- 1. Message Shapes ---

@Serializable
data class Hello(
    val sessionId: String,
    val userId: String,
    val displayName: String
)

@Serializable
data class Join(val roomId: String)

@Serializable
data class Joined(val roomId: String, val participants: List<Participant>)

@Serializable
data class Participant(val userId: String, val displayName: String)

@Serializable
data class PeerJoined(val roomId: String, val userId: String, val displayName: String)

@Serializable
data class PeerLeft(val roomId: String, val userId: String, val reason: String)

@Serializable
data class Offer(val roomId: String, val to: String, val from: String? = null, val sdp: String)

@Serializable
data class Answer(val roomId: String, val to: String, val from: String? = null, val sdp: String)

@Serializable
data class IceCandidate(
    val roomId: String,
    val to: String,
    val from: String? = null,
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int?
)

@Serializable
data class ErrorMsg(val code: String, val message: String)

// --- 2. Limits ---

object Limits {
    const val MAX_SDP_BYTES = 20_000
    const val MAX_CANDIDATE_BYTES = 1_000
}

fun tooLarge(text: String, max: Int) = text.toByteArray(Charsets.UTF_8).size > max

// --- 3. Connection Object ---

class Connection(
    val sessionId: String,
    val userId: String,
    var displayName: String,
    val socket: DefaultWebSocketServerSession
) {
    var roomId: String? = null
    suspend fun send(text: String) {
        socket.send(Frame.Text(text))
    }
}
