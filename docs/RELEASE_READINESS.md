# Release readiness tracker

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
