# iOS Port Plan — native SwiftUI + monorepo restructure

> **✅ DELIVERED & MERGED TO MASTER (2026-07-05 → 07-10).** Branch `implementation/iosVersion`
> merged `test` → `master`. All 12 authoring phases done; the iOS app then gained a Liquid Glass
> redesign, a new tab-bar IA, and many features beyond the initial 1:1 port (localization, family
> relations, wishlist share links + PDF, calendar private/colour/attendees, colour pickers,
> birthday icon/colour, background location, platform-aware push). 11 DB migrations applied to
> production. Android is now catching up via the [[Android Parity Track]].

Started 2026-07-05. Goal: a **native iOS app with 1:1 feature and design parity**, in the same
repo restructured as `android/` + `ios/`, both apps on the **same Supabase database**, with zero
disruption to the working Android app.

**Framework decision:** Native SwiftUI + supabase-swift 2.x (Auth PKCE, Postgrest, RealtimeV2,
Storage — same modules the Android app uses; same DB, same RLS, no backend rewrite). Flutter and
KMP rejected (both would force rewriting/restructuring the working Compose app).

## Workflow (user-defined, 2026-07-05)

- **All work on ONE branch: `implementation/iosVersion`** (created from `test`).
- Commits labeled `feature: Descriptive title`. No Co-Authored-By lines, ever.
- Merge into `test` only after **all** phases are complete; `test` → `master` only on
  the user's explicit go-ahead.
- iOS code is compiled on the Mac **once, after all changes are done** — no per-phase compile
  loop. On Linux: author all Swift sources, XcodeGen `project.yml`, asset JSON; verify YAML/JSON
  syntax + API accuracy against supabase-swift docs.
- Android regression: `cd android && ./gradlew ...` where Android is touched.
- This vault is the #1 source of truth — read before starting, update after each phase.

## Phase status

| # | Phase | Status |
|---|-------|--------|
| 1 | Repo restructure — Android into `android/`, docs/paths/gitignore, maestro appIds | ✅ 2026-07-05 |
| 2 | Backend push platform support (`platform` column, platform-aware fcm.ts) | ✅ 2026-07-05 |
| 3 | iOS scaffold: XcodeGen project, design system, core data layer (Entities, repo, realtime, storage) | ✅ 2026-07-05 |
| 4 | Auth: login/register/OAuth/email confirmation + auth gate | ✅ 2026-07-05 |
| 5 | Tab shell, routes, deep links, home dashboard | ✅ 2026-07-05 |
| 6 | Shopping + meal planning (establishes realtime/optimistic template) | ✅ 2026-07-05 |
| 7 | Calendar (Month/Week/Agenda, custom month grid) + birthdays | ✅ 2026-07-05 |
| 8 | Wishlists + secret gift reservations | ✅ 2026-07-05 |
| 9 | Family mgmt/QR/join deep link, profile, settings, onboarding | ✅ 2026-07-05 |
| 10 | Chat (largest: text/image/voice, reactions, replies, receipts, typing, groups) | ✅ 2026-07-05 |
| 11 | MapKit family map + location publishing | ✅ 2026-07-05 |
| 12 | Push notifications (FCM iOS, token registration, categories, inline reply) | ✅ 2026-07-05 |

## Phase 1 — delivered notes (2026-07-05)

- `git mv` of the whole Gradle tree into `android/` (100% renames, history preserved —
  `git log --follow` verified). `supabase/`, `maestro/`, `.github/` stay at repo root.
- Gitignored files moved on disk too: `local.properties`, `release.keystore`,
  `app/google-services.json`, `build/`, `.gradle/`, `.kotlin/`, `.idea/` → all under `android/`.
- `.gitignore`: anchored `android/app/google-services.json`, added `.kotlin/` and an iOS block
  (generated `*.xcodeproj`, `Secrets.xcconfig`, `GoogleService-Info.plist`, `DerivedData/`, `.DS_Store`).
- `CLAUDE.md` + `README.md`: repo-layout section, `cd android` build commands, path fixes.
- Maestro flows: `appId` updated `com.example.mainactivity` → `com.sandnes.familyapp` (they were
  broken by the Play Store applicationId change).
