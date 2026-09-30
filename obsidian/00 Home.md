# The Family App

Welcome to the project knowledge base for The Family App.

## Quick links
- [[01_Project_Overview/Project Overview]]
- [[02_Build_and_Environment/Build and Environment]]
- [[03_Architecture_and_Design/Architecture and Design]] — Android + iOS architecture, Liquid Glass
- [[03_Architecture_and_Design/Backend Options and API Strategy]]
- [[04_Features_and_Backlog/Feature Inventory]] — iOS-vs-Android feature matrix
- [[05_Implementation_Plan/Implementation Plan]] — plan index + delivered milestones
- [[05_Implementation_Plan/Android Parity Track]] — Android ⇄ iOS parity (delivered, merged to master)
- [[05_Implementation_Plan/iOS Port Plan]] — native SwiftUI port (delivered, merged to master)
- [[05_Implementation_Plan/Project - UI - UX - improvements]] — UI/UX discovery, audit & plan
- [[05_Implementation_Plan/Design_Improvements/00 Design Improvement Plan]] — design track D1–D8 (per-screen specs)
- [[06_Notes_and_References/Project Notes]]
- [[06_Notes_and_References/Play Store Release Guide]] — production deployment runbook

## Current status
- **iOS session gate hardening prepared** — validate restoration and profile identity, reject stale responses, observe sign-out and reset the account’s view lifetime. Seventeen new tests await native CI.
- **Private media clients prepared (2026-09-30)** — Android checks and 513 tests pass. Android/iOS use fresh signed URLs and bypass protected-image caches; restrictive read policies pass local isolation tests. 264 iOS simulator tests and strict Swift checks also pass. Local Auth/Storage HTTP smoke passed 65 assertions; signed-release UI checks and private-bucket rollout remain open.
- **Swift security scan recovered (2026-09-30)** — all four CodeQL languages passed in [run 36718982820](https://github.com/AleksanderSandnes/The_family_app/actions/runs/36718982820). The Swift scan builds the runner's native simulator architecture and retains compilation evidence. Verified changes advanced to `test`; the new test-branch run is pending.
- **Release readiness in progress (2026-09-30)** — 502 Android unit tests, 251 iOS tests and four Keystore emulator checks pass. Backend security fixes are staged; coverage, private media and production rollout remain open. Keep `master` by user decision. See [[05_Implementation_Plan/Release Readiness]].
- **🎁 Wishlist v2 verified on emulator (2026-07-14)** — wish descriptions (new `description` column), member detail popup (image, description, NOK price, full tappable URL, reserve), Glass House-styled PDF export (ambient canvas, white cards, gradient accent, footer), NOK prices in rows. R8-minified build (23.9→5.8 MB) smoke-tested end-to-end on emulator incl. login, realtime data, images and PDF export; merged to master.
- **🎁 Wishlist PDF v2 (2026-07-14)** — export now embeds wish image thumbnails, NOK-formatted prices and shortened links in a card layout with the indigo accent (both platforms). Shopping-item inline edit already existed (tap item text). R8 minification staged on `chore/enable-r8` pending on-device smoke test.
- **✉️ Signup email verification shipped (2026-07-12)** — new signups verify a 6-digit
  emailed code before the permission screen (both platforms); `enable_confirmations`
  is LIVE in production, so older installed versions error at signup until users
  update. Branch `feat/signup-email-verification`.
- **🔑 Forgot-password reset shipped (2026-07-12)** — 6-digit email-code flow on Android +
  iOS (branch `feat/forgot-password-reset`); domain **thefamilyapp.app** bought via Vercel.
  User action pending: edit the Supabase Reset Password email template ({{ .Token }}) and run
  the Resend SMTP runbook — [[06_Notes_and_References/Password Reset & Resend Email Setup]].
- **🔍 Impeccable design review + polish (2026-07-12)** — first full critique (28/40) + native
  audit (13/20) of both apps; polish pass on branch `fix/impeccable-polish`: TalkBack delete
  action, delete confirmations for shared data, ~60 strings wired to EN/NB resources, reduce
  motion, font-scale-safe chrome, AA contrast tokens, iOS Dynamic Type. Reports in
  `.impeccable/`; details: [[06_Notes_and_References/Design Review 2026-07]].
- **📱🤖 The Family App is now a TWO-platform product** — `android/` (Jetpack Compose +
  Material 3, package `com.sandnes.familyapp`) and `ios/` (native SwiftUI, iOS 26 "Liquid
  Glass") share one Supabase backend. Repo restructured into `android/` + `ios/` + shared
  `supabase/`, `maestro/`.
- **✅ Android ⇄ iOS parity track — DELIVERED & MERGED TO MASTER (2026-07-10 → 07-11)** —
  branch `feat/android-ios-parity`, merged via `test`; `test` and `master` both at `f2d056f`.
  All milestones M0–M7 done: Liquid Glass layer via **Haze** on Compose + **Material 3
  Expressive**, iOS tab-bar IA (Home / Shopping / Chat / Calendar / Profile), all iOS-era
  features (colour/icon pickers, calendar private/colour/attendees, wishlist share links + PDF,
  directional relations + member popup, profile-completion prompt), in-app EN/NB language
  switch, detekt+spotless CI gate, 440 unit tests green. After M7: a post-parity design pass
  against 70 live-iOS screenshots, a 7-issue on-device review batch, compact 64dp nav bar,
  calendar string localization, an iOS-matching launcher icon + fixed launcher name, and a
  comment cleanup. **Signed production APK built 2026-07-11** (`assembleRelease`,
  `com.sandnes.familyapp`). Full plan: [[05_Implementation_Plan/Android Parity Track]].
- **✅ iOS port + monorepo restructure — DELIVERED & MERGED TO MASTER (2026-07-05 → 07-10)** —
  branch `implementation/iosVersion`, merged `test` → `master`. Native SwiftUI + supabase-swift
  on the shared Supabase backend. All 12 authoring phases done; then the iOS app gained a
  redesign (Liquid Glass), a new tab-bar IA, and many features beyond the initial 1:1 port
  (in-app language switch + full EN/NB localization, directional family relations + member
  popup, wishlist shareable links + PDF export, private/coloured calendar events + attendees,
  colour pickers for meals/lists/wishlists, birthday custom icon/colour, Google
  profile-completion prompt + background LocationSharingService, platform-aware push). 11 DB
  migrations applied to production (shared DB). During this window Android was frozen except two
  backported fixes. Full plan + phase table: [[05_Implementation_Plan/iOS Port Plan]].
- **🚀 Play Store release prep (2026-06-27)** — in-repo changes now **committed** on
  `chore/play-store-release` and **merged into `test`** (2026-07-05): applicationId →
  `com.sandnes.familyapp`, foreground-only location for v1, `*.aab` ignored. Firebase app for
  `com.sandnes.familyapp` is registered (debug + signed release builds pass); remaining external
  setup: Play account + store listing. Full runbook:
  [[06_Notes_and_References/Play Store Release Guide]].

## Delivered so far (Android, all merged to master)
Grouped into categorized notes under [[05_Implementation_Plan/Implementation Plan]]:
- [[05_Implementation_Plan/Delivered/Foundation, Build & Tooling]] — Compose rewrite, Gradle 9.4.1 /
  AGP 9.2.1, Spotless + detekt.
- [[05_Implementation_Plan/Delivered/Backend & Data Sync]] — Supabase, multi-device (Room removed),
  realtime, auth trigger, dual-ID storage.
- [[05_Implementation_Plan/Delivered/Feature Milestones]] — Family Map, notifications, calendar,
  profile picture, family share code.
- [[05_Implementation_Plan/Delivered/Chat]] — Messenger UI, participant model.
- [[05_Implementation_Plan/Delivered/Design & UI Polish]] — M17 polish, transitions, D1–D8 design
  track (complete).
- [[05_Implementation_Plan/Delivered/Testing & Quality]] — Hilt DI, 440 unit tests, lint/detekt,
  Maestro flows.
- Test users: testuser1-4@familyapp.test / TestPass123! — all in "Test Family" (join code: TESTFAM).

## Working agreement
- Release work resumed at the user's request on 2026-09-30. Continue the existing
  task/releaseReadiness branch using docs/HANDOVER.md and docs/RELEASE_READINESS_PLAN.txt.
- This Obsidian vault is the project source of truth for plans, documentation, and notes.
