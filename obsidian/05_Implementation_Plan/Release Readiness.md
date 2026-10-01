# Release readiness — in progress

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


## Coverage reporting — 30 September 2026

Kover 0.9.11 runs with AGP 9.2.1 and all 527 Android unit tests. Full production
source coverage is 1717/13012 lines (13.20%). Logic (production data/util sources
and feature viewmodels) is 1569/3589 lines (43.72%). The Android workflow now
produces XML/HTML/JSON coverage and JUnit artifacts, plus a bounded job summary.
No production source was excluded to raise these numbers. The 80% overall and
90% logic thresholds and drop detection remain open release requirements.

Run from `android/`: `./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug`.
Then run `python ../scripts/coverage/android_summary.py app/build/reports/kover/reportDebug.xml`.
The detailed HTML report is `android/app/build/reports/kover/htmlDebug/index.html`.


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


Resumed by user request on 2026-09-30. Continue task/releaseReadiness using
docs/HANDOVER.md and docs/RELEASE_READINESS_PLAN.txt. Latest code and notes are
saved on GitHub; production master remains unchanged.

Started 2026-09-29 from the user-supplied release-readiness plan.

- Keep `master` as production branch (explicit user decision); work through
  `task/*` → `test` → `master`, with explicit approval before production merges.
- Full-history Gitleaks scan: 369 commits, no findings. This is automated evidence,
  not proof that every possible secret is absent.
- Installed Supabase CLI, Gitleaks, Maestro, ADB, bundletool and Android SDK 37.0;
  JDK 17 already provisioned by Gradle.
- GitHub, EAS and Vercel authentication checked. Supabase login in progress.
- Android baseline failed first because no SDK was installed, then because
  Spotless found existing formatting issues. Fixed formatting, cooldown constants
  and locale-aware share resources; full Spotless/detekt/lint/unit-test/debug-build
  checks now pass locally.
- Added shared commit-message/Gitleaks hooks and reinforced secret-file ignores.
- Added Android cleartext/backup restrictions and static web security headers.
- Existing Swift CI has outstanding lint failures; security and CI work remains open.
- Swift lint repair: extract birthday picker and reaction preference types, separate
  typing broadcast extension, replace wishlist argument list and reset-test tuple
  with named records. New macOS build/test workflow uses a placeholder backend and
  exports XCTest coverage; compilation and required coverage gates remain pending.
- Existing domain `thefamilyapp.app`, SMTP notes and fictional demo accounts were
  found in the vault. Verify live state before creating replacements.

Remaining: live security audit, branch protection, dependency remediation,
coverage and release workflows, legal/account-deletion pages and app flows,
store assets, signed builds and approved submissions. No release is claimed ready.

- 2026-09-30: Supabase CLI authentication succeeded; both projects are healthy.
  Live Family advisors report three mutable function search paths, privileged
  function exposure and disabled leaked-password protection; definition audit pending.
- Native iOS build exposed a font-helper name collision and missing shopping user-ID
  property. Fixed both, remaining Swift lint findings, and export formatter repairs
  from CI for review. Native build/test verification remains pending.

- Applied the authoritative SwiftFormat repair artifact from macOS CI (40 files).
  SwiftLint passed before formatting; rerun both gates and native tests after this change.

- Extracted the chat title view to keep ConversationScreen within the existing
  strict file-size gate after applying SwiftFormat. No lint thresholds changed.

- Native iOS application now compiles on CI; XCTest compilation exposed four
  async calls inside XCTUnwrap autoclosures. Await results before unwrapping.
  Full simulator execution and coverage still pending.

- Native simulator ran 251 tests: one failure exposed a real resend cooldown race.
  Set reset/verification cooldown state synchronously before launching the timer,
  preventing an immediate resend from slipping through before the timer task starts.
- Captured a redacted live public schema and recovered 34 remote migration files.
  These are evidence, not yet a verified fresh-install sequence; bootstrap gaps remain.
  Live schema contains a service-role webhook credential, removed from repo snapshot.
  Prepare Vault-backed webhook wiring before requesting production key rotation.

