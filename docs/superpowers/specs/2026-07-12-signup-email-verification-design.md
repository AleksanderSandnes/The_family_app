# Signup Email Verification — Design

**Date:** 2026-07-12
**Status:** Approved
**Branch:** `feat/signup-email-verification`
**Builds on:** `2026-07-12-forgot-password-reset-design.md` (reset flow, Resend SMTP, CLI-managed auth config)

## Problem

New accounts are created without proving the user owns the email address. The user wants email verification as a step between registration and the permission screen.

## Decision summary

- **Mechanism:** 6-digit OTP code typed into the app (same as the shipped password-reset flow). `verifyEmailOtp(type = SIGNUP)` creates the session only after the code is entered.
- **UI:** one shared **VerifyEmailScreen** (route `verify_email`) used by two entry paths — after registration, and when an unverified account tries to log in. Not an inline register step, because the login path needs the same screen.
- **Placement guarantee:** verification happens before any session exists; once verified, `completeSignInAfterConfirmation()` flips the auth gate to `NeedsPermissions` → `PermissionsOnboardingScreen`. No gate changes needed.
- **Rollout:** `enable_confirmations = true` flips **immediately** (user accepted that signup on old installed versions errors after account creation until the update is distributed).
- **Scope:** Android first, iOS parity second. Google OAuth signups unaffected (provider emails are pre-verified).

## User flows

**Register:** 2-step register form → `signUpWith` succeeds → no session (confirmations on) → navigate to VerifyEmailScreen(email) → "We emailed a 6-digit code to {email}" → enter code → `verifyEmailOtp(SIGNUP)` signs in → `completeSignInAfterConfirmation()` → permission screen.

**Unverified login:** login rejected with "email not confirmed" → LoginScreen routes to VerifyEmailScreen(email) instead of a dead-end error → same code entry (with resend) → signed in.

**Resend:** button with the same 60 s cooldown as the reset screen, via `auth.resendEmail(OtpType.Email.SIGNUP, email)` (Android) / `auth.resend(email:type: .signup)` (iOS).

**Compatibility:** `register()` checks for a session after `signUpWith`. Session present (confirmations off) → old immediate-sign-in path. Absent → verification path. The app is correct on both sides of the config flip.

## Backend config (CLI-managed, `supabase config push`)

- `[auth.email] enable_confirmations = true`
- New `[auth.email.template.confirmation]`: subject `Your Family App verification code`, `content_path = "./supabase/templates/confirmation.html"` — same Glass House layout as `recovery.html` (indigo gradient badge, ambient canvas, white card, `{{ .Token }}` code chip), copy: "Confirm your email" / welcome tone.
- Push requires explicit user approval (config push is no-dry-run; every managed value stays mirrored in config.toml).

## Platform changes

| Unit | Android | iOS |
|---|---|---|
| Repository | `confirmSignupEmail(email, code): Result<String>` (verify OTP → `completeSignInAfterConfirmation`), `resendSignupCode(email): Result<Unit>` | same via protocol seam + MockRepository stubs |
| ViewModel | `VerifyEmailUiState` (email, loading, error, resendCooldownSeconds) + `startEmailVerification(email)`, `confirmSignupEmail(code)`, `resendSignupCode()`, `clearVerifyFlow()`; `register()` gains the session-presence check; `login()` failure detects "email not confirmed" and exposes it as a routing event, not a plain error | mirrored on `AuthViewModel` (`verifyEmail`, `verifyCooldown`, …) |
| Screen | `VerifyEmailScreen(onVerified is implicit via gate, onBackToLogin)` — AuthScaffold + code field + verify button + resend w/ cooldown, mirroring ResetPasswordScreen step 2 | `VerifyEmailScreen` in `AuthFlowView`, `navigationDestination` like reset |
| Navigation | `Routes.VERIFY_EMAIL`; register and login both navigate there | `@State showVerify` + shared `AuthViewModel` |
| Error mapping | new row: "email not confirmed" → friendly copy; reuse existing OTP-expired row | same table addition |
| Strings | EN + NB in `strings.xml` ×2 | EN + NB `Localizable.strings` ×2 |

## Error handling

- Wrong/expired code → existing `that_code_is_wrong_or_expired` mapping; resend available.
- "Email not confirmed" at login → routes to verification (never a dead end).
- Resend cooldown 60 s protects the 30/hour email rate limit.
- Abandoned verification + re-register with same email: Supabase re-sends the confirmation code; flow re-enters verification normally.

## Testing

- Android (`AuthViewModelTest`, mockk): register-without-session enters verification state and does NOT call `completeSignInAfterConfirmation`; register-with-session keeps old path; code length/short-circuit validation; wrong-code error mapping; unconfirmed-login detection routes instead of erroring; resend cooldown gating.
- iOS (`AuthViewModelTests`, MockRepository): mirrored cases; Mac compile deferred as usual.
- Device smoke test after the config flip: register a fresh account → code email arrives from `auth@thefamilyapp.app` → verify → permission screen appears; unverified login routes to verification.

## Non-goals

- No change to `PermissionsOnboardingScreen` or the auth gate.
- No verification for Google OAuth signups.
- No retroactive verification of existing confirmed accounts.

## Milestones

1. **M1 — Backend:** confirmation template + `enable_confirmations = true` via config push (user-approved). Flips immediately.
2. **M2 — Android:** repo methods, VM verify flow, VerifyEmailScreen, routing from register + login, tests.
3. **M3 — iOS parity:** mirrored implementation + tests.
4. **M4 — Docs:** vault milestone + runbook note; merge chain to `test`.

Note on M1-before-M2 ordering: the user explicitly accepted that email signups error on old app versions during the gap; M2 should follow immediately.
