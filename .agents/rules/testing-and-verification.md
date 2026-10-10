# Testing and Verification Rules

## Mandatory Test Writing for All Changes
All agents and subagents modifying code in this project MUST write unit/integration tests for every change or implementation:
1. Every new feature, logic update, bug fix, or refactor must have corresponding test coverage in `app/src/test/java/com/mobplayer/remote/`.
2. Cover positive cases, edge cases, error conditions, and state transitions.
3. Validate contract and serialization compatibility whenever protocol messages or models are modified.

## Mandatory Test Suite Execution
Immediately following the completion of any implementation:
1. Run `./gradlew testDebugUnitTest` to execute all unit tests.
2. Ensure 100% of tests pass before finalizing changes or returning results.
3. If failures occur, diagnose and resolve them, then re-run until all tests are green.
