# Forgot-Password Reset — Design

**Date:** 2026-07-12
**Status:** Approved
**Branch:** `feat/forgot-password-reset`

## Problem

A user of the app forgot their password and has no way to recover their account. The app needs a self-service password reset delivered by email.

## Decision summary

- **Reset mechanism:** 6-digit OTP code typed into the app (not an email deep link). Works regardless of which device the email is opened on; no deep-link fragility.
- **Email delivery:** Supabase Auth sends the email. Short term it goes through Supabase's built-in sender (same channel as today's signup confirmations). The user will buy a domain, verify it in Resend, and configure Resend as Supabase custom SMTP — a dashboard-only change that requires no app changes.
- **Scope:** Android first, iOS parity second (both approved).

## User flow

1. Login screen shows a **"Forgot password?"** text button below the password field.
2. It opens a **Reset Password** screen with two steps on one screen:
   - **Step 1 — email:** user enters their email → app calls `auth.resetPasswordForEmail(email)` → Supabase emails a 6-digit code. UI always advances with the message "If an account exists, we've sent a code" (never reveals whether the email is registered).
   - **Step 2 — code + new password:** user enters the code and a new password (with the existing strength meter) → app calls `auth.verifyEmailOtp(OtpType.Email.RECOVERY, email, code)` (signs the user in) → `auth.updateUser { password = newPassword }` → existing `completeSignInAfterConfirmation()` resolves the `public.users.id` and lands the user signed in.
3. **Resend code** button in step 2 with a 60-second cooldown.

## Android changes

| Unit | Change |
|---|---|
| `FamilyRepository` | `sendPasswordResetEmail(email)` and `confirmPasswordReset(email, code, newPassword)` wrappers (auth calls go through the repo, matching login/register). |
| `AuthViewModel` | Reset-flow state (step, loading, error, resend cooldown) + the two actions. Reuse `friendlyAuthError`, `isValidEmail`, `passwordStrength`. |
| `AuthScreens.kt` | "Forgot password?" button on Login; new `ResetPasswordScreen` composable using `AppTextField` / `PremiumButton`. |
| `Routes` + AuthFlow nav graph | New `Routes.ResetPassword` destination. |
| Tests | Unit tests for validation and error mapping, matching existing auth test patterns. |

## iOS changes (parity milestone)

Mirror the Android flow in `Features/Auth/` (`AuthFlowView`, `AuthViewModel`) using supabase-swift: `resetPasswordForEmail`, `verifyOTP(email:token:type: .recovery)`, `update(user:)`. Same UX (two-step single screen, cooldown, neutral messaging). XCTest coverage in `FamilyAppTests`. Compiles on the Mac later, per the iOS workflow.

## Error handling

- Wrong/expired code → friendly message ("That code is wrong or has expired — request a new one").
- Network failures surface through the existing `friendlyAuthError` mapping.
- Email-exists disclosure avoided: step 1 always reports success.
- Resend cooldown (60 s) protects the email rate limit.

## Supabase / Resend configuration (dashboard runbook — user action)

App code is independent of all of this; it only changes who delivers the email.

1. Edit the **Reset Password** email template (Dashboard → Auth → Emails) to include the code: `{{ .Token }}`. *(Required before the feature works well; works with the built-in sender.)*
2. Buy a domain (~$10/yr), add it to Resend, add the DNS records Resend provides (SPF/DKIM), wait for verification.
3. Dashboard → Auth → SMTP: host `smtp.resend.com`, port `465`, username `resend`, password = Resend API key, sender `auth@<domain>`, sender name "The Family App".
4. After SMTP is live, raise the auth email rate limit (Dashboard → Auth → Rate limits) from the built-in default.

## Non-goals

- No deep-link recovery path.
- No password-reset from inside the app for signed-in users (profile already covers signed-in changes, or can later).
- No custom email-sending edge function / auth hook.

## Milestones

1. **M1 — Android reset flow** (repo + ViewModel + screen + tests).
2. **M2 — iOS parity** (Swift sources + tests; Mac compile deferred).
3. **M3 — Delivery upgrade** (user runs the dashboard runbook; template edit can happen immediately).
