# Push notification Edge Functions

Server side of the app's push notifications (events, birthdays, messages). Payloads are
shaped per platform using the `device_push_tokens.platform` column
(`supabase/add_device_push_token_platform.sql`):

- **Android** — **data-only** FCM messages; the Android client (`FamilyMessagingService`)
  renders them, so they arrive even when the app is killed.
- **iOS** — `notification` block + `apns` section (`apns-push-type: alert`, priority 10,
  `mutable-content: 1`, `thread-id` for grouping). Data-only pushes are background-throttled
  on iOS, so APNs must display the alert; the data payload is still attached for deep-linking.

| Function | Trigger | Purpose |
|---|---|---|
| `push-on-message` | Database Webhook on `messages` INSERT | Instant chat notification to other participants |
| `daily-reminders` | `pg_cron` schedule (each morning) | Birthday & calendar-event reminders honouring each user's `notify_days_before` |
| `_shared/` | — | `client.ts` (service-role client), `fcm.ts` (FCM HTTP v1 sender) — not deployable functions |

## 1. Firebase setup (one-time)

1. <https://console.firebase.google.com> → **Add project** (free Spark plan is fine for FCM).
2. **Add Android app**, package name **`com.sandnes.familyapp`** (the app's `applicationId`).
3. Download **`google-services.json`** → place at `android/app/google-services.json` (gitignored).
   The Gradle plugin only activates once this file is present.
   For iOS: **Add iOS app** with bundle id `com.sandnes.familyapp`, upload the APNs `.p8` key
   (Cloud Messaging → Apple app configuration), download `GoogleService-Info.plist` →
   `ios/FamilyApp/Resources/` (gitignored).
4. **Project settings → Cloud Messaging** → ensure **Firebase Cloud Messaging API (V1)** is enabled.
5. **Project settings → Service accounts → Generate new private key** → save the JSON; it is
   the server credential below. Keep it secret — never commit it.

## 2. Apply the database migration

Run `supabase/add_push_notifications.sql` in the Supabase SQL Editor (creates
`device_push_tokens` and the `users.notifications_enabled` / `notify_days_before` columns).

## 3. Set secrets & deploy

```bash
# from the repo root
supabase secrets set FCM_SERVICE_ACCOUNT="$(cat /path/to/service-account.json)"
supabase functions deploy push-on-message
supabase functions deploy daily-reminders
```

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are injected automatically — do not set them.
The Firebase project id is read from `FCM_SERVICE_ACCOUNT`, so no separate project-id secret
is needed.

## 4. Wire the message webhook through Vault

The reviewed replacement is `supabase/post_deploy/push_webhook.sql`. It reads the
`family_push_webhook_key` secret from Supabase Vault when a message is inserted;
the trigger definition contains no credential. Provision that secret securely in
the dashboard with the server credential accepted by the deployed function, then
apply the replacement after reviewing the deployment. Never paste a credential
into a trigger definition, SQL file, command history or GitHub log.

Both functions require POST and an exact server credential matching their injected
`SUPABASE_SERVICE_ROLE_KEY`, in addition to gateway JWT verification. User and anon
tokens are rejected. Keep JWT verification enabled when deploying. The message
function accepts only JSON `public.messages` INSERT webhook envelopes with valid
UUIDs and supported message types. It limits request bodies to 64 KiB and text
previews to 200 Unicode characters.

For rollout, deploy and validate the function before replacing the existing
trigger; confirm the Vault value matches its server credential. Coordinate any
production key rotation with both jobs and Vault, after user approval. The new
SQL and function code are tested locally and in CI; they are not yet deployed.

## 5. Schedule the daily reminders

Supabase Dashboard → **Integrations → Cron** (pg_cron), new job invoking `daily-reminders`,
e.g. `0 8 * * *` (08:00 UTC).

The scheduled POST must resolve its Authorization credential from Vault, using
the same server-only authorization contract. Inspect the existing job before
replacing it. Do not embed credentials in the stored cron command. The current
cron configuration and its Vault migration remain to be verified.

> **Note — fixed UTC hour:** unlike the retired per-device local 09:00 reminder, the cron
> fires at a single UTC hour for everyone. Pick the hour that best fits your users' timezone.
> Users without a family are not sent reminders (birthdays/events are family-scoped).

## Data payload contract (function → `FamilyMessagingService`)

All values are strings (FCM data requirement). `type` selects the client render path:

- `message`: `conversationId, conversationName, imageUri, messageId, messageType, text, senderId, senderName`
- `birthday`: `name, daysUntil`
- `event`: `activity, daysUntil`

iOS receives the same data payload plus the visible alert (title/body) composed server-side.

Live metadata checked on 2026-09-30: both functions are active with JWT verification
enabled. Their deployed code has not been compared with these local changes.
Production push delivery on Android and iOS still needs verification after rollout.