- Untracked the stale `.kotlin/errors/*.log` that was accidentally in git.
- Verified: `:app:compileDebugKotlin` BUILD SUCCESSFUL from `android/`.
- ⚠️ **Known gap (pre-existing, NOT from the restructure):** `assembleDebug` fails at
  `processDebugGoogleServices` — `google-services.json` only registers `com.example.mainactivity`.
  Blocked on the external Play-Store step "add Firebase prod app for `com.sandnes.familyapp` and
  download new google-services.json" ([[../06_Notes_and_References/Play Store Release Guide]]).
- Prerequisite handled first: the uncommitted Play Store WIP was committed on
  `chore/play-store-release` and merged into `test` (user-approved), so this branch includes the
  prod applicationId.

## Phase 2 — delivered notes (2026-07-05)

- `supabase/add_device_push_token_platform.sql` — `device_push_tokens.platform`
  (`'android'` default | `'ios'`, check constraint, re-runnable). **Applied to the live
  project** via Supabase MCP (`apply_migration`), column verified present.
- `_shared/fcm.ts` — `sendPushToTokens(supabase, targets, data, alert?)`; targets are
  `{token, platform}`. Android path byte-identical data-only; iOS gets `notification`
  title/body + `apns` (`apns-push-type: alert`, priority 10, `mutable-content: 1`,
  `sound: default`, `thread-id` = conversation id / "birthdays" / "events").
- `push-on-message` + `daily-reminders` select `platform` and compose the iOS alert text
  server-side. `deno check` green on both.
- Android client needs no change — its upserts get `platform='android'` by default.
- ⚠️ **Live edge functions NOT redeployed** (permission-gated; deployed data-only versions
  keep serving Android unchanged). Redeploy both before iOS push testing —
  added to the Mac-side steps below and the functions README.

## Interleaved — Android package rename (2026-07-05, user request)

- Source package `com.example.mainactivity` → `com.sandnes.familyapp` (git mv +
  package/import rewrite, namespace in `app/build.gradle` now matches the applicationId).
- Verified: `assembleDebug` + full unit-test suite green. The local (gitignored)
  `google-services.json` was extended with a `com.sandnes.familyapp` client entry so the
  build runs; the REAL file must come from Firebase when the prod Android app is registered.
- Maestro flows already retargeted to `com.sandnes.familyapp` in Phase 1.

## Phase 3 — delivered notes (2026-07-05)

- `ios/project.yml` (XcodeGen): app target `FamilyApp` (iOS 17, `com.sandnes.familyapp`),
  test target `FamilyAppTests` wired into the scheme, SPM: supabase-swift ≥2.20, Nuke ≥12.8.
  Info.plist: `familyapp://` scheme, camera/mic/photos/location usage strings,
  `UIBackgroundModes` location+remote-notification, secrets injected from xcconfig.
- Core layer: `SupabaseClientProvider` (PKCE, redirect `familyapp://auth`, heartbeat 25 s,
  reconnect 3 s), `SessionStore` (UserDefaults, keys identical to Android DataStore),
  `FamilyRepository.shared` (full port of FamilyRepository.kt incl. cachedUser +
  invalidateUserCache, familyChanged AsyncStream, pendingJoinCode, notification-prefs
  server mirror, push token upsert with platform='ios', avatar palette with **Java
  hashCode parity**), `Entities.swift` (all models, snake_case CodingKeys),
  `RealtimeObserver` (channel `"table-\(familyId)"`, any event → full reload),
  `StorageService` (auth-uid storage paths enforced centrally — dual-ID rule),
  Nuke `ImagePipelineConfig`.
- DesignSystem: exact hex tokens light+dark (mirrors Color.kt/Theme.kt incl. WCAG
  Slate600 choice), SF Pro type scale 34/28/23/19/17/15/16/14/12, brand/hero gradients,
  4-pt spacing + radius tokens, components: PrimaryButton/SecondaryButton/
  DestructiveButton (0.97 press scale, 38% disabled opacity), FamilyTextField,
  InitialAvatar (Nuke), ListCard, SectionHeader, EmptyState, LoadingState,
  featureTopBar + resumeEffect modifiers, inputDialog, DatePickerField.
- App shell: `@main FamilyApp` (auth gate loading/signedOut/signedIn, onOpenURL),
  `RootViewModel`, `DeepLinkRouter` (auth → `auth.handle(url)`, chat, join), placeholder
  AuthFlowView/MainTabView (replaced in Phases 4/5), Routes enum, asset catalog,
  PrivacyInfo.xcprivacy, `ios/README.md` (Mac setup + dual-ID warning), CLAUDE.md iOS section.
