# Project Notes

## Design references
The project references both a general design document and a system design document describing the intended product scope and architecture.

## Current notes
- The app concept remains centered around family coordination.
- The Family App is now a **two-platform product**: native Android (Jetpack Compose + Material 3,
  package `com.sandnes.familyapp`) and native iOS (SwiftUI, iOS 26 Liquid Glass), sharing one
  Supabase backend. Repo: `android/` + `ios/` + shared `supabase/`, `maestro/`.
- The Android codebase is fully modernized — all legacy Java / Fragment / SQLite / Room code was
  removed; there is no transition state remaining.
- The native iOS app has shipped (merged to `master`); the **Android ⇄ iOS parity track**
  ([[../05_Implementation_Plan/Android Parity Track]]) is the active work.
- Build/tooling modernization is complete; AGP/Gradle/JDK alignment is a standing first-step
  requirement.
- Delivery is via `task -> test -> master` (never commit directly to `master`/`test`).
