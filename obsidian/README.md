# Household Vault

This vault stores project documentation, implementation notes, and planning for Household —
now a **two-platform product**: a native Android app (`android/`, Jetpack Compose + Material 3)
and a native iOS app (`ios/`, SwiftUI, Liquid Glass) sharing one Supabase backend.

Start at **[[00 Home]]** for the dashboard and current status.

## Canonical notes (numbered folders)
- [[01_Project_Overview/Project Overview]]
- [[02_Build_and_Environment/Build and Environment]]
- [[03_Architecture_and_Design/Architecture and Design]]
- [[04_Features_and_Backlog/Feature Inventory]]
- [[05_Implementation_Plan/Implementation Plan]] · [[05_Implementation_Plan/Android Parity Track]] · [[05_Implementation_Plan/iOS Port Plan]]
- [[06_Notes_and_References/Project Notes]]

> The root-level `Architecture Notes`, `Implementation Plan`, and `Project Notes` are legacy
> stubs kept only for backlinks — the numbered-folder notes above are authoritative.

## Current status (summary)
- Full Android app: Kotlin + Jetpack Compose + Material 3 on Supabase (all legacy Java/Fragment/
  SQLite/Room removed).
- Native iOS app shipped (merged to `master`), leading on the newest features + Liquid Glass.
- Active work: the **[[05_Implementation_Plan/Android Parity Track]]**.
- Delivery workflow: `task → test → master` (never commit directly to `master`/`test`).
