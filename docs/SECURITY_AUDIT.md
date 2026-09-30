# Security audit — in progress

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


Date: 2026-09-29. Scope: tracked source, all local Git refs and local Android checks.
Live Supabase metadata inspected on 2026-09-30. Store signing and production hosting remain unverified.

| Finding | Remediation / evidence | Status |
| --- | --- | --- |
| No enforced history secret scan | Gitleaks scanned 369 commits with no findings. Added pre-commit and CI scan plus commit-message hook. | Local scan passed; CI pending |
| Secret-file ignores incomplete | Explicit env, Firebase, keystore, local properties and service-account patterns. | Fixed locally |
| Android allows backup of app data | Disable backup; validate merged release manifest and device transfer behavior before release. | Partial |
| Android transport policy implicit | Explicitly disable cleartext; trust system CAs only. | Fixed locally |
| Static web security headers absent | Add CSP, HSTS, framing, content-type, referrer and permissions policies. | Added locally; deployment pending |
| Production/test quality protection | GitHub API confirms 12 required Actions checks, strict up-to-date PRs and administrator enforcement. Keep master by user decision. | Quality protection live; trusted source/bot activation pending |
| Existing Android quality checks failing | Correct formatting, name cooldown timing constant and resolve translated share strings in composition. Full Spotless/detekt/lint/unit-test/debug-build checks pass locally. | Fixed locally |
| Existing Swift CI failing | Fixed native compilation, strict lint/format findings and resend cooldown race. All 251 simulator tests pass in CI. | Fixed on task/test |

The vault records an existing domain, Resend SMTP setup and fictional review family.
Verify the live projects before replacing these. Current Vercel login `apsandnes`
has no projects in its available team. Supabase CLI authentication and project linking are complete.

Remaining audit: live RLS/storage/auth/functions, reproducible database baseline,
encrypted token storage, deep-link validation, account deletion and moderation,
permissions/privacy manifests, signing and dependency vulnerability checks.

## Live findings and locally verified remediation (2026-09-30)

- All 19 public tables have RLS enabled; no public-schema views found.
- All four media buckets are public. Group/wish image policies permit any signed-in
  user to overwrite/delete other users' files. Private media and path rules are open.
- Three functions had mutable search paths. Ten privileged functions were reachable
  anonymously. Prepared private-schema implementations and public invoker wrappers;
  tests verify family isolation, blocked anonymous RPCs and valid authenticated joins.
- Database webhook trigger embeds a service-role JWT. Removed the credential from
  the captured schema. Prepared Vault-backed wiring and server-only notification
  authorization. Local installation, Deno type checks and authorization tests pass.
  Live deployment and any production credential rotation require reviewed rollout.
- Email confirmation enabled, anonymous sign-ins disabled. Site URL is localhost;
  redirect allow-list contains familyapp://auth only. Domain returns HTTPS 404.
- Leaked-password protection is off and requires the paid Pro plan. Password
  requirements/OTP/rate-limit review and billing decision remain open.
- Recovered 34 migration-journal files, captured a redacted public baseline and
  restored it locally. Original history lacks initial table creation. Reconciliation
  and full storage/auth/Realtime/cron bootstrap are pending; no remote history edits.
- Native iOS CI now passes all 251 tests plus strict SwiftLint/SwiftFormat.
  Coverage targets remain unmet/unverified. Android checks remain green.
- Isolated database and Edge Function security CI passed on commit 84db54d.
- Notification webhook requests now enforce JSON insert envelopes, UUIDs and
  message types, a streamed 64 KiB body limit and bounded Unicode text previews.
  Eight authorization/input tests pass locally; production deployment pending.
- Prepared restrictive storage write guards that remain effective alongside the
  existing permissive policies. Preserve legacy app/auth-ID paths for wish images,
  owner avatar paths, conversation-member images and family-photo paths; chat
  uploads require membership and the caller's auth-ID folder. Tests exercise valid
  uploads, cross-user/family insert/update/delete and attempts to move an object
  into another user's path. Private reads/client URL resolution and rollout remain open.
- CodeQL for Android/Swift/TypeScript/workflows passed on dc3c76f. Added Dependabot
  for Gradle and GitHub Actions. Swift dependency manifest support remains open.
- Android now supplies explicit encrypted Supabase session and PKCE storage.
  AES-256-GCM uses an Android Keystore key, random IVs and authenticated slot names;
  preferences contain ciphertext only. Legacy settings are removed only after
  encrypted persistence succeeds. Corruption fails closed; serialized refresh/logout
  cannot restore a session after logout completes. Cryptography/migration unit tests
  added; signed release upgrade/login/logout and minimum API device checks remain pending.
- API 37 emulator verified real Android Keystore encryption, ciphertext-only
  preferences, object recreation, deletion and restoration after force-stop in
  separate instrumentation invocations. Full signed release/auth-flow and minimum
  API device validation remain pending; this does not claim complete device coverage.
- Four Keystore instrumentation tests passed, including actual plaintext-settings
  migration and cleanup. `android/scripts/test-encrypted-auth.ps1` reproduces the
  tests and the separate process-restart check on an isolated emulator.
- Android navigation now consults Supabase session status. Cached app-user
  preferences cannot bypass initialization, missing/revoked sessions or failed
  restoration. Temporary refresh failures retain offline access only for the
  app identity previously observed with an authenticated session. Policy and
  ViewModel tests cover these transitions and permission/profile completion.
  Full signed release/login/logout smoke checks remain pending.

- Client private-media prerequisite prepared: per-read short signed URLs, strict
  project/object validation, no public fallback and protected-image cache bypass.
  Android real Coil pipeline tests reject cached private pixels after logout/account
  switch and discard results when identity changes during fetching. All 513 unit
  tests and required Android checks pass. iOS resolver/URLSession/cache tests await
  macOS CI. Existing public buckets and permissive read policies still require
  coordinated backend rollout; this change alone does not make stored media private.

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

- Live GitHub quality protections enabled on master/test: 12 named
  GitHub Actions checks, strict PR/update requirements, administrator enforcement
  and blocked force pushes/deletion. Trusted promotion-source enforcement is
  staged but awaits an approved default-branch bootstrap. No production commits
  changed and no release-bot bypass exists. See docs/BRANCH_PROTECTION.md.