- Tests (run on Mac): Entities decoding for every model, avatar-palette Kotlin parity
  (known Java hashCode values incl. overflow), SessionStore defaults/keys/round-trip,
  DeepLink parsing (positive + negative), ThemeMode/isoNow/ARGB helpers.
- Verified on Linux: project.yml YAML-parses, asset JSON + privacy XML valid.

## Phase 4 — delivered notes (2026-07-05)

- `AuthViewModel` (@Observable): same validation + ordered friendly-error keyword table as
  Android; `authStateChanges` listener finalizes Google OAuth by resolving `public.users.id`
  from `auth_id` (`completeSignInAfterConfirmation`); register → immediate sign-in (email
  confirmation disabled, matching Android).
- `AuthFlowView`: AuthScaffold (hero gradient, brand header, floating card, entrance
  animation), LoginScreen (forgot-password alert, Continue-with-Google), RegisterScreen
  (2 steps, StepIndicator, PasswordStrengthBar, BirthdayPickerField ISO yyyy-MM-dd, mobile).
- Tests: passwordStrength matrix, isValidEmail, friendlyAuthError mapping/order/fallbacks.

## Phase 5 — delivered notes (2026-07-05)

- `MainTabView`: 5 tabs, per-tab `NavigationStack(path:)` with typed `Route` destinations;
  feature VMs hoisted at tab level (MainFlow parity); deep links: chat push tap → chat tab +
  pushed thread, `familyapp://join` → family tab. Later-phase screens = PlaceholderScreen,
  swapped as phases land. Chat unread badge lands with Phase 10's ChatViewModel.
- `HomeViewModel` + `HomeScreen`: 1:1 port of the dashboard (greeting header, gradient
  family card / no-family banner, TONIGHT / NEXT EVENT / SHOPPING / NEXT BIRTHDAY summary
  cards, 6-tile quick-access grid, pull-to-refresh + resume refresh, familyChanged observer).
- `Core/LocalDate`: timezone-less date struct (Hinnant epoch-day algorithms) so all date
  math matches java.time.LocalDate; `nextBirthdayDate`/`turnsAge`/`timeBasedGreeting` ports.
- Note discovered for Phase 9: Android's auth gate has a 4th state `NeedsPermissions` →
  PermissionsOnboardingScreen; the iOS RootViewModel gate gains it in Phase 9.
- Tests: LocalDate suite, birthday-date utils, greeting boundaries, eventWhen/birthdayWhen.

## Phase 6 — delivered notes (2026-07-05)

- Template established (all later features copy it): load → subscribe-once (guarded per
  familyId/listId), realtime event → **pure reload** (never re-subscribe), mutations =
  optimistic temp-UUID update → call → reload, session-static cache for instant re-entry.
- Shopping: full VM port (incl. `or(owner_user_id, family_id)` list query + progress map)
  and screens (swipe delete, 12-icon new-list sheet, collapsible completed group, inline
  tap-to-rename, "N left" pill, 3-dot menu with clear-completed).
- Meals: full VM port (createPlan generates meal_plan_days with locale weekday names +
  ISO week number — parity with Calendar.WEEK_OF_YEAR) and screens (create sheet with icon
  toggle grid + clamped date range, per-day inline editing, plan header).
- Shared: `IconKeyMap` (the DB stores Material icon keys — shopping_cart, restaurant, … —
  mapped centrally to SF Symbols; picker option lists match the Android dialogs),
  `IconPickerSheet`, `FloatingActionButton` (AppFab twin); `RealtimeObserver` gained a
  server-side `filter:` param matching Android's postgresChangeFlow filters.
- Tests: label/format/week-number/icon-coverage suites.

## Phase 7 — delivered notes (2026-07-05)

- Calendar: full port — segmented Month/Week/Agenda, custom Monday-first month grid
  (today = filled primary circle, selected = container + ring, ≤3 color-coded event dots via
  the ICON_COLOR_INDEX port), week strip, date-grouped agenda, event sheet (16-icon grid,
  all-day switch, Starts/Ends date+time rows with clamping, HH:mm strings). `YearMonth`
  struct + pure `monthCells`/`dateEventIcons` (60-day cap)/`eventTimeLabel` helpers.
