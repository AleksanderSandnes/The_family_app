# Delivered — Foundation, Build & Tooling

Delivered milestones for build/tooling, the Compose rewrite, and CI/formatting. Part of
[[../Implementation Plan]]. See also [[../../02_Build_and_Environment/Build and Environment]].

## Milestones 1–7 — tooling & modernization kickoff ✅
1. Tooling modernization & build stabilization.
2. Feature audit (implemented-vs-missing mapping).
3. English documentation baseline in Obsidian.
4. Backend options / API strategy draft.
5. Norwegian → English naming normalization (major pass across classes, resources, nav ids).
6. Compose modernization kickoff (`ModernMainActivity` in the post-login flow).
7. AGP/Gradle upgrade + premium global theme redesign baseline.

## Milestone 8 — full Compose redesign ✅
Clean-slate rewrite from legacy Java/Fragments/XML to a single-Activity Jetpack Compose + Material
3 architecture. All legacy Java (LoginActivity, Database.java, adapters, fragments, models,
NetworkUtils) and obsolete XML resource dirs removed. New layered architecture: `data/`,
`ui/theme/` (indigo/violet M3, light+dark, edge-to-edge), `ui/components/`, feature packages
(auth, home, shopping, meal, calendar, birthday, wishlist, chat, family, profile, settings),
`ui/navigation/` (auth gate + bottom nav). Dark mode selector (System/Light/Dark) persisted via
DataStore. Delivered via `feat/compose-full-redesign`.

> Note: this milestone originally shipped with Room; Room was **later removed entirely** when the
> app moved to Supabase-only — see [[Backend & Data Sync]].

## Milestone 20 — build infrastructure upgrade ✅
Upgrade to **Gradle 9.4.1 + AGP 9.2.1** and migrate to the **Gradle Daemon Toolchain** (JVM 17,
any vendor) via `gradle/gradle-daemon-jvm.properties`. Applied through the Android Studio AGP
Upgrade Assistant (handles the AGP 9.x compat flags: explicit `resValues`, `targetSdk` no longer
defaults to `compileSdk`, compile-time R class off, R8 strict/optimized-shrinking off, built-in
Kotlin support removed). Kotlin bumped 2.1.20 → 2.3.0 (forced by maps-compose 8.3.0); compileSdk/
targetSdk → 36; minSdk → 23. Full detail: [[../../02_Build_and_Environment/Build and Environment]].

## Linting — Spotless / ktlint ✅
`com.diffplug.spotless` + `ktlint('1.3.1')`, `trimTrailingWhitespace`, `endWithNewline` on
`src/**/*.kt`. `.editorconfig` at root (4-space indent, `max_line_length = off`). All 16 Compose
files carry `@file:Suppress("ktlint:standard:function-naming")` (Spotless runs without the compile
classpath, so it can't resolve `@Composable`). `./gradlew spotlessApply` / `spotlessCheck`.
Later joined by **detekt** (`./gradlew detekt`, 0 findings, no baseline; config in `config/detekt/`).

## Build fix — GMS location kotlin-reflect crash ✅
`play-services-location:21.3.0` uses Kotlin reflection internally; Kotlin 2.x no longer bundles
`kotlin-reflect` transitively → `ClassNotFoundException … FunctionClassKind` at runtime. Fix: add
`org.jetbrains.kotlin:kotlin-reflect:2.3.0`. Also downgraded `play-services-location` 21.4.0 →
21.3.0 (21.4.0 doesn't exist on Maven).

## Bug — dead space above headers ✅
`enableEdgeToEdge()` made the outer `MainFlow` Scaffold pad the status-bar inset, which inner
Scaffolds/TopAppBars then padded again. Fix: outer Scaffold `contentWindowInsets =
WindowInsets.systemBars.only(Bottom + Horizontal)`; inner TopAppBars handle the status bar
naturally; `HomeScreen` (no inner Scaffold) gets `.statusBarsPadding()`.
