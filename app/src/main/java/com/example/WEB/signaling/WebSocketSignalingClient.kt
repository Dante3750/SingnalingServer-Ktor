package com.example.WEB.signaling

import com.example.WEB.signaling.model.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import timber.log.Timber

class WebSocketSignalingClient(
    private val serverUrl: String,
    private val userId: String,
    private val roomId: String
) : SignalingClient {

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _events = MutableSharedFlow<SignalingEvent>(replay = 1, extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    private val client = OkHttpClient.Builder().build()
    private var webSocket: WebSocket? = null
    private var remotePeerId: String? = null
    private val sessionId = "sess-" + java.util.UUID.randomUUID().toString().substring(0, 8)
    private var isClosing = false

    init {
        connect()
    }

    private fun connect() {
        if (isClosing) return
        val url = if (serverUrl.endsWith("/ws")) serverUrl else "$serverUrl/ws"
        Timber.tag("WebRTC-SIG").d("Connecting to $url ...")
        
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.tag("WebRTC-SIG").i("WebSocket Connected. Sending Hello...")
                sendHello()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.tag("WebRTC-SIG").e("Connection Failed: ${t.message}")
                emitEvent(SignalingEvent.ConnectionStateChange(false))
                
                // Auto-reconnect logic
                scope.launch {
                    delay(3000)
                    Timber.tag("WebRTC-SIG").d("Attempting to reconnect...")
                    connect()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.tag("WebRTC-SIG").d("WebSocket Closing: $reason")
                emitEvent(SignalingEvent.ConnectionStateChange(false))
            }
        })
    }

    private fun emitEvent(event: SignalingEvent) {
        scope.launch { _events.emit(event) }
    }

    private fun sendHello() {
        val hello = JsonObject().apply {
            addProperty("sessionId", sessionId)
            addProperty("userId", userId)
            addProperty("displayName", android.os.Build.MODEL)
        }
        sendMessage(hello)
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                delay(10_000)
                webSocket?.send("""{"type":"heartbeat"}""")
            }
        }
    }

    private fun joinRoom() {
        val join = JsonObject().apply {
            addProperty("type", "join")
            addProperty("roomId", roomId)
        }
        sendMessage(join)
    }

    private fun handleMessage(text: String) {
        try {
            val root = gson.fromJson(text, JsonObject::class.java)
            val type = root.get("type")?.asString ?: ""
            
            if (type == "hello-ack") {
                Timber.tag("WebRTC-SIG").i("Handshake OK. Joining room...")
                joinRoom()
                startHeartbeat()
                return
            }

            if (type == "hangup") {
                Timber.tag("WebRTC-SIG").i("Received Hangup from peer")
                emitEvent(SignalingEvent.HangupReceived)
                return
            }

            // Detect 'joined' or participants list
            if (root.has("participants")) {
                val participants = root.getAsJsonArray("participants")
                participants?.forEach { 
                    val id = it.asJsonObject.get("userId").asString
                    if (id != userId) remotePeerId = id
                }
                Timber.tag("WebRTC-SIG").i("Joined room. Target is $remotePeerId")
                emitEvent(SignalingEvent.ConnectionStateChange(true))
                return
            }

            // Detect PeerJoined (Ktor model: PeerJoined(roomId, userId, displayName))
            if (root.has("userId") && root.has("displayName")) {
                val id = root.get("userId").asString
                if (id != userId) {
                    remotePeerId = id
                    emitEvent(SignalingEvent.PeerJoined(id))
                }
                return
            }

            // Detect PeerLeft (Ktor model: PeerLeft(roomId, userId, reason))
            if (root.has("userId") && (root.has("reason") || type == "peer-left")) {
                val id = root.get("userId").asString
                if (id == remotePeerId) remotePeerId = null
                emitEvent(SignalingEvent.PeerLeft(id))
                return
            }

            // WebRTC Logic
            if (root.has("sdp")) {
                val sdp = root.get("sdp").asString
                val from = root.get("from")?.asString
                if (from != null && from != userId) {
                    remotePeerId = from
                    if (sdp.contains("setup:actpass")) {
                        emitEvent(SignalingEvent.OfferReceived(SdpPayload("offer", sdp)))
                    } else {
                        emitEvent(SignalingEvent.AnswerReceived(SdpPayload("answer", sdp)))
                    }
                }
            } else if (root.has("candidate")) {
                val candidate = root.get("candidate").asString
                val sdpMid = root.get("sdpMid")?.asString ?: "0"
                val sdpMLineIndex = root.get("sdpMLineIndex")?.asInt ?: 0
                emitEvent(SignalingEvent.IceCandidateReceived(IceCandidatePayload(candidate, sdpMid, sdpMLineIndex)))
            }

        } catch (e: Exception) {
            Timber.tag("WebRTC-SIG").e("Error: ${e.message}")
        }
    }

    override fun sendOffer(payload: SdpPayload) {
        val target = remotePeerId ?: return
        val offer = Offer(roomId = roomId, to = target, sdp = payload.sdp)
        sendMessage(offer)
    }

    override fun sendAnswer(payload: SdpPayload) {
        val target = remotePeerId ?: return
        val answer = Answer(roomId = roomId, to = target, sdp = payload.sdp)
        sendMessage(answer)
    }

    override fun sendIceCandidate(payload: IceCandidatePayload) {
        val target = remotePeerId ?: return
        val ice = IceCandidate(
            roomId = roomId,
            to = target,
            candidate = payload.sdp,
            sdpMid = payload.sdpMid,
            sdpMLineIndex = payload.sdpMLineIndex
        )
        sendMessage(ice)
    }

    override fun sendHangup() {
        val target = remotePeerId ?: return
        val hangup = JsonObject().apply {
            addProperty("type", "hangup")
            addProperty("roomId", roomId)
            addProperty("to", target)
        }
        sendMessage(hangup)
    }

    override fun disconnect() {
        isClosing = true
        // Send leave before disconnecting
        val leave = JsonObject().apply { addProperty("type", "leave") }
        webSocket?.send(gson.toJson(leave))
        webSocket?.close(1000, "App closed")
        scope.cancel()
    }

    private fun sendMessage(obj: Any) {
        val json = gson.toJson(obj)
        val success = webSocket?.send(json) ?: false
        if (!success) {
            Timber.tag("WebRTC-SIG").e("Failed to send message: WebSocket not connected")
        }
    }
}
