# Environments and configuration

Names and locations only — never commit values.

| Environment | Clients | Backend | Website |
| --- | --- | --- | --- |
| Local | debug builds with `local.properties` / `Secrets.xcconfig` | disposable `supabase start` stacks used by tests | `web/` opened locally |
| Production | Play / App Store builds from `master` | Supabase `bntcznvsbyshetndbxfa` | Vercel project `thefamilyapp-web`, domain `thefamilyapp.app` |

## Android — `android/local.properties` (ignored)

| Key | Purpose |
| --- | --- |
| `SUPABASE_URL`, `SUPABASE_ANON_KEY` | Supabase client (anon key is public by design) |
| `MAPS_API_KEY` | Google Maps key, restricted to `com.sandnes.familyapp` + signing SHA-1 |
| `release.keystore`, `store.password`, `key.alias`, `key.password` | Upload-key signing for release builds |

`android/google-services.json` (Firebase/FCM) and `android/release.keystore` are
ignored and backed up outside the repository.

## iOS — `ios/Config/Secrets.xcconfig` (ignored)

| Key | Purpose |
| --- | --- |
| `SUPABASE_URL`, `SUPABASE_ANON_KEY` | Same values as Android |

Signing, APNs key and App Store Connect API key are Apple-account items (see
`docs/RELEASE.md`).

## Supabase

| Name | Where | Purpose |
| --- | --- | --- |
| `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | injected into Edge Functions | Function clients |
| `FCM_SERVICE_ACCOUNT` | Edge Function secret | FCM push delivery (`push-on-message`, `daily-reminders`) |
| `family_push_webhook_key` | Vault secret | Bearer credential the chat-message trigger sends to `push-on-message` (`supabase/post_deploy/push_webhook.sql`) |

Auth settings (Site URL, redirect allow-list `familyapp://…`, SMTP,
leaked-password protection) are dashboard / Management API settings.

## GitHub Actions

| Name | Kind | Used by |
| --- | --- | --- |
| `GITHUB_TOKEN` | automatic | all workflows |
| `CLAUDE_CODE_OAUTH_TOKEN` | secret | `claude.yml`, `claude-code-review.yml` |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | secrets, `production` environment (to be added) | signed release bundle |
| `GOOGLE_SERVICES_JSON`, `MAPS_API_KEY`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` | secrets, `production` environment (to be added) | signed release bundle client configuration |
| `PLAY_SERVICE_ACCOUNT_JSON` | secret, `play-internal` environment (to be added) | Play internal-track upload |
| `APP_STORE_CONNECT_API_KEY_*` | secrets (after Apple enrolment) | TestFlight upload |

## Release automation configuration

| Name | Kind | Purpose |
| --- | --- | --- |
| `RELEASE_PREPARE_ENABLED` | repository variable | `true` enables task release PR creation; otherwise patch preview only |
| `RELEASE_DRAFT_ENABLED` | repository variable | `true` enables tags and draft GitHub Releases after production CI |
| `RELEASE_ANDROID_ENABLED` | repository variable | `true` allows the manual `android-release.yml` signed bundle build |
| `RELEASE_PLAY_ENABLED` | repository variable | `true` allows the opt-in Play internal draft upload |
| `RELEASE_BOT_TOKEN` | optional secret | scoped Contents/PR write token for release proposals with automatic CI; no bypass |

Configure the `production` environment and owner approval before activation.
No activation variables or credentials were added by preparing the workflows.
