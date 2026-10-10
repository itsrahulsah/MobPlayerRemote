# AGENTS.md - Workspace Rules & Instructions

This repository enforces strict operational guidelines for all AI agents, subagents, and automated pair programmers working within this codebase.

---

## 1. Mandatory Test Creation for Every Change / Implementation

Every agent modifying code in this repository MUST write or update test cases for every change or implementation:

- **No Untested Logic:** Any new feature, bug fix, refactor, command payload, state property, repository function, or ViewModel logic MUST be accompanied by comprehensive unit or integration test cases.
- **Coverage Requirements:**
  - **Happy Paths:** Verify expected functionality with valid inputs.
  - **Edge Cases & Error Handling:** Test boundary values, nullability, unexpected inputs, and network/deserialization anomalies.
  - **Contract & Serialization Integrity:** Any modifications to JSON schemas, WebSocket protocols (`APP_CONTROL_CONTRACT.md`), or payloads (`WebSocketMessage`, `PlayerCommandPayload`, `PlayerStatePayload`) must include serialization/deserialization tests validating backward compatibility.
  - **State & Coroutines:** Test ViewModel state transitions and Flow emissions using `kotlinx.coroutines.test.runTest` and `StandardTestDispatcher`.
- **Test File Organization:** Place test files in `app/src/test/java/com/mobplayer/remote/` mirroring the main source package structure. Follow the conventions in existing tests (e.g., `SerializationTest.kt`, `RemoteViewModelTest.kt`).

---

## 2. Mandatory Test Execution After Implementation

Every agent MUST execute the test suite after implementing any code changes and before considering the task complete:

- **Execution Command:**
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Verification Rule:**
  - The agent must run the command and inspect the test results.
  - Never declare an implementation complete, mark a task done, or present final results to the user without first verifying that all tests pass.
- **Zero-Failure Policy:**
  - If any test fails, the agent must diagnose the failure, fix the issue in the implementation or test, and re-run `./gradlew testDebugUnitTest`.
  - Repeat until all unit tests pass with 0 failures and 0 errors.
- **Reporting:** Include the test execution status and outcome in the response to the user.

---

## 3. General Project Guidelines

- **Architecture:** Android MVVM with Kotlin Coroutines & Flow, Jetpack Compose (Material 3).
- **Protocol:** Strict adherence to `APP_CONTROL_CONTRACT.md` for LAN WebSocket communication with `MobPlayerTv`.
- **Documentation:** Keep architectural and progress documentation updated in `.gemini/`.