- iOS build/test CI is green: 251 tests. Verified work advanced to test, not master.
- Docker Desktop updated to 4.93.0 and WSL to 3.0.1; engine and hello-world pass.
- Prepared private-schema function wrappers and Vault-backed push webhook; local
  schema restore, family-isolation/permission tests and Edge authorization tests pass.
  Production policies/media fixes, webhook rollout and key rotation remain pending.
- Added backend-security CI for isolated SQL restoration and notification auth tests.
- Backend-security CI passed for the restored schema, function isolation and
  Vault-backed webhook installation. No production database changes applied.
- Validate message webhook envelopes/identities/types, bound streamed requests
  to 64 KiB and notification text to 200 Unicode characters. Eight Edge tests pass.
  Updated push setup docs to use Vault and coordinate server-only authorization.
- Prepared and locally tested restrictive media write policies against the existing
  permissive policies. Protect owner wish/avatar paths, conversation membership and
  family photos, including UPDATE's destination path. Buckets are still public;
  client authenticated media resolution and coordinated private-read rollout remain open.
- Added CodeQL scans for Android, Swift, notification TypeScript and workflows,
  plus Gradle/GitHub Actions Dependabot entries. First scan verification pending.
  Swift dependency updates need a supported tracked manifest; current dependency
  declarations live in XcodeGen project.yml and are not monitored by Dependabot.
- Replace default Android Supabase token/PKCE persistence with Keystore-backed
  AES-GCM preferences. Migrate plaintext only after encrypted persistence, authenticate
  storage slot names and serialize refresh/logout. Added crypto and migration tests;
  physical/emulator Keystore and upgrade/restart verification remain open.
- API 37 headless emulator is operational with WHPX acceleration. Real Keystore
  ciphertext persistence/deletion and process-restart restoration passed using
  dedicated fictional preferences/aliases. Signed release login/upgrade flows remain open.
- Four real Keystore instrumentation tests pass, including legacy plaintext-settings
  migration/cleanup. Added an emulator-only PowerShell script to repeat them and
  verify persistence across separate instrumentation invocations and force-stop.
- Android auth gate now observes real session status alongside cached app identity.
  Require successful restoration before main navigation, clear offline eligibility
  after sign-out/revocation, and retain access during transient refresh failures
  only after a verified session. Profile completion retains the existing auth flow.
  Added policy and ViewModel transition tests.
