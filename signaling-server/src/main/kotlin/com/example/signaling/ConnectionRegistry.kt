package com.example.signaling

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

class ConnectionRegistry(private val scope: CoroutineScope) {

    private val sessions = ConcurrentHashMap<String, Connection>()
    private val roomMembers = ConcurrentHashMap<String, MutableSet<String>>()
    private val pendingLeaves = ConcurrentHashMap<String, Job>()

    fun get(sessionId: String): Connection? = sessions[sessionId]

    fun participantsIn(roomId: String, exclude: String? = null): List<Connection> =
        (roomMembers[roomId] ?: emptySet())
            .filter { it != exclude }
            .mapNotNull { sessions[it] }

    fun join(conn: Connection, roomId: String) {
        pendingLeaves.remove(conn.sessionId)?.cancel()
        sessions[conn.sessionId] = conn
        conn.roomId = roomId
        roomMembers.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(conn.sessionId)
    }

    fun leaveImmediately(sessionId: String) {
        val conn = sessions.remove(sessionId) ?: return
        conn.roomId?.let { roomMembers[it]?.remove(sessionId) }
        pendingLeaves.remove(sessionId)?.cancel()
    }

    fun scheduleDisconnect(
        sessionId: String,
        graceMillis: Long = 12_000,
        onFinalLeave: suspend (Connection) -> Unit
    ) {
        val conn = sessions[sessionId] ?: return
        val job = scope.launch {
            delay(graceMillis)
            val stillPending = pendingLeaves.remove(sessionId) != null
            if (stillPending) {
                sessions.remove(sessionId)
                conn.roomId?.let { roomMembers[it]?.remove(sessionId) }
                onFinalLeave(conn)
            }
        }
        pendingLeaves[sessionId] = job
    }

    fun isReconnectPending(sessionId: String): Boolean = pendingLeaves.containsKey(sessionId)
}