- Birthdays: next-occurrence-sorted countdown cards ("Turning N" chip, urgency pill:
  green Today / amber ≤7 days), add/edit sheets, swipe delete, realtime per family.
- Both wired into MainTabView (calendar tab + birthday route), VMs hoisted.
- Tests: YearMonth math, grid alignment incl. leap Feb, icon spans/caps, sort order.

## Phase 8 — delivered notes (2026-07-05)

- Full wishlist port: owner-name resolution (cached), realtime on wishlists + wishes,
  owner view (claim toggle, add-a-wish sheet with link/price/PhotosPicker photo) vs member
  view (Reserve / Reserved-by-you / Reserved), rename/change-icon menu, swipe deletes.
- Secret reservations preserved: reservations map loads via RLS-filtered
  `wish_reservations` select — empty for the owner, so nothing leaks. Cross-platform
  verification of RLS behavior happens in the final Mac smoke test.
- **Path-convention fix:** wish images upload to `wish-images/{app_userId}/{ts}.jpg` — the
  Android convention (bucket policy is any-authenticated, so the app id is correct here,
  unlike avatars). `StorageService.uploadWishImage` now takes `appUserId`.
- Tests: wishTitle, reservation-state matrix, unchecked-first sort, icon coverage.

## Phase 9 — delivered notes (2026-07-05)