- The earlier Swift CodeQL build exceeded its 40-minute limit while compiling both
  simulator architectures. Compile the runner's native architecture for the
  security scan and upload the build log on success/failure. The remaining language
  scans and Android CI passed. [The updated scan](https://github.com/AleksanderSandnes/The_family_app/actions/runs/36718982820)
  passed all four languages, and verified changes advanced to `test`. Its new
  branch run is pending; production `master` remains unchanged.

- Prepared authenticated media reads on Android and iOS. Stable database URLs resolve
  to fresh five-minute signed links; invalid object/origin/path links and missing
  sessions fail closed. Protected image requests bypass legacy memory/disk caches
  and reject account changes during signing/fetching. PDFs, gallery saves and voice
  notes use the same resolver. All 513 Android tests and required checks pass;
  13 new iOS tests await native CI. Private bucket/read-policy rollout remains open.

- Staged restrictive private-read policies alongside legacy permissive policies.
  Local rollback tests verify anonymous/unregistered/cross-family denial, valid
  conversation/family/shared-wishlist reads, forged locator/user-ID denial and
  immediate share/member revocation. Existing write tests still pass. Removing
  either guard makes the tests fail; local security advisors report no issues.
  CI now repeats both read and write checks. Full Storage HTTP tests, minimum client
  version coordination and private-bucket rollout remain open; no live flags changed.
  Rollout details: supabase/security/PRIVATE_MEDIA_ROLLOUT.md.

- Native media validation passed: 264 iOS simulator tests and strict Swift gates
  on eee5b7f. Android required checks and all 513 tests pass. Local full Auth/Storage
  HTTP testing passed 65 assertions across four private buckets, including real
  upload/upsert, signing/authenticated download, cross-account/anonymous/public
  denial, shared-wishlist revocation and token expiry. Found and fixed the missing
  chat-media UPDATE policy so owned uploads can be replaced. SQL read/write tests
  and local security advisors still pass; HTTP smoke testing is now wired into CI.
  Native signed-release UI flows and production rollout remain open.

- Prepared iOS restored-session navigation hardening: cached app IDs cannot unlock
  navigation; bootstrap restores Supabase credentials and resolves their matching
  profile. Ignore non-nil local INITIAL_SESSION as authorization, close/clear on
  sign-out, reject profile responses after token/account changes and cancel stale
  work. Verified runtime refresh keeps the existing identity; switching account
  resolves a fresh profile and resets push sync. Signed-in SwiftUI screens are
  keyed by app user ID so their drafts/navigation/view models are recreated.
  Added 17 tests; native CI verification is pending. Android required checks pass.

- GitHub master/test now have live strict quality protection: 12 named Actions
  checks, required PRs/up-to-date branches, administrator enforcement and blocked
  force pushes/deletion. Removed workflow path filters so required jobs always
  report. Added four promotion-policy tests and Python CodeQL scanning. Trusted
  source checking uses default-branch code and head-commit statuses; activation
  awaits an approved production bootstrap. No release-bot bypass exists.

- iOS auth-gate native run e3798e3 passed all 281 tests with no failures.
  SwiftLint passed; apply the authoritative SwiftFormat patch and rerun its gate.

- In-app account deletion (Play/App Store requirement): `delete-account` Edge Function
  re-validates the caller's JWT, requires an explicit confirmation body, removes the
  user's storage media, transfers shared family ownership to a remaining member and
  deletes the auth user (cascading owned rows) via service-role-only SQL helpers
  (supabase/security/account_deletion.sql). 6 unit tests plus a local Auth/Storage/
  Postgres HTTP integration test (wired into backend-security CI). Android and iOS
  Settings gain an Account section: privacy policy, terms and Delete account with a
  confirmation dialog; all 516 Android tests and checks pass; iOS tests added, native
  CI verification pending. Live function deployment and SQL migration are not applied.
- Static legal pages in web/: privacy (deletion section updated, push providers
  added), terms, support and delete-account. The Vercel project thefamilyapp-web
  built from the repo root, so thefamilyapp.app returned 404; Root Directory must be
  `web` so the pages and web/vercel.json headers are served.

- Release tooling and repo docs (2026-09-30): dependency-free `scripts/release/`
  computes the SemVer bump from Conventional Commits, writes one shared version and
  build number into `android/app/build.gradle` and `ios/project.yml`, and prepends
  CHANGELOG.md (7 node tests, run by the Security workflow). The bump is prepared on
  a task branch into `test`, so protected `master` needs no bot push. Added LICENSE
  (proprietary), SECURITY.md, CONTRIBUTING.md, docs/RELEASE.md, docs/ENVIRONMENTS.md,
  PR/issue templates and CODEOWNERS. Backend CI fix: the account-deletion fixture
  disables the push webhook trigger while seeding messages.
- UGC moderation (Play UGC policy / App Store 1.2), 2026-09-30: `supabase/security/moderation.sql`
  adds `content_reports` (written only via the `report_message` RPC, which checks the
  reporter is a conversation participant, rejects own messages and snapshots the message
  server-side) and `user_blocks` (owner-only RLS). `push-on-message` skips recipients who
  blocked the sender. Android + iOS: long-press another member's message → Report (reason
  picker) / Block; 1:1 chat menu → Block/Unblock; blocked senders' messages are hidden.
  Tests: 8 SQL isolation checks in backend-security CI, Android ChatModerationTest +
  5 ChatViewModel tests, iOS ChatModerationTests (8). Terms/privacy pages describe
  reporting, blocking and 24 h review. Live SQL not applied yet.

- Resume verification (2026-09-30): moderation/account-deletion sources compiled
  on macOS run 36756809107 (commit 0f03682); all 292 XCTest cases passed. Android
  Spotless/detekt/lint/unit tests/debug assembly passed locally. Fixed Node 22
  release-test discovery to use explicit *.test.mjs files (7 pass) and shortened
  the iOS job summary while retaining full coverage in the artifact. Coverage
  thresholds and production rollout are still open.

