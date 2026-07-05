# The Family App

The Family App gives families one shared home for everyday coordination —
shopping, meals, calendar, birthdays, wishlists, chat, real-time location
sharing and more — wrapped in a modern, premium interface backed by a live
cloud backend.

## Repository layout

```
android/    # the Android app (Jetpack Compose + Material 3) — Gradle root
ios/        # the native iOS app (SwiftUI, XcodeGen) — full feature & design parity
supabase/   # shared backend: schema baseline, SQL migrations, edge functions
maestro/    # Maestro UI end-to-end flows (shared E2E ground)
```

Both apps are first-class native clients that talk to the **same Supabase project**
(Auth, Postgrest, Realtime, Storage) with a shared data model, identical realtime
conventions and matching design tokens. See `ios/README.md` for the full Android → iOS
architecture map.

## Features

- **Authentication** — register and sign in with Supabase Auth (email/password + **Continue with Google** OAuth, PKCE flow, deep-link verification)
- **Family** — create or join a family with an invite code, a **shareable QR code** and `familyapp://join` deep link; admin management of members
- **Home dashboard** — a glanceable feed of live summary cards (tonight's meal, next event, items left to buy, next birthday) above a quick-access grid
- **Shopping lists** — collaborative lists with check-off, a collapsible "Completed" group and per-list progress ("N of M bought")
- **Meal planner** — plan dinners across the week, with per-plan progress
- **Calendar** — shared family events with Month / Week / Agenda views, date/time ranges and all-day support
- **Birthdays** — keep track of everyone's special days with countdowns
- **Wishlists** — wishes with optional link, price and image; family members can **reserve a gift** without the owner seeing it
- **Family chat** — group and direct conversations with images, voice notes, emoji reactions, **read receipts, typing indicators and presence**
- **Family map** — real-time location sharing with reverse-geocoded place names and optional background tracking (Google Maps on Android, MapKit on iOS)
- **Notifications** — birthday and calendar event reminders (WorkManager on Android; server-side `daily-reminders` pg_cron job on iOS) plus FCM push for new chat messages
- **Profile & settings** — edit your profile and upload a photo (EXIF-orientation-correct), manage notifications and location sharing
- **Theming** — System / Light / Dark theme persisted across launches, plus a branded splash screen and adaptive icon
- **Localization** — English and Norwegian Bokmål throughout; iOS adds an **in-app language switch** that overrides the system language

All features are available on both Android and iOS with matching UI and behaviour.

## Tech stack

### Shared backend

- **Supabase** — Auth (PKCE + Google OAuth), Postgrest, Realtime (live sync), Storage (file uploads)
- **Realtime convention (both apps):** one channel per table+family (`tablename-<familyId>`), any event triggers a full reload, optimistic local writes with server reconciliation
- **Database:** Postgres with Row-Level Security on every table; edge functions for push and reminders

### Android

- **Language:** Kotlin 2.3.0
- **UI:** Jetpack Compose + Material 3 (single-Activity architecture), Compose BOM 2026.06.00
- **Navigation:** Navigation Compose with an auth gate and bottom navigation
- **DI:** Hilt — every ViewModel is `@HiltViewModel`/`@Inject`
- **Backend SDK:** Supabase 3.6.0 · **HTTP/WebSocket:** Ktor + OkHttp engine
- **Maps:** Google Maps Compose 8.3.0 · Google Play Services Location 21.3.0
- **Notifications:** WorkManager 2.11.2 (periodic birthday & calendar checks)
- **Image loading:** Coil 3.5.0 · **EXIF:** androidx.exifinterface
- **Serialization:** kotlinx-serialization · **Preferences/session:** Jetpack DataStore
- **Architecture:** MVVM — layered `data` (models, repository, session) and `ui`
  (theme, reusable components, per-feature ViewModel + screens)
- **Toolchain:** Android Gradle Plugin 9.2.1 · Gradle 9.6 · Compose compiler 2.3.0 · JDK 17 · compileSdk/targetSdk 37 · minSdk 23

### iOS

- **Language:** Swift 5.10
- **UI:** SwiftUI with an iOS 26 **Liquid Glass** design, mirroring Android's design tokens (identical colors, type, radii, spacing)
- **Navigation:** `TabView` + per-tab `NavigationStack` with an auth gate
- **Backend SDK:** supabase-swift 2.20.0
- **Maps:** MapKit (native) with reverse geocoding
- **Push:** Firebase iOS SDK 11.0.0 (`FirebaseMessaging` / FCM); reminders run server-side via a `daily-reminders` pg_cron job
- **Image loading:** Nuke 12.8.0 / NukeUI
- **Session:** `UserDefaults` (`SessionStore`, same keys as Android's DataStore)
- **Architecture:** MVVM under `Features/<Domain>/`, mirroring Android's `ui/<domain>/`; shared `Core/` (client, session, models, realtime, storage) and `DesignSystem/`
- **Project generation:** XcodeGen (`project.yml` → `FamilyApp.xcodeproj`, never committed)
- **Localization:** English + Norwegian Bokmål with an in-app language picker
- **Toolchain:** Xcode with XcodeGen · deployment target iOS 26.0 · SwiftLint + SwiftFormat gates

## Getting started

Both clients need the **same** Supabase project credentials (`SUPABASE_URL` /
`SUPABASE_ANON_KEY`). Secrets are injected at build time and are never committed.

### Android

Create a `local.properties` file in the `android/` directory (next to `android/settings.gradle`) with the following keys:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
MAPS_API_KEY=your-google-maps-api-key
```

These values are injected as `BuildConfig` fields at compile time.

**Android Studio:**

1. Open the `android/` directory (the Gradle root), not the repo root.
2. Ensure the Android SDK is installed and configured.
3. Add `android/local.properties` as described above.
4. Let Gradle sync.
5. Build and run the `app` module on an emulator or device (API 23+).

**Command line:**

```bash
cd android
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run the unit-test suite
./gradlew spotlessApply          # format (ktlint)
./gradlew detekt                 # static analysis (code quality)
./gradlew lint                   # Android lint
```

### iOS

Requires a Mac with Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen).
`FamilyApp.xcodeproj` is **generated** from `project.yml`, so it is never committed —
re-run `xcodegen generate` after editing the manifest.

```bash
brew install xcodegen
cd ios
cp Config/Secrets.example.xcconfig Config/Secrets.xcconfig   # fill in real values
xcodegen generate
open FamilyApp.xcodeproj
```

`Secrets.xcconfig` takes the same `SUPABASE_URL` / `SUPABASE_ANON_KEY` as Android's
`local.properties`. Set your signing team locally under Signing & Capabilities.

**Command line:**

```bash
cd ios
xcodebuild -project FamilyApp.xcodeproj -scheme FamilyApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
xcodebuild -project FamilyApp.xcodeproj -scheme FamilyApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' test
```

Push notifications require a physical device, a paid Apple Developer team and a
`GoogleService-Info.plist` in `ios/FamilyApp/Resources/` (gitignored). See
`ios/README.md` for the full push-setup and linting details.

## Project structure

### Android

```
android/app/src/main/java/com/sandnes/familyapp/
├── data/
│   ├── remote/       # SupabaseManager (Auth, Postgrest, Realtime, Storage)
│   ├── Entities.kt   # kotlinx-serializable data models (UserModel, FamilyModel, …)
│   ├── FamilyRepository.kt
│   └── SessionManager.kt
├── receivers/        # BootReceiver — restarts WorkManager on device boot
├── workers/          # NotificationWorker — birthday & calendar reminders
├── util/             # shared helpers (e.g. EXIF-aware image compression)
├── ui/
│   ├── theme/        # Material 3 theme (light + dark), colors, type, shapes
│   ├── components/   # reusable premium UI components
│   ├── auth/         # login & registration screens
│   ├── home/         # glanceable dashboard feed
│   ├── shopping/ meal/ calendar/ birthday/ wishlist/ chat/  # feature modules
│   ├── family/ profile/ settings/
│   ├── map/          # family map, location foreground service
│   └── navigation/   # Navigation Compose graph + auth gate
└── MainActivity.kt   # single Activity entry point

android/config/detekt/  # detekt config (Compose-friendly)
maestro/                # Maestro UI end-to-end flows (one per page)
supabase/               # schema baseline + incremental SQL migrations
```

### iOS

```
ios/FamilyApp/
├── App/              # app entry point + root scene
├── Core/             # SupabaseClientProvider, SessionStore, RealtimeObserver,
│   │                 #   StorageService, FamilyRepository
│   └── Models/       # Entities.swift (same snake_case columns as Android)
├── DesignSystem/     # color/type/shape/spacing tokens matching Android's ui/theme
│   └── Components/   # reusable UI components (same names as Android)
├── Features/         # per-domain MVVM: Auth, Home, Shopping, Meal, Calendar,
│   │                 #   Birthday, Wishlist, Chat, Map, Family, Profile,
│   │                 #   Settings, Onboarding, Navigation
│   └── …
└── Resources/        # Assets.xcassets, en.lproj / nb.lproj localizations

ios/Config/           # xcconfig files (Shared + gitignored Secrets)
ios/project.yml       # XcodeGen manifest (generates FamilyApp.xcodeproj)
ios/FamilyAppTests/   # unit tests (run via the scheme's test action)
```

## Quality & testing

**Android**

- **Unit tests** — MockK + Turbine + Robolectric across pure functions, entities, the
  repository/session layer and every ViewModel: `./gradlew testDebugUnitTest`.
- **Formatting** — Spotless (ktlint): `./gradlew spotlessApply`.
- **Static analysis** — detekt with a Compose-friendly config: `./gradlew detekt`.
- **Lint** — clean: `./gradlew lint`.

**iOS**

- **Unit tests** — `FamilyAppTests/`, run via the scheme's test action (or the
  `xcodebuild … test` command above) with coverage enabled.
- **Formatting & lint** — SwiftFormat (`.swiftformat`) and SwiftLint (`.swiftlint.yml`);
  SwiftLint also runs report-only on every build. See `ios/LINTING.md`.

**Shared**

- **UI end-to-end** — Maestro flows in `maestro/` (one per page, re-runnable on a device):
  `maestro test maestro/`. See `maestro/README.md`.

## Database

The Postgres schema lives in `supabase/schema.sql`; incremental changes are individual,
re-runnable `.sql` files in `supabase/`. Row-Level Security scopes every table; table
privileges must be granted to the `authenticated` role alongside the RLS policies.

## Roadmap

- Home-screen Glance widget
- Per-message delete in chat
- Full TalkBack / 200%-font accessibility sign-off
