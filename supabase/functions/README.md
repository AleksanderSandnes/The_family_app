# Push notification Edge Functions

Server side of the app's push notifications (events, birthdays, messages). All pushes are
**data-only** FCM messages; the Android client (`FamilyMessagingService`) renders them, so
they arrive even when the app is killed.

| Function | Trigger | Purpose |
|---|---|---|
| `push-on-message` | Database Webhook on `messages` INSERT | Instant chat notification to other participants |
| `daily-reminders` | `pg_cron` schedule (each morning) | Birthday & calendar-event reminders honouring each user's `notify_days_before` |
| `_shared/` | — | `client.ts` (service-role client), `fcm.ts` (FCM HTTP v1 sender) — not deployable functions |

## 1. Firebase setup (one-time)

1. <https://console.firebase.google.com> → **Add project** (free Spark plan is fine for FCM).
2. **Add Android app**, package name **`com.example.mainactivity`** (the app's `applicationId`).
3. Download **`google-services.json`** → place at `app/google-services.json` (gitignored).
   The Gradle plugin only activates once this file is present.
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

## 4. Wire the message webhook

Supabase Dashboard → **Database → Webhooks → Create a new hook**:

- Table: `public.messages`, Events: **Insert**
- Type: **Supabase Edge Functions** → `push-on-message`
- Add header `Authorization: Bearer <SERVICE_ROLE_KEY>` (the function reads `record` from the
  webhook payload).

## 5. Schedule the daily reminders

Supabase Dashboard → **Integrations → Cron** (pg_cron), new job invoking `daily-reminders`,
e.g. `0 8 * * *` (08:00 UTC).

> **Note — fixed UTC hour:** unlike the retired per-device local 09:00 reminder, the cron
> fires at a single UTC hour for everyone. Pick the hour that best fits your users' timezone.
> Users without a family are not sent reminders (birthdays/events are family-scoped).

## Data payload contract (function → `FamilyMessagingService`)

All values are strings (FCM data requirement). `type` selects the client render path:

- `message`: `conversationId, conversationName, imageUri, messageId, messageType, text, senderId, senderName`
- `birthday`: `name, daysUntil`
- `event`: `activity, daysUntil`
