# Password Reset & Resend Email Setup — ALL DONE ✅ (2026-07-12)

The app-side reset flow (6-digit code) shipped in `feat/forgot-password-reset`.
End-to-end verified: `/auth/v1/recover` → Resend SMTP → branded code email
delivered to a real inbox.

**Root cause of the original delivery failures:** Supabase SMTP had been pointed
at Resend with sender `onboarding@resend.dev` (no verified domain) — Resend only
delivers that to the account owner's own address, so family members never got
auth emails.

**How it's managed now:** `supabase/config.toml` + `supabase config push` (CLI,
linked to `bntcznvsbyshetndbxfa`). ⚠️ config push has NO dry-run and auto-applies
in non-interactive shells; every dashboard-managed [auth] value must stay mirrored
in config.toml (see warnings in that file). The SMTP key is read from `.envrc`
(gitignored) as `env(RESEND_SMTP_KEY)`.

## 1. Reset email template — DONE ✅

Managed in `supabase/templates/recovery.html` — Glass House-branded (indigo
gradient badge, ambient canvas, white card, 6-digit `{{ .Token }}` code chip).
Subject: `Your Family App reset code`. OTP expiry: default 3600 s.

## 2. Domain — DONE ✅

**thefamilyapp.app** — bought 2026-07-12 through Vercel ($9.99 first year).
DNS is managed at Vercel (Dashboard → Domains → thefamilyapp.app, or `vercel dns`).

## 3. Verify the domain in Resend — DONE ✅ (2026-07-12)

Done via Resend MCP + Vercel CLI: domain `thefamilyapp.app` created in **eu-west-1**
(sending enabled), DKIM TXT + SPF MX/TXT records added to Vercel DNS, status **verified**.
Test email from `auth@thefamilyapp.app` delivered successfully.
SMTP API key `supabase-smtp-familyapp` created (sending-only, domain-restricted) —
value held by Aleksander; it is the SMTP password for step 4.

## 4. Point Supabase at Resend — DONE ✅

Applied via `supabase config push`: `smtp.resend.com:465`, user `resend`,
password = `supabase-smtp-familyapp` key (from `.envrc`), sender
`auth@thefamilyapp.app` / "The Family App". Replaced the old
`onboarding@resend.dev` sender.

## 5. Rate limit — DONE ✅

`[auth.rate_limit] email_sent = 30` in config.toml (Resend free tier ceiling:
100/day, 3 000/month).

## 6. Smoke test — server side DONE ✅, device pending

`/auth/v1/recover` returned 200; Resend shows the reset email **delivered**
(2026-07-12 21:10 UTC). Remaining: on-device test — Login → Forgot password? →
code arrives → new password signs in, old password rejected.

## Next planned

Email verification at signup (6-digit code step before the permission screen) —
requires app-side flow first; flip `enable_confirmations = true` in config.toml
only together with that app release.
