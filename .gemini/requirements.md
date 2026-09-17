# MobPlayer Remote - Requirements & System Specifications

## 1. Functional Requirements

### 1.1. Discovery & Connection
- **FR-1:** Automatically discover MobPlayer TV devices on the local network via mDNS/NSD without requiring manual IP entry.
- **FR-2:** Support manual pull-to-refresh and display discovery progress with a 3-second auto-settle window.
- **FR-3:** Connect to the TV's WebSocket server at `ws://<TV_IP>:8080/control`.

### 1.2. Authentication & Pairing
- **FR-4:** Implement seamless auto-connection using persistent cryptographic UUID tokens stored securely in `EncryptedSharedPreferences`.
- **FR-5:** Prompt user for 4-digit PIN when connecting for the first time or when the token has been invalidated/rotated.
- **FR-6:** Provide instant feedback on PIN validation errors or remote connection rejection by the TV.

### 1.3. Playback Controls & Real-Time Sync
- **FR-7:** Support full playback transport actions: Play, Pause, Toggle, Relative Seek ($\pm 10\text{s}$), and Absolute Timeline Seek.
- **FR-8:** Display real-time playback progress bar with current elapsed and total media duration formatted as `mm:ss`.
- **FR-9:** Support volume control via interactive slider ($0.0 - 1.0$) and quick D-pad incremental adjustments ($\pm 5\%$).
- **FR-10:** Enable streaming arbitrary video URLs to the TV via a dedicated dialog with pre-populated sample video presets.

### 1.4. Network Resilience & Session Lifecycle
- **FR-11:** Keep connection alive while app is in the background for up to 3 minutes before cleanly disconnecting.
- **FR-12:** Handle unexpected connection drops or network switching gracefully without crashing.
- **FR-13:** Provide haptic feedback on button clicks and slider bounds.

---

## 2. Non-Functional Requirements

- **NFR-1 (Latency):** Command round-trip latency over local 5GHz/2.4GHz Wi-Fi must remain under $50\text{ms}$.
- **NFR-2 (Local-First):** Must operate completely offline on local area networks without any cloud API dependencies.
- **NFR-3 (Battery Optimization):** Zero background battery drain when the app is disconnected or minimized beyond 3 minutes.
- **NFR-4 (Security):** Tokens must be secured with Android Keystore backed hardware encryption (AES-256-GCM / AES-256-SIV).

---

## 3. Technology Stack & Dependencies

- **Language:** Kotlin 1.9.22
- **UI Framework:** Jetpack Compose (Material 3 BOM 2024.02.02)
- **Networking:** Ktor Client 2.3.9 (CIO engine, WebSockets)
- **Serialization:** `kotlinx-serialization-json` 1.6.3
- **Async Programming:** Kotlin Coroutines & StateFlow 1.8.0
- **Security:** AndroidX Security Crypto (`androidx.security:security-crypto-ktx:1.1.0-alpha06`)
- **Compatibility:** minSdk 24 (Android 7.0), targetSdk 34 (Android 14)
