# MobPlayer Remote App - Detailed Implementation Plan

## Phase 1: Project Initialization & Core Infrastructure
**Objective:** Set up the foundational architecture, dependency injection, and project structure following MVVM and Jetpack Compose.

1.  **Gradle Configuration:**
    *   Configure `build.gradle.kts` for Android (minSdk 24, targetSdk 34+).
    *   Implement dependencies: Kotlin 1.9+, Jetpack Compose (Material 3), `kotlinx.coroutines`, `kotlinx.serialization`, Jetpack Security (EncryptedSharedPreferences), and Ktor Client (CIO) or OkHttp.
    *   Set up a Dependency Injection framework (e.g., Hilt or Koin) to provide singletons for networking and local storage repositories.
2.  **Package Structure:**
    *   Create domain-driven packages: `ui` (Compose screens), `viewmodel`, `repository` (Model layer), `network` (WebSocket & NSD clients), and `model` (Serialization classes).
3.  **Secure Storage Initialization:**
    *   Implement `AuthTokenRepository` utilizing `EncryptedSharedPreferences` to securely persist the UUID token required for session authentication[cite: 1].

## Phase 2: Network Service Discovery (NSD)
**Objective:** Enable the app to scan the local Wi-Fi network and discover active TV instances.

1.  **NSD Client Implementation:**
    *   Create an `NsdManager` wrapper class to handle the discovery of the `_http._tcp.` service type.
    *   Implement the `NsdManager.DiscoveryListener` and `NsdManager.ResolveListener` to filter services matching prefix `MobPlayer TV` (to accommodate network suffix indices like `MobPlayer TV (1)`).
2.  **Discovery Repository & State:**
    *   Create `DeviceDiscoveryRepository` exposing a `Flow<List<TvDevice>>`.
    *   Map the resolved `java.net.InetAddress` and port (default `8080`) to the `TvDevice` model.
3.  **Discovery UI Layer:**
    *   Build `DiscoveryViewModel` to collect the NSD flow.
    *   Implement `DiscoveryScreen` (Compose) to display a list of discovered TVs, a loading indicator for the 3-second discovery window, and a manual pull-to-refresh action.

## Phase 3: WebSocket Transport & Data Models
**Objective:** Establish the persistent bidirectional communication channel and define the JSON data contracts.

1.  **JSON Serialization Setup:**
    *   Define the base envelope `WebSocketMessage` containing `type` and `payload` (String).
    *   Create nested data classes for payloads:
        *   `PlayerCommandPayload` (`action`: `"PLAY" | "PAUSE" | "SEEK" | "SET_VOLUME"`, `seekToMs`: `Long?`, `volume`: `Float?`)
        *   `PlayerStatePayload` (`isPlaying`: `Boolean`, `positionMs`: `Long`, `durationMs`: `Long`, `volume`: `Float`)
2.  **WebSocket Client Wrapper:**
    *   Implement `ControlSocketRepository` using Ktor Client/OkHttp to connect to `ws://<RESOLVED_IP>:8080/control`.
    *   Configure a 15-second heartbeat (Ping interval) and a 15-second timeout.
    *   Expose incoming messages via a `SharedFlow<WebSocketMessage>` for ViewModels to consume.

## Phase 4: Authentication State Machine
**Objective:** Implement the PIN and Token pairing sequence to secure the LAN connection.

1.  **Authentication Flow Logic (`AuthViewModel`):**
    *   **On Connect:** Dispatch an `AUTH_REQUEST`. If `AuthTokenRepository` contains a token for the selected TV, attach it; otherwise, send `"no-token-yet"`.
    *   **PIN Challenge:** If the server returns `PIN_REQUIRED`, update the UI state to display the `PinAuthScreen`.
    *   **Submission:** User inputs the 4-digit PIN. Dispatch `PIN_SUBMIT` with the user's input.
    *   **Result Handling:** 
        *   On `AUTH_SUCCESS`: Extract the UUID token, save it to `EncryptedSharedPreferences`, and navigate to `RemoteControlScreen`.
        *   On `AUTH_FAILED`: Display a descriptive Snackbar based on payload (`"Invalid PIN"` or `"Connection rejected by TV"`), clear the input field, and reset the socket connection.

## Phase 5: Remote Control UI & Real-Time Sync
**Objective:** Build the physical remote interface and synchronize playback states.