- Gate: `needsPermissions` state added (parity with Android's 4-state gate); primer screen
  fires UNUserNotificationCenter + when-in-use location prompts; `onSignedIn` syncs the
  push token and notification prefs to the server.
- Family: full port — invite code (copy/share/QR via CoreImage), join deep-link pre-fill,
  create sheet with regenerable 8-char code, admin remove (context menu instead of swipe —
  minor idiom deviation), family photo upload (`group-images/family-photos/{id}/photo.jpg`,
  EXIF-normalized, cache-busted URL).
- Profile: hero card with tappable avatar (camera strip/upload overlay), camera+gallery+
  remove flows, `avatars/{auth_uid}/avatar.jpg` (dual-ID rule enforced), edit screen.
- Settings: theme/notifications+lead-time chips (0/1/2/7, server mirror)/map visibility/
  about, "Settings saved" toast.
- `ImageUtils.compressWithOrientation` (1024pt, JPEG 0.85) = the Android EXIF util twin.
- Tests: join code, invite message, QR, formatBirthday, options parity, image scaling.

## Phase 10 — delivered notes (2026-07-05)

- One shared `ChatViewModel` for list + thread (Android contract preserved). Full port:
  previews/unread via per-conversation notify channels, thread realtime, typing via
  Realtime **broadcast** (2 s throttle, 5 s auto-clear), reactions (optimistic toggle,
  insert/delete realtime), Seen receipts from `last_read_at`, send text with reply quotes,
  image/voice to `chat-media/{conversationId}/{auth_uid}/…`, create-conversation with
  existing-1:1 dedupe, add-member (1:1 → new group + system message), remove member,
  rename, group image, delete.
- UI: BrandGradient outgoing bubbles, quote bubbles, swipe-to-reply drag, context-menu
  quick reactions, 10-min time separators, typing dots, camera/photo attach, m4a voice
  notes (AVAudioRecorder/Player), pinch-zoom image viewer, unread tab badge.
- Tests: time-formatter buckets (fixed clock), presence, seen semantics, group-gap,
  fractional-second ISO parsing, display-name/preview matrix.

## Phase 11 — delivered notes (2026-07-05)

- MapKit map with avatar-palette pins + pointer, reverse-geocoded legend (CLGeocoder,
  cached per user), center-on-me, rationale alert before the system prompt.
- Publishing: first-fix immediate then 30 s cadence while the map is open; upsert honours
  `location_visible`; `visible=false` on leave. **Foreground-only v1** — matches the
  Android Play-Store build (background service was dropped there too), so parity is exact
  rather than "closest possible".
- Tests: formatLastSeen buckets (fixed clock) incl. clock-skew and fallbacks.

## Phase 12 — delivered notes (2026-07-05)

- FirebaseMessaging via SPM; `aps-environment` entitlement generated by XcodeGen.
- AppDelegate: Firebase gated on `GoogleService-Info.plist` presence, APNs→FCM bridge,
  token upsert `platform='ios'`, `pushTokenProvider` wired into
  `FamilyRepository.syncPushToken` (fires on sign-in), 3 notification categories —
  MESSAGE (inline reply `UNTextInputNotificationAction` posting through Postgrest),
  BIRTHDAY, EVENT — matching the new `category` field the edge functions now put in the
  APNs payload; foreground banner suppressed for the open conversation; tap →
  conversationId → chat tab.
- ⚠️ Still pending user-side: Firebase iOS app + APNs .p8 + `GoogleService-Info.plist`,
  edge-function redeploy, physical-device test.

## ✅ ALL 12 PHASES AUTHORED (2026-07-05) — next: Mac compile stage

Branch `implementation/iosVersion` (13 commits) **merged into `test` and pushed**
(merge commit `8b58c18`, 2026-07-05, user-instructed). `test`→`master` still awaits the
user's explicit go. Mac compile fix-ups land on `implementation/iosVersion` and get
re-merged into `test`. Mac checklist: "Mac-side steps" below and `ios/README.md`.

## Architecture mapping (translation dictionary)

| Android | iOS decision |
|---|---|
| Compose + M3 | SwiftUI, iOS 17 min (`@Observable`, MapKit SwiftUI annotations) |
| ViewModel + StateFlow | `@Observable @MainActor` class per feature; chat list+detail share one instance |
| `FamilyRepository.get(context)` | `FamilyRepository.shared` singleton |
| DataStore Preferences | `UserDefaults` via `SessionStore`, identical keys (`current_user_id_v2`, `theme_mode`, `notifications_enabled`, `notify_days_before`, `location_visible`) |
| NavHost + bottom nav (5 tabs) | `TabView` + per-tab `NavigationStack(path:)`, `enum Route: Hashable` |
| `postgresChangeFlow`, channel `"table-$familyId"`, full reload | RealtimeV2 `postgresChange` via `RealtimeObserver`, same channel names, reload-on-any-event |
| Optimistic UI (temp ID → call → reload, no rollback) | identical (temp `UUID()`) |
| Deep links auth/chat/join | `onOpenURL` → `DeepLinkRouter`; `familyapp://auth` → `supabase.auth.handle(url)`; OAuth via ASWebAuthenticationSession |
| FCM + NotificationHelper | FCM iOS SDK + `UNUserNotificationCenter`, 3 categories, `UNTextInputNotificationAction` inline reply |
| WorkManager reminders | nothing — server-side `daily-reminders` pg_cron is client-agnostic |
| Google Maps Compose | MapKit (free, no key) |
| Foreground location service (5 min) | Always auth + background location + significant-change; honest gap: no strict 5-min cadence on iOS |
| ZXing QR | CoreImage `CIFilter.qrCodeGenerator()` |
| Coil | Nuke `LazyImage` (12.x) |
| EXIF fix | redraw `UIImage` to `.up` orientation before JPEG upload |
| Voice notes (MediaRecorder) | `AVAudioRecorder` m4a/AAC + `AVAudioPlayer`, same `chat-media` bucket |

**Dual-ID rule carries over verbatim:** `SessionStore.currentUserId` = `public.users.id` (FKs);
`supabase.auth.currentSession?.user.id` = `auth_id` (RLS + storage paths `avatars/{auth_uid}/...`).
Enforced inside `StorageService`.

## Mac-side steps (deferred to the very end, after all phases)

1. `brew install xcodegen && cd ios && xcodegen generate`; create `Config/Secrets.xcconfig` from example.
2. `xcodebuild -project FamilyApp.xcodeproj -scheme FamilyApp -destination 'platform=iOS Simulator,name=iPhone 16' build`; fix-up commits on the same branch.
3. Firebase: add iOS app to the Firebase project, upload APNs .p8 key, download `GoogleService-Info.plist` (also still pending: Android prod app for `com.sandnes.familyapp`).
4. Push: redeploy the platform-aware edge functions (`supabase functions deploy push-on-message daily-reminders`), enable Push capability, physical-device test, TestFlight.
5. Cross-platform smoke: one Android + one iOS device in the same family — realtime chat both ways, shopping sync, wish-reservation invisibility, avatar upload, push on both.
