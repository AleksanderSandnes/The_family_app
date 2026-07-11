# Android ⇄ iOS Parity Track

**Delivered & merged to master (2026-07-10 → 07-11).** Branch `feat/android-ios-parity`, merged
via `test`; `test` and `master` both at `f2d056f`. Brought the Android app up to the iOS-era
feature set and the Liquid Glass look, after the iOS app shipped ahead (see [[iOS Port Plan]]).
Part of [[Implementation Plan]].

**Post-M7 follow-ups (2026-07-11, all merged):** post-parity design pass fixing every page
against the 70 live-iOS screenshots; 7-issue on-device review batch (wishlist bg, owner gating,
photo pins, dark toggles, nav bar); compact 64dp nav bar + scrollable calendar empty state;
calendar empty-state/OK strings moved to resources (NB support); iOS-matching launcher icon
(gradient + family glyph) with the launcher name fixed to "The family app" in all locales;
project-wide comment cleanup. Signed production APK built with `assembleRelease`.

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
| M4 | In-app EN/NB localization — 396 strings each in `values/` + `values-nb/`; live switch via `AppCompatDelegate.setApplicationLocales`; Settings language picker; core screens extracted (feature-body strings a documented follow-up) | ✅ |
| M5 | Backend verification via Supabase MCP — all 16 objects present in prod; advisors only pre-existing WARNs; no migrations needed | ✅ |
| M6 | Lint/CI gate (`android-lint.yml`) + Kotlin pre-commit hooks + detekt baseline + `android/LINTING.md`; test gaps filled (AuthViewModelTest, FamilyMapViewModelTest) | ✅ |
| M7 | Final review + emulator/Maestro verification | ✅ |

**M7 review (2026-07-10):** multi-agent code review over the branch diff surfaced 3 real bugs, all fixed + re-verified: (1) **missing `familyapp://wishlist` manifest intent-filter** — the share-link feature was unreachable (HIGH); (2) FamilyScreen opaque background broke the glass look; (3) Calendar/Birthday/Meal/Home date formatters pinned to `Locale.ENGLISH` didn't localize. Three lower/latent items left documented (nullable-colour `setToNull` harmonization; share-token consumed on redeem failure; feature-body string extraction still pending). The wishlist deep link was re-tested on the emulator after the manifest fix and now opens the Wishlist screen.

## Verification evidence (2026-07-10)
- **Build/static**: `spotlessCheck` + `detekt` + `lintDebug` + `assembleDebug` all green; **440 unit tests pass** (was ~382).
- **Backend (MCP)**: 16/16 iOS-era objects present in prod; `get_advisors` only pre-existing WARNs.
- **On-device (Maestro, Pixel 7 emulator)**: registered a temp account end-to-end → 2-step register → permissions onboarding → Home dashboard (glass feature tiles + correct feature accents) → glass tab bar with the new IA → Shopping New-list dialog (shared IconGrid + 8-colour ColorPickerRow) → created a list. Verified the write landed in prod with `color = 0x6366F1` (the indigo swatch chosen) — cross-platform parity proven live. Settings → **Norsk** re-localized the whole screen instantly (AppCompat locale recreate). `AppCompatActivity` migration launches cleanly.

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

**Current-state visual source of truth (2026-07-11):** `The family app design docs/ios/` — 70
screenshots from the **live iOS app** in light + dark, named per screen (index +
inventory in `The family app design docs/README.md`). These replace the older loose `*.jpeg`
reference shots, which were stale and have been deleted. When matching an Android screen, read the
iOS **source** (`ios/FamilyApp/Features/…`) for exact strings/behaviour alongside the screenshot.
`The family app design docs/android/` is filled in **after** each Android screen reaches parity —
an emulator screenshot per fixed screen, same naming — as before/after evidence.

### Post-parity design pass (2026-07-11) — ✅ COMPLETE
Screen-by-screen emulator review (Maestro MCP) against the current iOS screenshots + source, all
pages fixed and user-approved page by page. Android before/after shots (light + dark, 26 files) in
`…/design docs/android/`. Highlights:
- **Glass layer fixed app-wide**: split Haze states (cards no longer blur their own scroll
  container — the "smudge" artifact), ambient-wash radial gradients drawn at the correct centres
  (bug: brush coords were rect-relative), floating pill tab bar, transparent top bars (opaque
  bands removed), tab bar now visible on pushed screens like iOS (except conversation).
- **iOS creation-sheet system**: new `CreationSheet`/`SheetHeader`/`SheetField`/`SheetSectionLabel`
  components (mirror iOS `SheetHeader`/`GlassField`); every create/edit dialog converted —
  shopping list, meal plan, calendar event, birthday, wishlist, wish. IconGrid selected tile =
  solid accent + white glyph, 8-colour row fits without clipping.
- **Real bugs found & fixed on-device**: chat timestamps entirely blank on device
  (`Instant.parse` rejects Supabase's `+00:00` offset on desugared java.time → robust
  `parseInstant`, fixes tap-to-reveal/gap pills/list labels/presence/read receipts, +tests);
  in-app **Dark theme left the background light** (design layer read `isSystemInDarkTheme()`;
  now `appDarkTheme()` CompositionLocal from the theme); Family-Map cold-start crash
  (`BitmapDescriptorFactory` before Maps SDK init); map camera never centred without own GPS fix
  (now fits family pins); meal inline-edit cursor at start + untrimmed saves.
- **Chat parity**: reply preview/bubble show the quoted sender's name, tap-a-bubble reveals full
  date+time (iOS `exactMessageTimestamp`), "+"-menu composer with `Message…` placeholder, white
  composer surface wraps to the page bottom, destructive menu items red.
- **Feature-body localization completed** (the documented M4 follow-up): calendar, family
  (incl. relation display names EN/NB), wishlists, chat, profile, birthdays, settings — plus
  Home summary lines resolved in the UI layer.
- MealViewModelTest suite repaired (rollback-on-failure semantics from the M7-era no-family
  gate) via pure `buildOptimisticPlan`; new NB-label and timestamp-parser tests. Full unit
  suite + detekt + spotless green.
- Test data: family "Parityveien 7" (accounts familyapp.parity.test1/2@gmail.com) seeded in prod
  mirroring the iOS reference shots; register → join-with-code → relations flows exercised live.
