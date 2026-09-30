# Release readiness handover — 30 September 2026

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


> Owner decision (2026-09-30): accept the remaining Pro-only leaked-password
> protection warning in both projects. No upgrade is required; this warning
> does not block release readiness. Other security requirements still apply.

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
  down from 24. Auth confirmation is enabled; minimum password length is eight (verified through Management API read-back),
  Site URL is still localhost, SMTP is smtp.resend.com. Domain/sender operation,
  OTP/rate limits and production redirects need verification.


**Resumed on Linux at the user’s request. The release plan is not complete.**

Resume **task/releaseReadiness** in both repositories:

- https://github.com/AleksanderSandnes/HMI
- https://github.com/AleksanderSandnes/The_family_app

The latest code and handover are saved on those remote branches. The full updated
plan is docs/RELEASE_READINESS_PLAN.txt in both repos. Downloads contains copies:
release-readiness-plan.txt and release-readiness-handover.txt. The original local
folders were D:/Dev/HMI and D:/Dev/The_family_app. Credentials and ignored secrets
are machine-local and are not included in Git.

## Progress

- Release tooling installed; Docker/WSL repaired; hooks, secret scanning and
  task/test workflows established. Family keeps master by user decision.
- HMI dependencies updated, including Spring Boot 4.1.1. Shared core is at 100%
  coverage with per-file gates. App suites: 247 core, 115 web and 84 mobile tests.
- HMI auth storage, login redirects, cookie refresh, token error handling and
  account cache/draft isolation hardened on web and native.
- HMI backend now has 89 tests (one deliberate live-provider skip), 99.39% line and
  98.18% branch coverage, with 90% line/branch gates on every class. Password
  hashing is local. HMI task/test CI, Security and CodeQL are green.
- Family sessions use Android Keystore, with migration/restart verification and
  restored-session auth gating. Earlier verification: 502 Android, 251 iOS and
  four actual Keystore emulator tests. Native iOS uses GitHub macOS runners.
- Supabase function isolation, Vault webhook wiring and restrictive media writes
  are prepared locally. Production deployment is unfinished.

- Family clients now resolve stored media locators to five-minute signed URLs for
  images, voice notes, PDF images and gallery saves. Protected image caches are
  bypassed; sign-out/account changes reject signing/fetch results. All 513 Android
  tests, Spotless, detekt, lint and debug assembly pass. All 264 iOS simulator tests and strict Swift
  lint/format checks pass on eee5b7f. Live bucket settings/policies have not changed.


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

## Remaining work

- Live security remediation/deployment. Supabase: HMI xdttfrknoazcqcelieck;
  Family bntcznvsbyshetndbxfa. Last live audits had 9/24 security warnings.
- Family live SQL trigger contains a service-role credential. Coordinate Vault
  rollout and approved rotation; never put the value in Git. Add authenticated
  media rollout after client validation and deployment of staged read policies. Fresh migration reproducibility
  remains unproven.
- Coverage targets, end-to-end/release tests, branch protection, version/release
  automation and signed builds. Last full-source web/mobile lines: 20.88%/29.8%.
- Domains, support/controller details, SMTP, legal/deletion pages, moderation,
  store declarations, fictional screenshots/listings and approved uploads.
- iOS signing, Apple account actions and TestFlight/submission preparation.

## Preserve these decisions

Production main/master remain unchanged; no production merge was approved. Keep
Family master. Obtain approval for production merges, DNS, paid services, store
submissions, branch deletion and production secret rotation. Never use real user
data for demo assets. Follow repo instructions and required pre-commit checks;
update Family's Obsidian vault alongside changes.

Details: docs/RELEASE_READINESS.md, docs/SECURITY_AUDIT.md and Family's
obsidian/05_Implementation_Plan/Release Readiness.md.

## Branch protection update

Live master/test require 12 GitHub Actions quality checks and up-to-date
PRs, including administrators. Force pushes/deletion are blocked. New promotion
policy tests pass; trusted source enforcement is staged but awaits an approved
default-branch bootstrap. No bot bypass exists. Use verified task PRs to test;
production still requires explicit approval. See docs/BRANCH_PROTECTION.md.
