# Release readiness tracker

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

## Completion gates from the supplied plan

- [ ] Tooling/account access complete (Supabase works; EAS/Vercel/signing still need verification).
- [ ] Branch cleanup/promotion policy, CI green on test, production release verified.
- [ ] Security audit complete with no unaccepted live warnings and deployed verification.
- [ ] Reproducible initial schema including Auth/Storage/Realtime/cron configuration.
- [ ] Domains/SMTP/association files live and checked; owner steps documented.
- [ ] Common-mistakes checklist verified: declarations, push, location, signing,
      crash reporting, offline/error states, accessibility/localisation and reviewer account.
- [ ] Android/iOS coverage gates met (80% overall, 90% logic) with drop detection.
- [ ] Automated version/release/signed-build/submission pipelines verified.
- [ ] Documentation and public legal pages live and linked in both native clients.
- [ ] Fictional demo seed, automated logged-in captures and complete store assets.
- [ ] Signed Android smoke tests and Play internal/closed-track uploads for both apps.
- [ ] iOS signing/TestFlight pipeline ready; Apple account/submission steps fulfilled.
- [ ] Final requirement-by-requirement completion audit and HTML report.

Original scope: [RELEASE_READINESS_PLAN.txt](RELEASE_READINESS_PLAN.txt).
