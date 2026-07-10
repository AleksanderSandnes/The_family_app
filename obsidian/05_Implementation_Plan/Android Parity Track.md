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
| M0 | Vault moved into repo (`obsidian/`) + brought current | ✅ |
| M1 | Glass layer (Haze) + data-model additions (all 11 migrations' columns/models) | ✅ |
| M2 | Tab-bar IA change (Home / Shopping / Chat / Calendar / Profile) + ambient background + glass tab bar | ✅ |
| M3 | Per-feature parity — shopping/meal/wishlist/calendar/birthday colour+icon pickers, calendar private/colour/attendees, birthday icon/colour + creator-edit, wishlist share link + PDF + "shared with me", family directional relations + member popup, map avatar pins + geocode legend, Home glass summary cards, profile-completion prompt, auth strength/2-step, glass reskin throughout | ✅ |
| M4 | In-app EN/NB localization | ⏳ |
| M5 | Backend verification via Supabase MCP — all 16 objects present in prod; advisors only pre-existing WARNs; no migrations needed | ✅ |
| M6 | Lint/CI gate (`android-lint.yml`) + Kotlin pre-commit hooks + detekt baseline + `android/LINTING.md`; test gaps filled (AuthViewModelTest, FamilyMapViewModelTest) | ✅ |
| M7 | Final review + emulator/Maestro verification | ⏳ |

**Delivery note (2026-07-10):** M3 built via 5 parallel worktree agents (shopping+meals, calendar+birthdays, wishlists, family+map, home+profile+settings+auth), each spotless+detekt+compile+test green, merged sequentially into `feat/android-ios-parity` with zero conflicts (disjoint feature dirs; shared design layer + pickers landed first in M1–M3-groundwork). Wishlist share-link deep link (`familyapp://wishlist?token=`) wired in AppNavHost afterwards.

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
