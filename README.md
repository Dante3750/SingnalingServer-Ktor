# WebRTC Video Call & Ktor Signaling Server

A professional-grade WebRTC implementation featuring a Ktor-based signaling server and a modern Android client built with Jetpack Compose, Clean Architecture, and Kotlin Coroutines.

## 🚀 Key Features

- **P2P Video & Audio**: Real-time communication using Google's WebRTC library.
- **Ktor Signaling**: High-performance WebSocket signaling server for session negotiation.
- **Perfect Negotiation**: Implemented the "Polite/Impolite" peer pattern to handle glare and state collisions gracefully.
- **WebRTC Data Channels**: 
    - **P2P Hangup**: Near-zero latency call termination.
    - **Emoji Chat**: Direct peer-to-peer data transfer for interactive emojis.
- **Media Optimization (QoS)**:
    - **Bitrate Capping**: Limited at 2Mbps for mobile stability.
    - **Degradation Preference**: Set to `MAINTAIN_FRAMERATE` to ensure fluid 30fps video even on poor networks.
    - **Adaptive Bitrate (ABR)**: Dynamic quality adjustment based on network conditions.
- **Clean Architecture**: Android client follows MVVM pattern with `SharedFlow` for reactive event handling.
- **Resilient Networking**: Automatic exponential backoff and reconnection logic for the signaling layer.

## 🛠 Tech Stack

### Android Client
- **UI**: Jetpack Compose
- **Concurrency**: Kotlin Coroutines & Flow
- **Networking**: OkHttp (WebSockets)
- **WebRTC**: `io.getstream:stream-webrtc-android` (Modern wrapper)
- **Dependency Management**: Gradle Version Catalog (libs.versions.toml)

### Signaling Server
- **Framework**: Ktor (Kotlin)
- **Transport**: WebSockets
- **Serialization**: Kotlinx Serialization

## 📦 Project Structure

```text
.
├── app/                  # Android Front-end Application
│   └── src/main/java     # Clean Architecture implementation
└── signaling-server/     # Ktor Signaling Back-end
    └── src/main/kotlin   # WebSocket routing and connection registry
```

## 🏃 How to Run

### 1. Start the Signaling Server
1. Navigate to the `signaling-server` directory.
2. Run the server using Gradle:
   ```bash
   ./gradlew run
   ```
3. The server will start on `0.0.0.0:8887`.

### 2. Configure and Run the Android App
1. Build and install the app on two physical devices.
2. Ensure both devices are on the **same WiFi network** as your computer.
3. Open the app and tap the **Pencil (Edit) icon** in the top bar.
4. Enter your computer's **Local IP Address** (e.g., `192.168.1.6`).
5. Ensure the **Room ID** is identical on both phones (e.g., `test-room`).
6. Once the status dot turns **Green (Online)**:
    - **Phone A**: Tap **"Start Call"**.
    - **Phone B**: Tap **"Accept"** on the incoming call popup.

## 🔍 Troubleshooting
- **Red Dot (Offline)**: Check your Windows Firewall. Ensure port `8887` is allowed for incoming TCP connections.
- **Frozen Video**: Ensure both phones are on a stable network. Check Logcat with tag `WebRTC-SIG` for signaling errors.

## 📝 License
MIT License - feel free to use this for your interviews or projects!
