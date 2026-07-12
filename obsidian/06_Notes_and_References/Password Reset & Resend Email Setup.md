# Password Reset & Resend Email Setup

The app-side reset flow (6-digit code) shipped in `feat/forgot-password-reset` (2026-07-12).
Delivery starts on Supabase's built-in sender and upgrades to Resend via SMTP —
no app changes needed at any point.

## 1. REQUIRED NOW — put the code in the reset email

Supabase Dashboard → Authentication → Emails → **Reset Password** template.
The default template only contains a link; the app flow needs the 6-digit code.

- Subject: `Your Family App reset code`
- Body (replace the default):

```html
<h2>Reset your password</h2>
<p>Enter this code in The Family App to choose a new password:</p>
<h1 style="letter-spacing: 4px;">{{ .Token }}</h1>
<p>The code expires in 1 hour. If you didn't ask for this, you can ignore this email.</p>
```

Note: OTP expiry is configurable under Authentication → Providers → Email
(default 3600 s — fine as is).

## 2. Domain — DONE ✅

**thefamilyapp.app** — bought 2026-07-12 through Vercel ($9.99 first year).
DNS is managed at Vercel (Dashboard → Domains → thefamilyapp.app, or `vercel dns`).

## 3. Verify the domain in Resend

Resend Dashboard → Domains → Add Domain → `thefamilyapp.app`.
Resend shows the DNS records to add (SPF TXT + DKIM CNAMEs, optionally the bounce MX).
Add them at Vercel: Dashboard → Domains → thefamilyapp.app → DNS Records, or
`vercel dns add thefamilyapp.app <name> <TYPE> <value>` — or paste them to Claude,
who can add them via the Vercel CLI. Wait for status **Verified** (minutes to a few hours).
Then Resend → API Keys → create a key with sending access. Copy it once.

## 4. Point Supabase at Resend

Supabase Dashboard → Project Settings → Authentication → SMTP (enable custom SMTP):

| Field | Value |
|---|---|
| Host | `smtp.resend.com` |
| Port | `465` |
| Username | `resend` |
| Password | *the Resend API key* |
| Sender email | `auth@thefamilyapp.app` |
| Sender name | `The Family App` |

## 5. Raise the email rate limit

Dashboard → Authentication → Rate Limits → increase "emails per hour"
(the built-in default is very low; with Resend's free tier the ceiling is
100 emails/day, 3 000/month — set e.g. 30/hour).

## 6. Smoke test

From the app: Login → Forgot password? → enter a real account email → code
arrives → set a new password → app signs in. Also confirm the old password
no longer works.
