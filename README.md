# The Family App

The Family App is an Android application that gives families one shared home for
everyday coordination — shopping, meals, calendar, birthdays, wishlists, chat,
real-time location sharing and more — wrapped in a modern, premium interface
backed by a live cloud backend.

## Features

- **Authentication** — register and sign in with Supabase Auth (email/password, PKCE flow, deep-link verification)
- **Family** — create or join a family with a join code; admin management of members
- **Home dashboard** — a personalized overview of everything you share
- **Shopping lists** — collaborative lists with items and check-off
- **Meal planner** — plan meals across the week
- **Calendar** — shared family events with date/time ranges and all-day support
- **Birthdays** — keep track of everyone's special days
- **Wishlists** — wish lists and individual wishes per family member
- **Family chat** — group conversations and direct messages between members
- **Family map** — real-time location sharing on Google Maps with optional background tracking
- **Notifications** — birthday and calendar event reminders via WorkManager (configurable lead time)
- **Profile & settings** — edit your profile, upload a photo, and choose your appearance
- **Dark mode** — System / Light / Dark theme, persisted across launches

## Tech stack

- **Language:** Kotlin 2.3.0
- **UI:** Jetpack Compose + Material 3 (single-Activity architecture)
- **Navigation:** Navigation Compose with an auth gate and bottom navigation
- **Backend:** Supabase 3.1.4 — Auth (PKCE), Postgrest, Realtime (live sync), Storage (file uploads)
- **HTTP/WebSocket:** Ktor + OkHttp engine (required for Supabase Realtime WebSocket support)
- **Maps:** Google Maps Compose 8.3.0 · Google Play Services Location 21.3.0
- **Notifications:** WorkManager 2.9.0 (periodic checks for birthdays and calendar events)
- **Image loading:** Coil 2.7.0
- **Serialization:** kotlinx-serialization
- **Preferences/session:** Jetpack DataStore
- **Architecture:** MVVM — layered `data` (models, repository, session) and `ui`
  (theme, reusable components, per-feature ViewModel + screens)

## Build and environment

- Android Gradle Plugin 8.13.2 · Gradle 8.13
- Kotlin 2.3.0 · Compose compiler 2.3.0
- Supabase SDK 3.1.4
- JDK 17 · compileSdk 36 · minSdk 23

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
./app/gradlew assembleDebug
./app/gradlew test
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
├── ui/
│   ├── theme/        # Material 3 theme (light + dark), colors, type, shapes
│   ├── components/   # reusable premium UI components
│   ├── auth/         # login & registration screens
│   ├── home/         # dashboard
│   ├── shopping/ meal/ calendar/ birthday/ wishlist/ chat/  # feature modules
│   ├── family/ profile/ settings/
│   ├── map/          # family map, location foreground service
│   └── navigation/   # Navigation Compose graph + auth gate
└── MainActivityCompose.kt   # single Activity entry point
```

## Roadmap

- Reaction emoji on chat messages
- Read receipts for direct messages
