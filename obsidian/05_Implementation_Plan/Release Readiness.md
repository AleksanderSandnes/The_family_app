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
