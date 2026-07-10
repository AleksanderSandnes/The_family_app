# Architecture Notes (legacy stub)

> **Superseded.** This root note predates the Supabase migration and the iOS app. The current,
> authoritative architecture note is **[[03_Architecture_and_Design/Architecture and Design]]**
> (Android Compose + Supabase, iOS SwiftUI, the Liquid Glass design system, and the dual-ID rule).

## Snapshot (current, in brief)
- **Two platforms, one backend:** `android/` (Jetpack Compose + Material 3, single-Activity) and
  `ios/` (native SwiftUI, iOS 26 Liquid Glass) share one Supabase project.
- **No local database** on either app — Supabase (Postgres + Auth + Realtime + Storage) is the sole
  store; DataStore (Android) / `SessionStore` UserDefaults (iOS) hold lightweight session state.
- All legacy Java / Fragment / SQLite / Room code was removed long ago.

See [[03_Architecture_and_Design/Architecture and Design]] and
[[03_Architecture_and_Design/Backend Options and API Strategy]] for detail.
