package com.example.signaling

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.server.http.content.*
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.channels.ClosedReceiveChannelException

fun main() {
    embeddedServer(Netty, port = 8887, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = 64 * 1024
            masking = false
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }

        val registry = ConnectionRegistry(this)
        val router = MessageRouter(registry)
        val json = Json { ignoreUnknownKeys = true }

        routing {
            staticFiles("/", File(".")) {
                default("tester.html")
            }

            webSocket("/ws") {
                val helloFrame = incoming.receive() as? Frame.Text
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "expected hello"))

                val hello = try { json.decodeFromString<Hello>(helloFrame.readText()) } catch (e: Exception) {
                    return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "invalid hello"))
                }
                
                val conn = Connection(hello.sessionId, hello.userId, hello.displayName, this)

                val wasReconnecting = registry.isReconnectPending(conn.sessionId)
                send(Frame.Text("""{"type":"hello-ack","reconnected":$wasReconnecting}"""))

                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        router.handle(conn, frame.readText())
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // socket closed
                } finally {
                    handleSocketClosed(registry, router, conn)
                }
            }
        }
    }.start(wait = true)
}

private suspend fun handleSocketClosed(
    registry: ConnectionRegistry,
    router: MessageRouter,
    conn: Connection
) {
    val roomId = conn.roomId ?: return
    registry.scheduleDisconnect(conn.sessionId, graceMillis = 12_000) { finalConn ->
        router.broadcast(
            roomId,
            exclude = null,
            payload = Json.encodeToString(PeerLeft(roomId, finalConn.userId, reason = "disconnected"))
        )
    }
}
