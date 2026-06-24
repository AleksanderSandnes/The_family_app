# The Family App — Claude Instructions

## What this project is

An Android family coordination app. One shared space for shopping, meals, calendar, birthdays, wishlists, chat, and location sharing. Single-Activity, 100% Jetpack Compose + Material 3, backed by Supabase (Postgres + Realtime + Storage + Auth).

## Mandatory workflow rules

**Never build to verify.** Do not run `./gradlew assembleDebug`, `./gradlew build`, or any build/test commands. The user runs builds manually to save tokens. After writing code, report what changed and stop. If compilation correctness is uncertain, say so explicitly.

**Branch workflow — no exceptions:**
1. Branch from `master` with a descriptive name: `feat/`, `fix/`, `chore/` prefix (e.g. `feat/calendar-recurring-events`).
2. Merge the task branch into `test`.
3. Merge `test` into `master`.
4. Never commit directly to `master` or `test`.

**After plan-related implementation:** Update the Obsidian vault at `/home/aleksander/Obsidian/The_family_app/`. Change milestone status from ⏳ to ✅ in `05_Implementation_Plan/Implementation Plan.md`, update `00 Home.md` current status, and update any relevant architecture notes.

## Tech stack

| Layer | Library / Version |
|---|---|
| Language | Kotlin 2.3.0 |
| UI | Jetpack Compose + Material 3 (Compose BOM 2024.09.03) |
| Navigation | Navigation Compose 2.8.2 |
| Backend | Supabase 3.1.4 (auth-kt, postgrest-kt, realtime-kt, storage-kt) |
| HTTP engine | `ktor-client-okhttp` — **not** `ktor-client-android` (Android engine lacks WebSocket support; switching causes Realtime to silently fail) |
| Image loading | Coil 2.7.0 |
| Background work | WorkManager 2.9.0 |
| Session/prefs | Jetpack DataStore (Preferences) |
| Maps | maps-compose 8.3.0 + play-services-location 21.3.0 |
| Build | AGP 8.13.2 · Gradle 8.13 · JDK 17 · compileSdk 36 · minSdk 23 |

## Architecture

### Entry point

`MainActivity` (single Activity). Sets theme, handles auth deep-links (`familyapp://auth`), renders `TheFamilyAppTheme { FamilyApp() }`.

### Auth gate

`RootViewModel` observes `FamilyRepository.currentUserId` (a DataStore Flow). Emits `AuthGate.Loading → SignedOut → SignedIn`. `FamilyApp()` switches between `AuthFlow` (login/register nav graph) and `MainFlow` (main app).

### Data layer

**`SupabaseManager`** — singleton lazy-init Supabase client. All features use `SupabaseManager.client`.

**`SessionManager`** — wraps the DataStore `session` preferences file. Stores `current_user_id_v2`, `theme_mode`, `notifications_enabled`, `notify_days_before`, `location_visible`.

**`FamilyRepository`** — application-scoped singleton (`FamilyRepository.get(context)`). Central API for auth, family management, user profile, and settings. Holds an in-memory user cache (`cachedUser`); call `invalidateUserCache()` after any mutation that changes the user row.

### Critical dual-ID distinction

There are **two different UUIDs** for every user. Confusing them causes subtle, hard-to-debug bugs:

- **`public.users.id`** — the app's internal user ID. Stored in DataStore as `current_user_id_v2`. Used in all foreign keys across `shopping_lists`, `calendar_events`, `birthdays`, etc. Retrieved from `repo.currentUserId` (the DataStore flow) or `FamilyRepository.getUser(userId)`.
- **`auth.uid()` / `auth_id`** — the Supabase Auth UUID. Used in RLS policies and in Storage paths. Retrieved at runtime via `SupabaseManager.client.auth.currentSessionOrNull()?.user?.id`.

