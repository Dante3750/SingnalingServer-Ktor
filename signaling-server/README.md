# SignalingServer-Ktor

A simple WebRTC signaling server built with [Ktor](https://ktor.io/) and Kotlin. This server facilitates communication between peers by routing WebRTC offers, answers, and ICE candidates.

## Features

- **WebSocket-based signaling**: Real-time communication via `/ws` endpoint.
- **Room management**: Peers can join rooms and get notified when others join or leave.
- **Message Routing**: Targeted message delivery for WebRTC handshakes (`offer`, `answer`, `ice-candidate`).
- **Connection Registry**: Handles session persistence and brief disconnects.
- **Static Testing Page**: Includes `tester.html` for basic connection testing.

## Prerequisites

- JDK 17 or higher
- Gradle

## Getting Started

### Run the server
You can run the server using the Gradle wrapper:

```bash
./gradlew run
```

The server starts on port `8887` by default.

### WebSocket API

Connect to `ws://localhost:8887/ws`.

1. **Hello**: Every connection must start with a `Hello` frame.
   ```json
   {
     "sessionId": "unique-session-id",
     "userId": "user-123",
     "displayName": "Alice"
   }
   ```
2. **Join Room**:
   ```json
   {
     "type": "join",
     "roomId": "room-abc"
   }
   ```
3. **Signaling**:
   - `offer`
   - `answer`
   - `ice-candidate`

## Testing
Open `http://localhost:8887/` in your browser to access the built-in tester page.

## Project Structure
- `Application.kt`: Server entry point and Ktor configuration.
- `MessageRouter.kt`: Logic for handling and routing incoming socket messages.
- `ConnectionRegistry.kt`: Manages active connections and room state.
- `Models.kt`: Data classes for JSON serialization.
