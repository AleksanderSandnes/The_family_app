# Release readiness — in progress

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
