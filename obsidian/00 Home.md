# The Family App

## Current release status — 1 October 2026

Reviewed English iOS store screenshots are in `store/ios/screenshots/iphone-6.9/en/`.
Android coverage gates are delivered; iOS coverage PR #15 and store assets PR #14
must pass required checks before merging into `test`. Promotion PR #16 awaits
owner review. See `docs/HANDOVER.md`; production activation remains owner-gated.


## First release-proposal workflow verification — 30 September 2026

HMI PR #46 merged into `test` after all task/PR checks passed. The first
Prepare Release workflow (36775117818) passed and generated a proposal for
5.0.0/build 4 without writing a branch, tag or release. Artifact inspection
found that the newly created CHANGELOG.md was absent from git diff. Both
workflows now include that file with intent-to-add; a disposable-Git regression
verifies that the patch contains the changelog and release notes. The fix's
remote workflow verification remains pending. Production main/master are unchanged.


## Release automation prepared — 30 September 2026

`prepare-release.yml` computes a version proposal after changes land in `test`.
It always uploads a patch; set `RELEASE_PREPARE_ENABLED=true` to let it open a
`task/releaseV...` PR. Manual dispatch on `test` defaults to preview. Preparation
recognizes an already-bumped version and never bumps it again against the same
production base. Every version file, native build number and changelog section
must agree; release build numbers must exceed the last release and Play's existing 3.

`release.yml` listens for completed quality workflows on `master`. It checks the
exact commit, current production tip and latest push run of every workflow listed
in `scripts/release/pipeline.json`. Missing, pending, failed or skipped quality
workflows prevent drafting. Superseded production commits cannot release.
With `RELEASE_DRAFT_ENABLED=true`, its `production` environment job creates an
immutable version tag and a **draft** GitHub Release. Repeated runs reuse the
same release and reject a tag that points to another commit. Publish the draft
only after the owner reviews the release. This workflow does not upload to stores.

Both activation variables are currently unset. Production branches, environment
protections, credentials and activation must be reviewed before enabling them.
Coverage targets, signed native build/store jobs and the first approved production
promotion remain open. Local release integration tests use disposable Git repos:
dry runs do not change files, version preparation is repeat-safe, inconsistent
versions/builds/missing notes fail, and every quality workflow must pass.

GitHub Actions PRs created with `GITHUB_TOKEN` require workflow approval; an
optional scoped `RELEASE_BOT_TOKEN` enables automatic task/PR CI. It needs Contents
and Pull requests write permission only and no protected-branch bypass. Repository
Actions settings must allow PR creation. Token behavior reference:
https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow


## Public-page factual review — 30 September 2026

Updated draft privacy/deletion pages to describe precise location permissions,
sharing with invited recipients, and current media download-link access accurately.
Removed the unverified 30-day hosting-backup promise: live backup inventory is
empty and PITR disabled; Supabase retention varies by plan. Provider reference:
https://supabase.com/docs/guides/platform/backups . Support and deletion-request
emails remain links; no external message was sent. Protected preview https://thefamilyapp-jg761gpts-aleksander-sandnes-projects.vercel.app
returns HTTP 200 for home/privacy/terms/support/delete-account with CSP, HSTS,
frame denial and nosniff headers. Chrome verifies privacy navigation and deletion
instructions. Publication and owner legal review remain open.

Recovery CI passed the schema/platform SQL checks and the existing security job,
but its final negative-check step failed because ripgrep is absent on the runner.
Changed the runner check to standard grep. Required Android checks and seven
release-tooling tests pass locally; remote recovery CI must pass before promotion.


## Auth configuration and empty-database recovery — 30 September 2026

Family's live Auth Site URL is now `https://thefamilyapp.app`; read-back confirms
password minimum eight, OTP lifetime 600 seconds, and signup confirmation enabled.
`supabase/config.toml` mirrors these values. Applied only a narrow Site URL patch,
without pushing unrelated Auth defaults or SMTP credentials.

`supabase/baseline/restore.sql` now restores an empty isolated application database
with the staged security patches, private signup trigger, 14 Realtime tables and
four initially private media buckets. Local SQL checks and nine real Auth/profile/
private-bucket HTTP checks pass; replay against an existing schema is rejected.
CI verifies the same recovery path and platform reapplication. This is an explicit
recovery entry point, not a rewrite of the historical migration journal. Push
Vault/cron setup and ordered migration reconciliation remain open.


Android coverage reporting verified: 13.20% overall / 43.72% logic; targets remain open.

## Verified native CI and deployment inspection — 30 September 2026

Password-policy commit `f6003cb` passed Android CI and macOS CI: 527 Android unit
tests and 293 iOS tests, with no failures. These results do not satisfy the open
coverage targets. The pre-existing Claude Code Review provider failure remains.

Vercel CLI 62.0.0 is authenticated as `aleksandersandnes` and can access the
`thefamilyapp-web` and `hmi` projects. Live Family settings already use root
`web` and production branch `master`. Rebuilt the existing production source from deployment
`dpl_EfvfE22hiLPHtBksubSbZv6WSoa3`. Preview validation returned HTTP 200, then a
production rebuild restored https://thefamilyapp.app/ and /privacy to HTTP 200.
Current production URL: https://thefamilyapp-27y6nx70p-aleksander-sandnes-projects.vercel.app.
This repairs existing public pages; new support/terms/deletion pages still require
release validation and publication. Production Git branches remain unchanged.


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
- **Live security rollout 2026-09-30** — private RPC wrappers, storage guards, moderation, deletion helpers and Vault webhook deployed. One Pro-only leaked-password warning remains per project. Family clients pass 292 iOS tests and Android checks; coverage/store/signing/private-bucket rollout remain open. See [[05_Implementation_Plan/Release Readiness]].
- **Release branches protected** — master/test now require 12 Actions checks and up-to-date PRs, including administrators. Trusted source enforcement and release-bot setup still need an approved production bootstrap.
- **iOS session gate hardening prepared** — validate restoration and profile identity, reject stale responses, observe sign-out and reset the account’s view lifetime. All 281 native tests passed on e3798e3; strict SwiftLint passed, and the SwiftFormat repair is applied for final CI verification.
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