## Verified continuation — 30 September 2026, 20:46 Oslo

This section supersedes older rollout status below. The full plan is still open.
Supabase CLI is authenticated and both projects are linked; the direct Codex
Supabase MCP server is configured with OAuth authentication. Production Git branches
have not been merged. Leaked-password protection was requested through the
Management API and rejected with HTTP 402 for both projects (Pro required).
No billing upgrade occurred. Email/password, SMTP, domains, signed builds, store
uploads, screenshots, coverage gates and complete bootstrap still require work.

- Family task commit `8e149f0`: release-tooling (seven tests), Android,
  iOS, SwiftLint, backend-security and promotion checks passed. Latest native
  sources passed 292 XCTest cases. Full coverage artifact measures only 20.43%
  app lines (5001/24482); the original 80%/90% goals are not achieved. The job
  summary now prints target totals without exceeding GitHub's 1 MiB limit.
- Five ordered security migrations applied live: private function wrappers,
  storage write guards, storage read guards, account-deletion helpers, moderation.
  `delete-account`, `push-on-message` and `daily-reminders` deployed with JWT
  verification enabled; unauthenticated deletion/push POSTs return 401.
- Fresh replay of the live public schema passed function isolation, moderation,
  storage read/write tests, 65 private-media HTTP checks and 30 deletion checks.
  Tests used only a disposable localhost stack with fictional fixtures. Bucket
  visibility remains public pending released signed-URL clients/minimum version.
- Vault webhook migration transferred the existing server credential entirely
  inside Postgres and replaced the legacy credential-bearing trigger with
  `private.notify_chat_message()`. Local rollback tests verify exact transfer,
  trigger replacement, no embedded credential and denial of client execution.
  Production secret rotation remains open; moving a credential does not revoke it.
- Fresh security advisors: one warning (Pro-only leaked-password protection),
  down from 24. Auth confirmation is enabled; minimum password length is six,
  Site URL is still localhost, SMTP is smtp.resend.com. Domain/sender operation,
  OTP/rate limits and production redirects need verification.

- Owner accepted the final Pro-only leaked-password protection warning in both
  projects (2026-09-30). Do not upgrade billing for this feature; it is an accepted
  release exception. New registration/reset passwords are being raised to eight
  characters with existing login credentials preserved.

- Password policy alignment: Android/iOS registration and resets require eight
  characters, with English/Norwegian messages. Existing six/seven-character login
  credentials remain accepted. Added registration rejection and legacy-login
  regressions; Android required checks and native macOS CI must verify the change.

- Signed Android workflow (`android-release.yml`) added, manual and inactive until
  `RELEASE_ANDROID_ENABLED=true`: verifies the production tip, validates the public
  Supabase client config (`scripts/release/client-config.mjs`, publishable or anon
  keys only), builds and verifies a signed AAB in the `production` environment, and
  optionally uploads a Play internal **draft** behind a separate approval. Not yet run:
  CI secrets and Play account steps are owner actions. Names are in `docs/ENVIRONMENTS.md`.

- iOS coverage (2026-10-01): `SupabaseClientProvider.testOverride` is a test-only seam;
  `FamilyAppTests/Support/StubSupabase.swift` serves fictional model fixtures through an
  in-process `URLProtocol` and records requests, so the real `FamilyRepository`,
  auth round-trips and `StorageService` path rules run in unit tests. Screen render tests
  host every screen/sheet with the fictional Nordmann family (light + dark), and
  `StoreScreenshotTests` attaches 1320x2868 App Store screenshots exported by CI as
  `ios-store-screenshots`. `scripts/coverage/ios_summary.py` reports app-only coverage
  (third-party packages excluded): 20.44% -> 76.02% overall, 58.96% -> 83.97% logic after
  the first pass, then 83.90% overall / 90.87% logic (388 tests) after view-model gap and
  state-render tests. ios.yml now enforces both gates (`--enforce`). Several interaction-only sheets became `internal` so they
  can be rendered directly.
