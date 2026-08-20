package com.example.signaling

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class MessageRouter(private val registry: ConnectionRegistry) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handle(conn: Connection, raw: String) {
        val type = try {
            json.parseToJsonElement(raw).jsonObject["type"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }

        when (type) {
            "join" -> {
                val join = json.decodeFromString<Join>(raw)
                handleJoin(conn, join.roomId)
            }
            "leave" -> handleLeave(conn)
            "offer" -> {
                val offer = json.decodeFromString<Offer>(raw)
                if (tooLarge(offer.sdp, Limits.MAX_SDP_BYTES)) {
                    conn.send(json.encodeToString(ErrorMsg("SDP_TOO_LARGE", "offer.sdp exceeds limit")))
                    return
                }
                relay(offer.roomId, offer.to, json.encodeToString(offer.copy(from = conn.userId)))
            }
            "answer" -> {
                val answer = json.decodeFromString<Answer>(raw)
                if (tooLarge(answer.sdp, Limits.MAX_SDP_BYTES)) {
                    conn.send(json.encodeToString(ErrorMsg("SDP_TOO_LARGE", "answer.sdp exceeds limit")))
                    return
                }
                relay(answer.roomId, answer.to, json.encodeToString(answer.copy(from = conn.userId)))
            }
            "ice-candidate" -> {
                val ice = json.decodeFromString<IceCandidate>(raw)
                if (tooLarge(ice.candidate, Limits.MAX_CANDIDATE_BYTES)) {
                    conn.send(json.encodeToString(ErrorMsg("CANDIDATE_TOO_LARGE", "candidate exceeds limit")))
                    return
                }
                relay(ice.roomId, ice.to, json.encodeToString(ice.copy(from = conn.userId)))
            }
            "heartbeat" -> conn.send("""{"type":"pong"}""")
            else -> conn.send(json.encodeToString(ErrorMsg("UNKNOWN_TYPE", "unrecognized message type: $type")))
        }
    }

    private suspend fun handleJoin(conn: Connection, roomId: String) {
        registry.join(conn, roomId)
        
        // Snapshot for joiner
        val others = registry.participantsIn(roomId, exclude = conn.sessionId)
        val joinedMsg = Joined(
            roomId = roomId,
            participants = others.map { Participant(it.userId, it.displayName) }
        )
        conn.send(json.encodeToString(joinedMsg))

        // Tell others
        broadcast(roomId, exclude = conn.sessionId, 
            json.encodeToString(PeerJoined(roomId, conn.userId, conn.displayName)))
    }

    private suspend fun handleLeave(conn: Connection) {
        val roomId = conn.roomId ?: return
        registry.leaveImmediately(conn.sessionId)
        broadcast(roomId, exclude = conn.sessionId,
            json.encodeToString(PeerLeft(roomId, conn.userId, reason = "left")))
    }

    private suspend fun relay(roomId: String, targetUserId: String, payload: String) {
        registry.participantsIn(roomId)
            .firstOrNull { it.userId == targetUserId }
            ?.send(payload)
    }

    suspend fun broadcast(roomId: String, exclude: String?, payload: String) {
        registry.participantsIn(roomId, exclude = exclude)
            .forEach { it.send(payload) }
    }
}
