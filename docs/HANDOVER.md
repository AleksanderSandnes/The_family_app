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
  tests, Spotless, detekt, lint and debug assembly pass. iOS changes and 13 new
  tests await macOS CI. Live bucket settings/policies have not changed.

## Remaining work

- Live security remediation/deployment. Supabase: HMI xdttfrknoazcqcelieck;
  Family bntcznvsbyshetndbxfa. Last live audits had 9/24 security warnings.
- Family live SQL trigger contains a service-role credential. Coordinate Vault
  rollout and approved rotation; never put the value in Git. Add authenticated
  media rollout after client validation and restrictive read policies. Fresh migration reproducibility
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