**Storage paths MUST use the auth UUID.** `ProfileViewModel.uploadAvatar` stores avatars at `avatars/{auth_uid}/...` because the RLS policy checks `(storage.foldername(name))[1] = auth.uid()::text`. Using `public.users.id` here causes a policy denial with no error surfaced (wrapped in `runCatching`).

### UI layer

```
app/src/main/java/com/example/mainactivity/
  data/
    Entities.kt          — all @Serializable data classes (UserModel, FamilyModel, …)
    FamilyRepository.kt  — singleton; auth, family, profile operations
    SessionManager.kt    — DataStore wrapper for session/prefs
    remote/
      SupabaseManager.kt — lazy Supabase client init

  ui/
    theme/               — TheFamilyAppTheme, LightColors/DarkColors (indigo/violet palette)
    components/
      Components.kt      — PremiumButton, AppTextField, AvatarCircle, SwipeToDelete,
                           InitialAvatar, LifecycleResumeEffect, DatePickerField
      Scaffolding.kt     — FeatureTopBar, InputDialog
    navigation/
      Routes.kt          — all route string constants + builder fns
      RootViewModel.kt   — auth gate state
      AppNavHost.kt      — FamilyApp → AuthFlow / MainFlow; bottom nav; NavHost graph
    auth/    home/    shopping/    meal/    calendar/    birthday/
    wishlist/    chat/    family/    profile/    settings/    map/
    workers/
      NotificationWorker.kt  — WorkManager worker for birthday/calendar notifications
    receivers/
      BootReceiver.kt        — reschedules WorkManager jobs after reboot
```

### Navigation

`Routes` object holds all route strings. Bottom nav: Home, Calendar, Chat, Family, Profile — use **crossfade** transitions. Detail/feature screens (shopping detail, meal detail, etc.) use **horizontal slide** (iOS-style, defined as NavHost default in `AppNavHost`).

Feature ViewModels are hoisted to `MainFlow` scope (Activity-scoped) so they survive tab switches. Chat list and chat detail share the same `ChatViewModel` instance — this is intentional so deletes in the detail screen reflect in the list on pop-back.

### Realtime pattern

1. `SupabaseManager.client.channel("tablename-$familyId")` — channel name must be unique per subscription.
2. Collect `channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "..."; filter(...) }`.
3. On any event, call the full reload function (not partial merge).
4. `onCleared()`: `SupabaseManager.client.realtime.removeChannel(channel)`.

Tables with Realtime enabled: `messages`, `conversations`, `wishlists`, `wishes`, `shopping_lists`, `shopping_items`, `calendar_events`, `birthdays`, `meal_plans`, `user_locations`.

### Optimistic UI

Write operations: update local `StateFlow` immediately (temp ID) → fire Supabase call → reload from server. Deletes remove before the network call. No rollback logic.

### Location sharing

Two-tier:
- `FamilyMapViewModel` — publishes every 30 s while map screen is open.
- `LocationForegroundService` — `START_STICKY` foreground service; publishes every 5 min; sets `visible = false` on `onDestroy`; exposes `isRunning` companion flag.
- `FamilyMapScreen.DisposableEffect` only clears visibility when the service is NOT running.
- `locationVisible` DataStore toggle drives the `visible` column on every upsert.

## Supabase database

**Schema baseline:** `supabase/schema.sql` — run once to create all tables, indexes, RLS.

**Incremental migrations** live in `supabase/` as individual `.sql` files. Each is safe to re-run (`add column if not exists`, `drop policy if exists` + recreate). New changes go in a new file, not in `schema.sql`.

**Key tables and their scoping column:**

