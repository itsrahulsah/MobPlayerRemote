# Agent Instructions - MobPlayer Remote

## Core Responsibilities & Guidelines
- **Android Skill & Best Practices:** Follow modern Android development standards utilizing Kotlin (1.9+), Jetpack Compose (Material 3), Kotlin Coroutines & Flow, and Android Architecture Components (MVVM).
- **Documentation & Persistence:** Save all architectural context, requirements, protocol contracts, and implementation plans within this `.gemini/` folder.
- **Local-Only LAN Protocol:** Maintain strict fidelity to the local LAN control protocol defined in `APP_CONTROL_CONTRACT.md`. Do not introduce cloud dependencies or external third-party servers.
- **Dual-App Compatibility:** Ensure any protocol modifications or command enhancements are kept in lockstep with the TV host application located at `../MobPlayerTv`.
- **Mandatory Test Authoring:** Write unit/integration test cases in `app/src/test/` for every feature, bug fix, model change, or refactoring. Code is not complete without test coverage.
- **Mandatory Test Execution:** Always execute `./gradlew testDebugUnitTest` immediately after implementation. All tests must pass before declaring work complete.
