# Security audit — in progress

Date: 2026-09-29. Scope: tracked source, all local Git refs and local Android checks.
Live Supabase, store signing and production hosting remain unverified.

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
has no projects in its available team. Supabase CLI login remains incomplete.

Remaining audit: live RLS/storage/auth/functions, reproducible database baseline,
encrypted token storage, deep-link validation, account deletion and moderation,
permissions/privacy manifests, signing and dependency vulnerability checks.
