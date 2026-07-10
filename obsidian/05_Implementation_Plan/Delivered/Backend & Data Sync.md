# Delivered — Backend & Data Sync

Supabase adoption, multi-device sync, realtime, auth, and the data-correctness bug fixes. Part of
[[../Implementation Plan]]. See also [[../../03_Architecture_and_Design/Backend Options and API Strategy]].

## Milestone 16 — backend modernization ✅
Backend chosen: **Supabase** (Postgres + Auth + Realtime + Storage) via `supabase-kt 3.1.4`.
Added the serialization plugin, supabase-kt BOM + `auth-kt`/`postgrest-kt`/`realtime-kt`/
`storage-kt`, `ktor` HTTP engine; `SUPABASE_URL`/`SUPABASE_ANON_KEY` injected via `BuildConfig`.
`SupabaseManager` singleton (`lazy { createSupabaseClient(...) }`, all four plugins). Auth moved to
Supabase Auth (local SHA-256 storage removed). Full `schema.sql` for all tables (UUID PKs,
`created_at`/`updated_at`/`deleted_at`, FK indices, RLS enabled). Delivered via `feat/backend-api`.

## Milestones 9 + 11 — multi-device sync via Supabase ✅
**Room removed entirely** (`feat/remove-room-supabase`). All feature ViewModels read/write Supabase
Postgrest directly, each with the correct family-scoped or user-scoped filter matching the RLS
policies:
- Shopping `owner_user_id = me OR family_id = myFamily`; Meals `family_id`; Calendar
  `user_id OR family_id`; Birthdays `made_by_user_id OR family_id`; Conversations
  `user_from OR family_id`; Wishlists `owner_user_id` (private by design).
SQL run order: `schema.sql` → `fix_users_rls_recursion.sql` → `add_auth_user_trigger.sql` →
`add_grants.sql` → `migrate_to_supabase_only.sql` → `add_storage_buckets.sql`.

## Milestone 19 — realtime live sync for core features ✅
`add_realtime_core.sql` sets `REPLICA IDENTITY FULL` and adds `shopping_lists`, `shopping_items`,
`calendar_events`, `birthdays`, `meal_plans` to the `supabase_realtime` publication. Shopping /
Calendar / Birthday / Meal ViewModels subscribe (channel per `family_id`/`list_id`), any postgres
change → full reload, `onCleared()` removes the channel — same pattern as Chat and Family Map.

## Registration auth flow ✅
FK constraint on `public.users` during PKCE + email-confirmation signup. Fix: DB trigger
`handle_new_auth_user()` (SECURITY DEFINER, `add_auth_user_trigger.sql`) inserts the `public.users`
row on every `auth.users` INSERT from `raw_user_meta_data`, `ON CONFLICT (auth_id) DO NOTHING`.
`register()` signs out first, passes profile fields in `signUpWith` metadata, drops the manual
insert. `completeSignInAfterConfirmation()` resolves `public.users.id` by `auth_id` after
confirmation. Multi-step registration UI (name/email/password → birthday/mobile → confirm).

## Storage buckets + avatar dual-ID fix ✅
`add_storage_buckets.sql` creates `avatars` (locked to `auth.uid()`) and `group-images` (any
authenticated). `ProfileViewModel.uploadAvatar` uses `auth.currentSessionOrNull()?.user?.id` (the
Auth UUID) as the folder — **not** `public.users.id` — because the storage RLS checks `auth.uid()`.
Cache-busting `?t=<millis>` appended after upload (Coil caches by URL). This is the canonical
**dual-ID** pitfall.

## Data-isolation correctness ✅
All 4 data ViewModels (Chat, Calendar, Shopping, Birthday) apply a two-layer filter: the Supabase
query plus a Kotlin post-decode `familyId == null || familyId == currentFamilyId` guard, so rows
from old families can't leak. Birthday keeps user-owned rows regardless of family so a user's
auto-synced birthday stays visible after leaving a family.

## Bug — family data didn't sync; nothing live ✅
Three stacked bugs (fixed across `fix/family-sync-refresh-on-resume` +
`fix/realtime-websocket-engine`):
1. **Realtime websocket never connected** — the app used `ktor-client-android`, which has no
   WebSocket support (`Engine doesn't support WebSocketCapability`). **No** realtime feature ever
   worked. Fix: switch to `ktor-client-okhttp`. *(This is now pitfall #1 in CLAUDE.md.)*
2. **`getUser()` cached null on failure** — one transient failure poisoned the session cache,
   making every feature see "no family" and create rows with `family_id = null`. Fix: cache only a
   successful, non-null fetch.
3. **M22 hoist removed refresh-on-reentry** — Activity-scoped VMs `init{}` once. Fix: reusable
   `RefreshOnResume` + `refresh()` on the feature VMs, wired into each list screen.