1.  **State Synchronization & Scrub Bar:**
    *   In `RemoteViewModel`, observe `STATE_UPDATE` messages from the repository.
    *   Parse the escaped JSON string into `PlayerStatePayload` and emit it via `StateFlow` to update the Compose UI in real-time.
    *   Implement a playback scrub bar (Slider) bound to `positionMs` and `durationMs` displaying formatted elapsed/total time (`mm:ss / mm:ss`). On seek release, dispatch `COMMAND` with `action="SEEK"` and `seekToMs`.
2.  **Command Execution & D-Pad Shortcuts:**
    *   Implement D-pad and media playback buttons in `RemoteControlScreen`.
    *   Map D-pad and transport buttons to dispatch `COMMAND` messages:
        *   **Center / OK Button & Play/Pause**: Toggles `PLAY` / `PAUSE`.
        *   **Left D-Pad**: Seek backward by 10 seconds (`seekToMs = maxOf(0L, positionMs - 10_000L)`).
        *   **Right D-Pad**: Seek forward by 10 seconds (`seekToMs = minOf(durationMs, positionMs + 10_000L)`).
        *   **Up D-Pad**: Increase volume by 5% (`volume = minOf(1.0f, currentVolume + 0.05f)`).
        *   **Down D-Pad**: Decrease volume by 5% (`volume = maxOf(0.0f, currentVolume - 0.05f)`).
    *   Implement a direct URL dialog or drawer to dispatch `LOAD_MEDIA` messages with direct video URLs.
3.  **Volume Control & Debouncing:**
    *   Implement an interactive Compose `Slider` mapping to the `0.0` - `1.0` volume float scale.
    *   Apply a Kotlin Coroutines `debounce(50L)` operator on the volume slider's StateFlow within the ViewModel before dispatching `COMMAND` with `action="SET_VOLUME"` and `volume=it` to prevent flooding the WebSocket during rapid sliding.
4.  **Haptic Integration:**
    *   Inject an interface wrapping `Vibrator` or use Compose's `LocalHapticFeedback` to trigger subtle clicks on button presses and slider limit hits.

## Phase 6: Session Management & Edge Cases
**Objective:** Handle app lifecycle events, network resilience, and disconnects gracefully.

1.  **Background Keepalive:**
    *   Implement a `LifecycleEventObserver` in the main activity or ViewModel.
    *   On `ON_STOP`, launch a Coroutine with `delay(180_000)` (3 minutes). If the app does not reach `ON_START` before the delay completes, close the WebSocket cleanly (Code 1000).
2.  **Network Resilience & Auto-Reconnect:**
    *   Observe Android's `ConnectivityManager`. If Wi-Fi is lost, display a "Connection Lost" overlay.
    *   If the WebSocket drops unexpectedly, implement an exponential backoff routine (e.g., retry at 1s, 2s, 4s, up to 16s).
    *   If the socket cannot connect (e.g., due to DHCP IP change), silently trigger the NSD client to re-resolve the IP address based on the saved TV service name.
    *   Handle server connection conflict rejection cleanly without crash loops.

## Phase 7: Automated Testing Strategy
**Objective:** Validate architecture and network protocol reliability.

1.  **Unit Tests (JVM):**
    *   Test `AuthViewModel` state transitions using `kotlinx-coroutines-test` to simulate `AUTH_REQUEST`, `PIN_REQUIRED`, `AUTH_SUCCESS`, and `AUTH_FAILED` sequences.
    *   Verify `kotlinx.serialization` accurately parses edge-case JSON strings and properly escapes nested payloads for `PlayerCommandPayload` and `PlayerStatePayload`.
2.  **Integration Tests (MockWebServer):**
    *   Deploy an OkHttp `MockWebServer` to emulate the TV host.
    *   Write tests that connect to the local server, exchange `PIN_SUBMIT` and `AUTH_SUCCESS` messages, and verify that the socket remains open and processes mock `STATE_UPDATE` broadcasts.
3.  **Instrumentation Tests (Compose):**
    *   Use `createComposeRule()` to validate `PinAuthScreen` only accepts 4-digit numeric inputs.
    *   Verify that tapping the "Play" button in the UI triggers the correct `COMMAND` method in the mocked `RemoteViewModel`.