# Project Notes (legacy stub)

> **Superseded.** This root note predates the Supabase migration and the iOS app. See
> **[[06_Notes_and_References/Project Notes]]** and **[[01_Project_Overview/Project Overview]]** for
> the current picture.

## Current notes
- Household is a **two-platform product**: native Android (Compose + Material 3) and native
  iOS (SwiftUI, Liquid Glass) on one shared Supabase backend.
- The codebase is fully modernized — legacy Java / Fragment / SQLite / Room code was removed; there
  is no "transition" state remaining.
- The native iOS app has shipped; the Android ⇄ iOS parity track is the active work
  ([[05_Implementation_Plan/Android Parity Track]]).
- Delivery is via `task → test → master`.