| Table | Scoped by |
|---|---|
| `users` | `auth_id` (FK → `auth.users`), `family_id` |
| `families` | `admin_id` → `users.id` |
| `shopping_lists` | `owner_user_id`, `family_id` |
| `shopping_items` | `list_id` (cascades from list) |
| `meal_plans` | `family_id` |
| `meal_plan_days` | `meal_plan_id` |
| `calendar_events` | `user_id`, `family_id` |
| `birthdays` | `family_id`, `user_id`, `made_by_user_id` |
| `wishlists` | `owner_user_id`, `family_id` |
| `wishes` | `wishlist_id`, `user_id` |
| `conversations` | `user_from`, `family_id` |
| `conversation_participants` | `conversation_id`, `user_id` |
| `messages` | `conversation_id`, `user_from`, `reply_to_id` |
| `user_locations` | PK: `user_id`; `family_id`, `lat`, `lng`, `visible` |

**RLS pattern:** All policies resolve the calling user's app ID via:
```sql
(select id from public.users where auth_id = auth.uid() limit 1)
```
The `limit 1` is required — without it, a multi-row subquery can break the policy check.

**User creation:** `auth.signUpWith(Email)` triggers `on_auth_user_created` (see `add_auth_user_trigger.sql`) which creates the `public.users` row automatically from auth metadata. Never insert into `public.users` manually after signup.

**Email confirmation flow:** After confirmation, call `completeSignInAfterConfirmation()` — this resolves the `public.users.id` from `auth_id` and persists it to DataStore.

**Storage buckets:** `avatars` and `group-images`. Created by `supabase/add_storage_buckets.sql`. Avatars path: `avatars/{auth_uid}/{filename}` — must use auth UUID, not `public.users.id`.

## Secrets — `local.properties` (gitignored)

```
SUPABASE_URL=...
SUPABASE_ANON_KEY=...
MAPS_API_KEY=...
```

Injected into `BuildConfig` at compile time. Never commit this file.

## Theme

`TheFamilyAppTheme` in `ui/theme/`. Palette: indigo/violet primary, pink tertiary, full light + dark schemes. Color tokens in `Color.kt` (`Indigo600`, `Canvas`, `InkSurface`, etc.).

Always use `MaterialTheme.colorScheme.*` tokens — never hardcode colors.

## Reusable components (use these, don't reinvent)

- `FeatureTopBar(title, onBack?, actions)` — standard top bar for every detail/feature screen.
- `InputDialog(title, label, …)` — single or dual-field dialog for quick add/edit.
- `PremiumButton` / `AppTextField` — themed button and text field.
- `AvatarCircle` / `InitialAvatar` — avatar with image URL or fallback initials + color.
- `SwipeToDelete` — swipe-to-reveal delete; used in list screens.
- `LifecycleResumeEffect` — calls a lambda on `ON_RESUME`; used for refresh-on-screen-return.
- `DatePickerField` — wraps Material 3 `DatePicker` in a click-to-open field.

## Common pitfalls

1. **Wrong Ktor engine.** `ktor-client-android` has no WebSocket support. Supabase Realtime silently fails with "Engine doesn't support WebSocketCapability". Always use `ktor-client-okhttp`.

2. **Caching null users.** `FamilyRepository.getUser` only caches non-null results. A transient RLS/network failure must not poison the cache — a cached null `familyId` causes rows to be created unscoped and invisible to all family members.

3. **ViewModel scope.** Feature ViewModels that need to survive tab switches must be hoisted in `MainFlow` in `AppNavHost.kt`. Do not create them inside individual screen composables if they hold inter-screen state.

4. **`familyChanged` SharedFlow.** Emitted by `FamilyRepository` after `createFamily`, `joinFamily`, and `leaveFamily`. ViewModels that scope queries to `familyId` (meals, calendar, shopping, etc.) should observe this or refresh on `ON_RESUME` to pick up family membership changes.

5. **Realtime channel uniqueness.** Use `"tablename-$familyId"` naming. Duplicate channel names cause subscription collisions.

6. **`auth_id` vs `id` in Storage.** Every place that constructs a Storage path for avatars must use `auth.currentSessionOrNull()?.user?.id`, not the DataStore user ID. These are different UUIDs and the storage RLS will silently reject the wrong one.
