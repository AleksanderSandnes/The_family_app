# The Family App

The Family App is an Android application that gives families one shared home for
everyday coordination — shopping, meals, calendar, birthdays, wishlists, chat and
more — wrapped in a modern, premium interface.

## Team

- Aleksander Ranum (Ranum99)
- Aleksander Sandnes (AleksanderSandnes)
- Emilie Nilsen (emilienilsen)

## Features

- **Authentication** — register and sign in with hashed (SHA-256) credentials
- **Family** — create or join a family and manage members
- **Home dashboard** — a personalized overview of everything you share
- **Shopping lists** — collaborative lists with items and check-off
- **Meal planner** — plan meals across the week
- **Calendar** — shared family events
- **Birthdays** — keep track of everyone's special days
- **Wishlists** — wish lists and individual wishes
- **Family chat** — conversations and messaging between members
- **Profile & settings** — edit your profile and choose your appearance
- **Dark mode** — System / Light / Dark theme, persisted across launches

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 (single-Activity architecture)
- **Navigation:** Navigation Compose with an auth gate and bottom navigation
- **Persistence:** Room (local database) with a repository layer
- **Preferences/session:** Jetpack DataStore
- **Architecture:** layered `data` (entities, DAOs, repository) and `ui`
  (theme, reusable components, per-feature ViewModel + screens)

## Build and environment

- Android Gradle Plugin 8.13.2 · Gradle 8.13
- Kotlin 2.1.20 · Compose compiler 2.1.20 · KSP 2.1.20-2.0.1
- Room 2.7.1
- JDK 17 · compileSdk 34 · minSdk 21

## Getting started

From Android Studio:

1. Open the project root.
2. Ensure the Android SDK is installed and configured.
3. Let Gradle sync.
4. Build and run the `app` module on an emulator or device.

From the command line:

```bash
./gradlew assembleDebug
./gradlew test
```

## Project structure

```
app/src/main/java/com/example/mainactivity/
├── data/        # Room entities, DAOs, AppDatabase, FamilyRepository, SessionManager
├── ui/
│   ├── theme/        # Material 3 theme (light + dark), colors, type, shapes
│   ├── components/   # reusable premium UI components
│   ├── auth/         # login & registration
│   ├── home/         # dashboard
│   ├── shopping/ meal/ calendar/ birthday/ wishlist/ chat/  # feature modules
│   ├── family/ profile/ settings/
│   └── navigation/   # Navigation Compose graph + auth gate
└── MainActivityCompose.kt   # single Activity entry point
```

## Design references

Planning and architecture follow the original system design document
(`Systemdesign_Android_Gruppe06.pdf`): hierarchical family navigation, hashed
credentials, and the Room Persistence Library for local storage.

## Roadmap

- Family map / location sharing module
- Cloud backend/API for multi-device, real-time family sync
