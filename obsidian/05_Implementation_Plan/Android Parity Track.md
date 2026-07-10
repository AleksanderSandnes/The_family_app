# Android ⇄ iOS Parity Track

**Active plan.** Branch `feat/android-ios-parity` (started 2026-07-10). Brings the Android app up
to the iOS-era feature set and the Liquid Glass look, after the iOS app shipped ahead (see
[[iOS Port Plan]]). Part of [[Implementation Plan]].

## Decisions (locked with the user)
- **Stay on Compose + Material 3**, upgrade to **Material 3 Expressive**. No rewrite — reuse all
  existing ViewModels and screens.
- **Glass layer via Haze** (`dev.chrisbanes.haze`) mirroring iOS `Glass.swift` — RenderEffect blur
  on API 31+ with a graceful fallback below.
- **Mirror the iOS look, adapt Android idioms:** native nav + predictive back, Material pickers,
  Google Maps (not MapKit), WorkManager reminders, Android share sheet, Coil (not Nuke).
- **Adopt the iOS tab-bar IA:** Home / Shopping / Chat / Calendar / Profile (Family / Map /
  Wishlists / Meals / Birthdays / Settings become pushed routes).
- **In-app EN/NB localization** on Android (independent of device locale).
- **CI/lint parity:** an Android detekt + spotless CI gate mirroring the iOS SwiftLint/SwiftFormat
  gate, plus Kotlin pre-commit hooks.
- All 11 iOS-era DB migrations are **already applied to production** (shared DB) — parity is
  client-side; M5 verifies via Supabase MCP.

## Milestones
| # | Milestone | Status |
|---|-----------|--------|
| M0 | Vault brought current (this update) | ⏳ in progress |
| M1 | Glass layer (Haze) + Material 3 Expressive + data-model additions | ⏳ |
| M2 | Tab-bar IA change (Home / Shopping / Chat / Calendar / Profile) | ⏳ |
| M3 | Per-feature parity (relations, wishlist share + PDF, calendar private/colour/attendees, colour pickers, birthday icon/colour, profile-completion prompt, background location) | ⏳ |
| M4 | In-app EN/NB localization | ⏳ |
| M5 | Backend verification via Supabase MCP (migrations, RLS) | ⏳ |
| M6 | Lint/CI gate + test-gap coverage | ⏳ |
| M7 | Final review | ⏳ |

## Feature targets (from iOS)
See [[../04_Features_and_Backlog/Feature Inventory]] for the full iOS-vs-Android matrix. The
Android-side gaps to close: in-app language switch + localization, directional family relations +
member popup, wishlist shareable links + PDF export, calendar private/colour/attendees, colour
pickers for meals/lists/wishlists, birthday custom icon/colour + creator-only edit, Google
profile-completion prompt, background location sharing, idempotent chat unread badge.

## Design reference
[[../03_Architecture_and_Design/Architecture and Design]] — Liquid Glass tokens (colours, SF Pro /
type scale, 4-pt spacing, radii card 20 / field 16 / button 18 / sheet 28 / tabBar 33) and the
planned Android glass layer.
