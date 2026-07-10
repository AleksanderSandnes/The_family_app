# Build and Environment

> Since the monorepo restructure, the Android app is the Gradle root under **`android/`** — all
> `./gradlew` commands run from there (e.g. `cd android && ./gradlew assembleDebug`).
> `local.properties`, `google-services.json`, and `release.keystore` live inside `android/`. The
> iOS app (`ios/`) is generated with XcodeGen and compiled on a Mac (this Linux env can't compile
> Swift) — see [[../05_Implementation_Plan/iOS Port Plan]].

## Current status
Build/tooling modernization is complete and verified. AGP/Gradle/JDK alignment is now a required first planning step before feature work.

## Environment
- OS: Arch Linux
- Java runtime: OpenJDK 17.0.19
- Android SDK path used: /home/aleksander/Android/Sdk

## Current toolchain (as of 2026-06-24)
| Tool | Version | Notes |
|---|---|---|
| Gradle wrapper | 9.4.1 | Upgraded from 8.13 — required by AGP 9.2.1 |
| Android Gradle Plugin | 9.2.1 | Upgraded from 8.13.2 |
| Gradle Daemon Toolchain | 17 (any vendor) | `gradle/gradle-daemon-jvm.properties` — auto-detects/downloads JVM |
| Kotlin | 2.3.0 | Upgraded from 2.1.20 to match maps-compose 8.3.0 |
| KSP | 2.3.9 | In classpath only — not applied (no Room/KSP usage) |
| compileSdkVersion | 36 | Bumped from 34 for maps-compose + Compose 1.10.x |
| targetSdkVersion | 36 | Latest API for full device compatibility |
| minSdkVersion | 23 | Bumped from 21 — required by maps-compose 8.3.0 |
| JVM target | 17 | |
| Core library desugaring | 2.1.5 | Still needed for java.time on API 23–25 |

## Upgrade — Gradle 9.4.1 + AGP 9.2.1 + Daemon Toolchain (applied 2026-06-24)

Android Studio Upgrade Assistant recommends upgrading to AGP 9.2.1 + Gradle 9.4.1. This is the correct pairing (AGP 9.2.0 mandates Gradle 9.4.1 minimum). **Use `Tools → AGP Upgrade Assistant → Run selected steps`** to apply the version bumps and the required compatibility flags automatically.

### What is the Gradle Daemon Toolchain?
Currently the build uses implicit JDK detection (`/opt/android-studio/jbr`). This means CLI and IDE can spawn separate Gradle daemons using different JVMs, and onboarding a new machine requires manual JDK setup.

The daemon toolchain feature (Gradle 9.x) stores JVM criteria in `gradle/gradle-daemon-jvm.properties`. Gradle then auto-detects a matching JVM or downloads one. **`gradle/gradle-daemon-jvm.properties` has been created:**
```properties
toolchainVersion=17
toolchainVendor=any
```

### Benefits of upgrading
| Area | Benefit |
|---|---|
| Daemon Toolchain | CLI and IDE share one daemon — fewer restarts, lower memory |
| Daemon Toolchain | Auto-downloads JVM on any machine; no manual JDK setup |
| Gradle 9.4.1 | Configuration Cache is now the preferred mode (mandatory in Gradle 10) |
| Gradle 9.4.1 | Precise property tracking — fewer unnecessary cache invalidations |
| AGP 9.2.1 | Lifecycle aligns with Gradle 9.x; R8 and R class improvements |
| AGP 9.2.1 | `resValues` and `targetSdk` behavior made explicit (no implicit defaults) |

### Required Upgrade Assistant steps (IDE handles these)
The IDE Upgrade Assistant lists these steps for AGP 8.13.2 → 9.2.1:
- Upgrade Gradle version to 9.4.1
- Enable resValues build feature
- Disable targetSdk defaults to compileSdk
- Disable App Compile-Time R Class
- Continue to allow `<uses-sdk>` in the main manifest
- Allow non-unique package names
- Enable Dependency Constraints
- Disable R8 Strict Mode for Keep Rules
- Disable R8 Optimized Resource Shrinking
- Disable built-in Kotlin support
- Preserve the old (internal) AGP DSL APIs
- Migrate to Gradle Daemon toolchain

### Compatibility checklist
- Kotlin 2.3.0 ✅ (Gradle 9.x requires Kotlin 2.0+)
- JVM target 17 ✅ (Gradle 9.x requires Java 17+ for the daemon)
- No `jcenter()` usage ✅ (removed in Gradle 9.x)
- Supabase SDK 3.1.4 and KSP 2.3.9: verify after upgrade

### Verification after upgrade
1. `./gradlew assembleDebug` — clean build passes
2. `./gradlew --info | grep "Using daemon"` — daemon uses JVM 17 from toolchain
3. Run on Pixel 7 emulator — Supabase auth, realtime, and Compose UI functional

## Build modernization history
- Gradle wrapper upgraded from legacy → 8.13 → **9.4.1**
- Android Gradle Plugin upgraded from legacy → 8.13.2 → **9.2.1** (with AGP 9.x compat flags in `gradle.properties`)
- Gradle Daemon Toolchain introduced: `gradle/gradle-daemon-jvm.properties` (JVM 17, any vendor)
- Kotlin upgraded: 1.9.24 → 2.1.20 → **2.3.0** (forced by maps-compose 8.3.0 binary incompatibility)
- Repositories migrated from jcenter() to mavenCentral()
- compileSdk/targetSdk: 34 → **36** (forced by maps-compose 8.3.0 + Compose 1.10.x transitive deps)
- minSdk: 21 → **23** (forced by maps-compose 8.3.0 manifest requirement)
- Kotlin Compose Compiler plugin enabled (bundled with Kotlin 2.x, no separate version needed)
- kotlinx-serialization plugin tracks Kotlin version

## Key dependency versions
| Dependency | Version |
|---|---|
| Compose BOM | 2024.09.03 (resolves to 1.7.x; transitive from maps pulls 1.10.x — compileSdk 36 required) |
| Supabase BOM | 3.1.4 |
| maps-compose | 8.3.0 |
| play-services-location | 21.3.0 |
| Navigation Compose | 2.8.2 |
| Lifecycle (ViewModel/Compose) | 2.8.6 |
| Coil | 2.7.0 |
| WorkManager | 2.9.0 |

## Verification
- `./gradlew assembleDebug` succeeds (post Kotlin 2.3.0 + compileSdk 36 upgrade)

## Operational rule (first step)
For every new branch or milestone, perform AGP/Gradle/JDK alignment verification first:
1. Confirm wrapper, AGP, Kotlin, and JDK compatibility.
2. Run `./gradlew assembleDebug`.
3. Start feature work only after build baseline passes.

## Google Maps API key
- Key is stored in `android/local.properties` as `MAPS_API_KEY=` (not committed — in `.gitignore`)
- Restricted to package `com.sandnes.familyapp` + debug SHA-1 fingerprint
- API restriction: Maps SDK for Android only
- For release builds: add release keystore SHA-1 as a second entry in Cloud Console
