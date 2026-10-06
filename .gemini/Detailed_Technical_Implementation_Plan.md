# MobPlayer Remote - Detailed Technical Implementation Plan

## Phase 1: Project Initialization & Core Infrastructure
**Status:** Completed
- [x] Configure Gradle with Kotlin DSL, plugins, and dependencies (`app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`).
- [x] Establish package structure: `model`, `network`, `repository`, `viewmodel`, `ui`.
- [x] Implement `AuthTokenRepository` utilizing `EncryptedSharedPreferences` with AndroidX MasterKey AES-256 encryption.
- [x] Configure `AndroidManifest.xml` with permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `VIBRATE`).

## Phase 2: Network Service Discovery (NSD)
**Status:** Completed
- [x] Implement `NsdDiscoveryManager` wrapping Android `NsdManager` with Wi-Fi `MulticastLock`.
- [x] Filter service registrations starting with `MobPlayer TV` on `_http._tcp.`.
- [x] Implement `DeviceDiscoveryRepository` exposing `Flow<List<TvDevice>>`.
- [x] Build `DiscoveryViewModel` with 3-second discovery window timeout and pull-to-refresh.
- [x] Build `DiscoveryScreen` Compose UI with device cards and scanning indicators.

## Phase 3: WebSocket Transport & Data Contracts
**Status:** Completed
- [x] Define `WebSocketMessage(type: String, payload: String)` envelope.
- [x] Define `PlayerCommandPayload(action, seekToMs, volume)`.
- [x] Define `PlayerStatePayload(isPlaying, positionMs, durationMs, volume)`.
- [x] Implement `ControlSocketRepository` using Ktor Client WebSockets with 15-second ping keepalive.
- [x] Expose `connectionState: StateFlow<ConnectionState>` and `incomingMessages: SharedFlow<WebSocketMessage>`.

## Phase 4: Authentication State Machine
**Status:** Completed
- [x] Implement `AuthViewModel` with `AuthStep` states (`Idle`, `Connecting`, `PinRequired`, `Authenticated`, `Failed`).
- [x] Automate `AUTH_REQUEST` with saved UUID token or `"no-token-yet"`.
- [x] Implement `PinAuthScreen` supporting 4-digit PIN input with auto-validation.
- [x] Handle `AUTH_SUCCESS` token storage and `AUTH_FAILED` error feedback.

## Phase 5: Remote Control UI & Real-Time Sync
**Status:** Completed
- [x] Implement `RemoteViewModel` observing `STATE_UPDATE` broadcasts.
- [x] Implement playback scrub bar with dynamic duration and formatted elapsed/total time (`mm:ss / mm:ss`).
- [x] Implement physical-style circular D-pad (Center = Play/Pause, Left/Right = Seek $\pm 10\text{s}$, Up/Down = Volume $\pm 5\%$).
- [x] Implement debounced (`50ms`) volume slider mapping to `SET_VOLUME`.
- [x] Implement `LoadMediaDialog` for casting direct video URLs with preset chips.
- [x] Integrated haptic feedback on button interactions using `LocalHapticFeedback`.

## Phase 6: Session Management & Edge Cases
**Status:** Completed
- [x] Implement `LifecycleEventObserver` in `MainActivity` with 3-minute grace keepalive on `ON_STOP`.
- [x] Handle connection conflict rejection (`"Connection rejected by TV"`) cleanly.
- [x] Clean session disconnect on app destruction or manual disconnect button press.

## Phase 7: Automated Testing Strategy
**Status:** Completed
- [x] Unit test JSON serialization and deserialization for all contract commands (`SerializationTest.kt`).
- [x] Unit test backward compatibility when `durationMs` is omitted (`SerializationTest.kt`).
- [x] Unit test ViewModel state operations, volume manipulation, and seeking (`RemoteViewModelTest.kt`).
- [x] Verified build and test tasks with `.\gradlew compileDebugKotlin` and `.\gradlew testDebugUnitTest`.

## Phase 8: TV test_client.html Feature Parity Integration
**Status:** Completed
- [x] Extended `PlayerCommandPayload` with `STOP` and `SET_SPEED` actions.
- [x] Extended `PlayerStatePayload` with `speed` property (retaining backward compatibility).
- [x] Added `UploadState` and `LogEvent` models.
- [x] Implemented `VideoUploadService` for memory-safe chunked multipart streaming to `http://<TV_IP>:8080/upload`.
- [x] Implemented `MediaUploadForegroundService` with `foregroundServiceType="dataSync"`, low-importance notification channel, and live progress bar.
- [x] Implemented raw WebSocket traffic logging in `ControlSocketRepository`.
- [x] Added `stop()`, `setPlaybackSpeed()`, `toggleMute()`, $\pm 30\text{s}$ seeks, video upload, and diagnostics to `RemoteViewModel`.
- [x] Updated `RemoteControlScreen` with Stop button, speed chips ($0.5\times - 2.0\times$), mute toggle, video picker launcher, upload progress card, and live diagnostics terminal bottom sheet.
- [x] Authored comprehensive unit tests for `STOP`, `SET_SPEED`, speed backwards compatibility, `RemoteViewModel` operations, and `VideoUploadService`.
- [x] Executed full test suite with `./gradlew testDebugUnitTest` verifying 20/20 tests passing (100% success rate, 0 failures, 0 errors).
