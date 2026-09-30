# Security audit — in progress

Date: 2026-09-29. Scope: tracked source, all local Git refs and local Android checks.
Live Supabase metadata inspected on 2026-09-30. Store signing and production hosting remain unverified.

| Finding | Remediation / evidence | Status |
| --- | --- | --- |
| No enforced history secret scan | Gitleaks scanned 369 commits with no findings. Added pre-commit and CI scan plus commit-message hook. | Local scan passed; CI pending |
| Secret-file ignores incomplete | Explicit env, Firebase, keystore, local properties and service-account patterns. | Fixed locally |
| Android allows backup of app data | Disable backup; validate merged release manifest and device transfer behavior before release. | Partial |
| Android transport policy implicit | Explicitly disable cleartext; trust system CAs only. | Fixed locally |
| Static web security headers absent | Add CSP, HSTS, framing, content-type, referrer and permissions policies. | Added locally; deployment pending |
| Production branch lacks protection | GitHub API confirms `master` unprotected. User requested keeping `master`. | Open |
| Existing Android quality checks failing | Correct formatting, name cooldown timing constant and resolve translated share strings in composition. Full Spotless/detekt/lint/unit-test/debug-build checks pass locally. | Fixed locally |
| Existing Swift CI failing | Retrieved annotations: file/type length, wishlist parameter count, tuple size and long lines. | Open |

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
- Added isolated database and Edge Function security tests to CI; result pending.
