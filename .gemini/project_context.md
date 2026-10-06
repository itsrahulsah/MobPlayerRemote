# MobPlayer Remote - Comprehensive Project Context

## 1. Project Overview
MobPlayer Remote is a companion Android mobile application designed to discover, pair with, and control the **MobPlayer TV** application over a local Wi-Fi network. It acts as a dedicated physical-style remote control, scrubber, and media caster with zero cloud or internet dependencies.

- **Companion Host App:** `MobPlayer TV` (`../MobPlayerTv`) running Ktor WebSocket server on port `8080`.
- **Target Platform:** Android Mobile (minSdk 24, compileSdk 34, targetSdk 34).
- **Primary Technologies:** Kotlin 1.9.22, Jetpack Compose (Material 3), Kotlin Coroutines & StateFlow, Ktor Client CIO WebSockets, Kotlinx Serialization, and Jetpack Security (`EncryptedSharedPreferences`).

---

## 2. Architecture & Core Components

```
+-------------------------------------------------------------------------+
|                              Compose UI                                 |
|  [DiscoveryScreen]       [PinAuthScreen]        [RemoteControlScreen]   |
+-------------------------------------------------------------------------+
                                   ▲
                                   │ StateFlow / UI Events
+----------------------------------┴--------------------------------------+
|                           ViewModel Layer                               |
|  [DiscoveryViewModel]      [AuthViewModel]          [RemoteViewModel]   |
+-------------------------------------------------------------------------+
                                   ▲
                                   │ Flow / Repositories
+----------------------------------┴--------------------------------------+
|                           Repository Layer                              |
|   [DeviceDiscoveryRepository] [AuthTokenRepository] [ControlSocketRepo] |
+-------------------------------------------------------------------------+
            │                            │                     │
            ▼                            ▼                     ▼
    Android NsdManager          EncryptedSharedPrefs       Ktor Client
     (_mobplayer._tcp.)          (MasterKey AES-256)      (ws://ip:8080)
```

### 2.1. Network Discovery (`NsdDiscoveryManager.kt` & `DeviceDiscoveryRepository.kt`)
- Uses Android's `NsdManager` with a Wi-Fi `MulticastLock` to discover active TV instances advertising `_mobplayer._tcp.` (resolves on mDNS to `_mobplayer._tcp.local`).
- Filters services matching service name prefix `MobPlayer TV` (to support indexed dynamic suffixes like `MobPlayer TV (1)`).
- Implements safe sequential service resolution with `Mutex` to prevent Android's concurrent `FAILURE_ALREADY_ACTIVE` resolve bug.
- Emits detailed Logcat telemetry for all discovery lifecycle stages (`onDiscoveryStarted`, `onServiceFound`, `onServiceResolved`, `onServiceLost`, and error conditions).
- Resolves host IPv4 address and port (default `8080`) into `TvDevice` domain models emitted over a reactive `Flow<List<TvDevice>>`.

### 2.2. WebSocket Client Transport (`ControlSocketRepository.kt`)
- Built on Ktor Client (CIO engine) with the `WebSockets` plugin.
- Maintains a 15-second heartbeat ping (`pingInterval = 15_000L`) to ensure low latency and early detection of network drops.
- Encapsulates connection lifecycle (`Disconnected`, `Connecting`, `Connected`, `Error`) exposed via `StateFlow<ConnectionState>`.
- Emits incoming server messages via `SharedFlow<WebSocketMessage>`.

### 2.3. Authentication State Machine (`AuthViewModel.kt` & `AuthTokenRepository.kt`)
- **Token Persistence:** Stores persistent UUID tokens in `EncryptedSharedPreferences` keyed by TV identifier.
- **Handshake Flow:**
  1. On WebSocket connection, client sends `AUTH_REQUEST` with saved UUID token or `"no-token-yet"`.
  2. If token is valid, TV grants `AUTH_SUCCESS` directly.
  3. If token is invalid or absent, TV sends `PIN_REQUIRED`.
  4. App presents `PinAuthScreen` prompting for 4-digit TV-displayed PIN.
  5. On submit, app sends `PIN_SUBMIT` with PIN string.
  6. On success (`AUTH_SUCCESS`), saves returned UUID token for future auto-connects and navigates to remote controls.
  7. On error or TV user rejection (`AUTH_FAILED`), displays a descriptive message (`"Invalid PIN"` or `"Connection rejected by TV"`).

