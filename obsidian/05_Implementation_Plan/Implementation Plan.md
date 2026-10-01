# Implementation Plan

## Source of truth
This Obsidian vault is the canonical source for project plans, notes, and architecture decisions.
Update it here first and keep it current as implementation progresses.

## Active tracks
- **⏳ [[Release Readiness]]** — security, coverage, CI and store preparation; keep `master` per user decision. Android coverage gates are merged (86.7% overall / 90.2% logic); iOS coverage gates merged in #15 (83.90% / 90.87%). Final store-assets PRs and owner-gated production rollout remain open.
- **✅ Signup email verification (Android + iOS)** — delivered 2026-07-12, branch
  `feat/signup-email-verification`. `enable_confirmations = true` live in production;
  new signups enter a 6-digit code (branded confirmation email) on a shared
  VerifyEmailScreen before the permission screen; unverified logins route into the
  same screen instead of a dead end. Old app versions error at signup until updated
  (accepted). Unit tests green (Android); iOS authored, Mac compile pending.
- **✅ Forgot-password reset (Android + iOS)** — delivered 2026-07-12, branch
  `feat/forgot-password-reset`. 6-digit OTP code flow (no deep links): repo methods +
  reset state machine + two-step screen on both platforms, EN/NB strings, unit tests
  (Android green; iOS authored, Mac compile pending). Email delivery upgrade to Resend
  SMTP via **thefamilyapp.app** (bought 2026-07-12 through Vercel) is a dashboard-only
  runbook: [[06_Notes_and_References/Password Reset & Resend Email Setup]] — the Reset
  Password email template edit ({{ .Token }}) is required before the flow delivers codes.
- **✅ [[Android Parity Track]]** — delivered and merged to `master` (2026-07-10 → 07-11, branch
  `feat/android-ios-parity`). Android brought up to the iOS feature set + Liquid Glass look;
  milestones M0–M7 all done, plus a post-parity design pass, on-device fix batches, the new
  launcher icon/name, and a signed production APK. No active track right now.
- **✅ [[iOS Port Plan]]** — native SwiftUI app, delivered and merged to `master` (2026-07-05 →
  07-10). All 12 authoring phases + a redesign + new IA + many features; 11 DB migrations applied
  to production.

## Delivered milestones (categorized)
The delivered Android milestones are grouped into linked notes rather than one long log:
- [[Delivered/Foundation, Build & Tooling]] — M1–8 (tooling, Compose rewrite), M20 build infra,
  Spotless/detekt, kotlin-reflect fix, dead-space fix.
- [[Delivered/Backend & Data Sync]] — M16 Supabase, M9+11 multi-device (Room removed), M19
  realtime, auth trigger, storage/dual-ID, data isolation, the okhttp realtime fix.
- [[Delivered/Feature Milestones]] — M10 Family Map, M12 notifications, M13 calendar, M15 profile
  picture, group chat settings, family share code, birthday editing / wishlist icons.
- [[Delivered/Chat]] — M20 Messenger UI, M21 participant model, shared-VM delete fix.
- [[Delivered/Design & UI Polish]] — M17 polish (8 batches), M18 transitions, M23 superseded →
  D1–D8 design track (complete).
- [[Delivered/Testing & Quality]] — M22 background loading, M24 unit testing + Hilt DI, suite
  revival, quality gates.

