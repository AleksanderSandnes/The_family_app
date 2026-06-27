# The Family App

The Family App is an Android application that gives families one shared home for
everyday coordination — shopping, meals, calendar, birthdays, wishlists, chat,
real-time location sharing and more — wrapped in a modern, premium interface
backed by a live cloud backend.

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
- **Family map** — real-time location sharing on Google Maps (reverse-geocoded place names) with optional background tracking
- **Notifications** — birthday and calendar event reminders via WorkManager (configurable lead time)
- **Profile & settings** — edit your profile and upload a photo (EXIF-orientation-correct), manage notifications and location sharing
- **Theming** — System / Light / Dark theme persisted across launches, plus a branded splash screen and adaptive icon

## Tech stack

- **Language:** Kotlin 2.3.0
- **UI:** Jetpack Compose + Material 3 (single-Activity architecture), Compose BOM 2026.06.00
- **Navigation:** Navigation Compose with an auth gate and bottom navigation
- **DI:** Hilt — every ViewModel is `@HiltViewModel`/`@Inject`
- **Backend:** Supabase 3.6.0 — Auth (PKCE + Google OAuth), Postgrest, Realtime (live sync), Storage (file uploads)
- **HTTP/WebSocket:** Ktor + OkHttp engine (required for Supabase Realtime WebSocket support)
- **Maps:** Google Maps Compose 8.3.0 · Google Play Services Location 21.3.0
- **Notifications:** WorkManager 2.11.2 (periodic checks for birthdays and calendar events)
- **Image loading:** Coil 3.5.0 · **EXIF:** androidx.exifinterface (orientation-correct uploads)
- **Serialization:** kotlinx-serialization
- **Preferences/session:** Jetpack DataStore
- **Architecture:** MVVM — layered `data` (models, repository, session) and `ui`
  (theme, reusable components, per-feature ViewModel + screens)

## Build and environment

- Android Gradle Plugin 9.2.1 · Gradle 9.6
- Kotlin 2.3.0 · Compose compiler 2.3.0
- Supabase SDK 3.6.0
- JDK 17 · compileSdk 37 · targetSdk 37 · minSdk 23

## Getting started

### Prerequisites

Create a `local.properties` file in the project root (next to `settings.gradle`) with the following keys:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
MAPS_API_KEY=your-google-maps-api-key
```

These values are injected as `BuildConfig` fields at compile time and are never committed to version control.

### Android Studio

1. Open the project root.
2. Ensure the Android SDK is installed and configured.
3. Add `local.properties` as described above.
4. Let Gradle sync.
5. Build and run the `app` module on an emulator or device (API 23+).

### Command line

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run the unit-test suite
./gradlew spotlessApply          # format (ktlint)
./gradlew detekt                 # static analysis (code quality)
./gradlew lint                   # Android lint
```

## Project structure

```
app/src/main/java/com/example/mainactivity/
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

config/detekt/        # detekt config (Compose-friendly)
maestro/              # Maestro UI end-to-end flows (one per page)
supabase/             # schema baseline + incremental SQL migrations
```

## Quality & testing

- **Unit tests** — MockK + Turbine + Robolectric across pure functions, entities, the
  repository/session layer and every ViewModel: `./gradlew testDebugUnitTest`.
- **Formatting** — Spotless (ktlint): `./gradlew spotlessApply`.
- **Static analysis** — detekt with a Compose-friendly config: `./gradlew detekt`.
- **Lint** — clean: `./gradlew lint`.
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
