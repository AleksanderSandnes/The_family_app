# Release readiness handover — 30 September 2026

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