### 2.4. Playback Controls & State Sync (`RemoteViewModel.kt`)
- **Continuous State Sync:** Consumes incoming `STATE_UPDATE` broadcasts from TV and decodes `PlayerStatePayload` (`isPlaying`, `positionMs`, `durationMs`, `volume`).
- **Interactive Scrubber:** Playback timeline slider dynamically mapped to `0..durationMs` displaying formatted elapsed/total time (`mm:ss / mm:ss`).
- **Debounced Volume Control:** Compose slider backed by a coroutine `debounce(50L)` pipeline to prevent WebSocket frame congestion while retaining optimistic local UI response.
- **D-Pad & Navigation:**
  - Center: Toggle Play/Pause
  - Left / Right: Seek relative $\pm 10\text{s}$
  - Up / Down: Adjust volume relative $\pm 5\%$
- **Media Caster:** Dialog allowing user to send direct video streaming URLs (MP4 / HLS / DASH) to the TV via `LOAD_MEDIA`.

### 2.5. Lifecycle & Keepalive Management (`MainActivity.kt`)
- Implements `LifecycleEventObserver` to monitor app backgrounding.
- On `ON_STOP`: launches a 3-minute grace timer. If the user does not resume within 3 minutes, closes the WebSocket cleanly (Code 1000) to preserve phone battery and free up the TV's single-controller slot.
- On `ON_START`: cancels the timer and keeps the active session alive.

---

## 3. Protocol Envelope & Data Schema

### 3.1. Envelope (`WebSocketMessage`)
```json
{
  "type": "STRING_MESSAGE_TYPE",
  "payload": "STRING_OR_ESCAPED_JSON_STRING"
}
```

### 3.2. Payload Models
- **`PlayerCommandPayload`**:
  ```json
  {
    "action": "PLAY | PAUSE | SEEK | SET_VOLUME",
    "seekToMs": 120000,
    "volume": 0.85
  }
  ```
- **`PlayerStatePayload`**:
  ```json
  {
    "isPlaying": true,
    "positionMs": 34520,
    "durationMs": 180000,
    "volume": 0.85
  }
  ```

---

## 4. Summary of Protocol Message Types

| Message Type | Direction | Payload Schema / Format | Description |
| :--- | :--- | :--- | :--- |
| `AUTH_REQUEST` | Remote -> TV | Token string or `"no-token-yet"` | Initiate authentication with existing token |
| `PIN_REQUIRED` | TV -> Remote | `""` | Server requests 4-digit PIN |
| `PIN_SUBMIT` | Remote -> TV | 4-digit PIN string (e.g. `"1234"`) | Submit PIN displayed on TV UI |
| `AUTH_SUCCESS` | TV -> Remote | UUID Token string | Authentication successful; save token |
| `AUTH_FAILED` | TV -> Remote | `"Invalid PIN"` or `"Connection rejected by TV"` | Auth failed or denied; connection closed |
| `COMMAND` | Remote -> TV | `{"action":"PLAY\|PAUSE\|SEEK\|SET_VOLUME", "seekToMs": Long?, "volume": Float?}` | Control playback or volume actions |
| `LOAD_MEDIA` | Remote -> TV | Video URL string | Stream new media item |
| `COMMAND_SUCCESS` | TV -> Remote | Action string | Acknowledgment of executed command |
| `STATE_UPDATE` | TV -> Remote | `{"isPlaying": Boolean, "positionMs": Long, "durationMs": Long, "volume": Float}` | Real-time state broadcast from TV ExoPlayer |