## Milestone status (delivered ✅)
| # | Milestone | Detail |
|---|-----------|--------|
| 1–7 | Tooling, audit, docs, backend draft, naming, Compose kickoff, theme baseline | [[Delivered/Foundation, Build & Tooling]] |
| 8 | Full Compose redesign | [[Delivered/Foundation, Build & Tooling]] |
| 9 + 11 | Multi-device sync via Supabase (Room removed) | [[Delivered/Backend & Data Sync]] |
| 10 | Family Map (Familiekart) | [[Delivered/Feature Milestones]] |
| 12 | Notifications (local + WorkManager) | [[Delivered/Feature Milestones]] |
| 13 | Calendar enhancements (all-day, ranges, ISO, month grid) | [[Delivered/Feature Milestones]] |
| 14 | Bug: Home tab unresponsive on top-level routes | [[Delivered/Foundation, Build & Tooling]] |
| 15 | Profile picture | [[Delivered/Feature Milestones]] |
| 16 | Backend modernization (Supabase chosen) | [[Delivered/Backend & Data Sync]] |
| 17 | Frontend polish pass (8 batches) | [[Delivered/Design & UI Polish]] |
| 18 | Navigation transitions | [[Delivered/Design & UI Polish]] |
| 19 | Realtime live sync for core features | [[Delivered/Backend & Data Sync]] |
| 20 | Build infrastructure upgrade + Messenger-style chat | [[Delivered/Foundation, Build & Tooling]] · [[Delivered/Chat]] |
| 21 | Participant-based conversations | [[Delivered/Chat]] |
| 22 | Background loading & optimistic mutations | [[Delivered/Testing & Quality]] |
| 23 | Design overhaul ⛔ superseded → D1–D8 track | [[Delivered/Design & UI Polish]] |
| 24 | Unit testing + full Hilt DI | [[Delivered/Testing & Quality]] |
| 25 | iOS port + monorepo restructure | [[iOS Port Plan]] |
| 26 | Android ⇄ iOS parity + Liquid Glass (M0–M7 + post-parity design pass) | [[Android Parity Track]] |

## Design track
- **✅ UI/UX overhaul (D1–D8)** — complete & merged to master (2026-06-27, PR #11). Per-screen
  specs: [[Design_Improvements/00 Design Improvement Plan]]; discovery/audit:
  [[Project - UI - UX - improvements]]. Superseded the old Milestone 23.

## Planning rule (mandatory first step)
1. Verify AGP/Gradle/JDK alignment and run a baseline build/tests.
2. Only then execute feature work for the milestone branch.
3. Branch workflow: `task → test → master` (never commit directly to `master` or `test`).

## Long-term goals

- Dashboard session isolation: discard refresh/family-reload results after the
  active user changes. Delayed success and failure regressions reproduced the
  sign-out/account-switch race found in final store-assets CI (2026-10-01).

- ✅ iPad support and store captures: native iPad target and orientations enabled;
  six reviewed 2064x2752 images captured on iPad Pro 13-inch (M5), run 36861490097,
  and imported on 2026-10-01. Production promotion and store submission remain pending.

- ✅ Reviewed English iOS store screenshots: six fictional-data images at
  1320x2868 imported on 2026-10-01. Native signing, store submission and the
  approved production promotion remain pending; see `docs/HANDOVER.md`.
- ⏳ Branch protection: live strict quality/PR protection enabled on master/test.
  Trusted source activation and release-bot provisioning remain open.
- ⏳ iOS auth gate: restoration/profile binding and sign-out handling implemented,
  with 17 new behavioral/race tests. All 281 native tests passed on e3798e3; final formatter verification and signed
  release smoke remain open.
- ⏳ Private media: Android/iOS signed URL reads implemented; Android 513 tests and
  required checks pass. Read guards pass local isolation/write-compatibility tests; 264 iOS tests/strict
  Swift checks and 65 real Auth/Storage HTTP assertions pass. Signed-release UI
  validation, minimum client coordination and private buckets remain pending.
- Release work resumed by user request on 2026-09-30; portable handover and full
  updated plan are saved in docs/HANDOVER.md and docs/RELEASE_READINESS_PLAN.txt.
- Swift CodeQL recovered: [run 36718982820](https://github.com/AleksanderSandnes/The_family_app/actions/runs/36718982820) passed all four languages after switching to the runner's native simulator architecture. Build-log artifacts remain available. Verified changes advanced to `test`; its new run remains pending.
- Deliver a premium-feeling family app on both Android and iOS.
- Preserve the original product concepts while modernizing the implementation.
- Keep the codebase maintainable and easy to extend.
