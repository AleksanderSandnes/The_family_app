# Architecture and Design

> **Two platforms, one backend.** The Family App is a native Android app (`android/`, Compose)
> **and** a native iOS app (`ios/`, SwiftUI) sharing a single Supabase project. The Android
> architecture is documented first; the iOS architecture and the shared Liquid Glass design
> system follow. The **dual-ID rule holds on both platforms** (see "Dual-ID rule" below).

## Android architecture (as of 2026-06-23)
The app uses:
- A single Android application module
- Kotlin + Jetpack Compose + Material 3 with single-Activity navigation
- **Supabase** as the sole data store — no local SQLite or Room
- AndroidX DataStore for lightweight session state (current user UUID)

## Tech stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Navigation**: Navigation Compose (NavHost)
- **Backend**: Supabase (supabase-kt 3.1.4)
  - `auth-kt` — email/password auth
  - `postgrest-kt` — all CRUD operations
  - `realtime-kt` — live chat message subscriptions
  - `storage-kt` — avatar images (`avatars` bucket), group chat images (`group-images` bucket)
- **Workers**: WorkManager (`NotificationWorker` — daily birthday + calendar reminders)
- **Maps**: Google Maps Compose SDK 8.3.0 (`GoogleMap` composable, `FusedLocationProviderClient`)
- **Location service**: `LocationForegroundService` — foreground service publishing location to Supabase every 5 min while running

## Data layer
- **No Room** — removed entirely; no `@Entity`, `@Dao`, `@Database`, no KSP plugin
- All models are `@Serializable` data classes with `@SerialName` for snake_case ↔ camelCase
- All IDs are UUID `String` (was `Long`)
- `SessionManager` stores `public.users.id` (app-layer UUID) in DataStore
- `FamilyRepository` wraps auth, profile CRUD, family membership
- ViewModels call `SupabaseManager.client.postgrest` directly for feature-level queries
- Reactive state: `MutableStateFlow` + explicit `load()` suspend functions (no Room Flows)

## Image storage
- Avatars: uploaded to Supabase Storage bucket `avatars` at path `{userId}/avatar.jpg`
- Group chat images: uploaded to bucket `group-images` at path `{conversationId}/image.jpg`
- No local file storage — all images are remote URLs

## Realtime
- Chat `ConversationScreen` subscribes to `messages` table insert events filtered by `conversation_id`
- Family Map `FamilyMapViewModel` subscribes to `user_locations` change events filtered by `family_id`
- Both use `channel.postgresChangeFlow<PostgresAction>` + `channel.subscribe()`
- Channels are cleaned up in `ViewModel.onCleared()`

## Location sharing (M10)
- **Foreground** (`FamilyMapViewModel`): `FusedLocationProviderClient` at 30-second interval while map screen is open. Updates `_myLocation` state for camera centering and upserts to `user_locations`.
- **Background** (`LocationForegroundService`): foreground service with persistent notification, 5-minute interval. Starts when user grants `ACCESS_BACKGROUND_LOCATION`. Survives the user leaving the map screen. Sets `visible = false` in Supabase on `onDestroy`.
- **Visibility logic**: `user_locations.visible` column is set to the value of the `locationVisible` DataStore key on every upsert — the "Visible on family map" toggle in Settings drives both services.
- **Permission flow**: foreground location first → background location offered inline on the map screen after foreground is granted. On Android 11+, background permission request opens system settings ("Allow all the time").

## Supabase schema
- 13 tables: `users`, `families`, `shopping_lists`, `shopping_items`, `meal_plans`, `meal_plan_days`, `calendar_events`, `birthdays`, `wishlists`, `wishes`, `conversations`, `messages`, `user_locations`
- RLS enabled on all tables — policies reference `(select id from public.users where auth_id = auth.uid() limit 1)`
- Realtime enabled on `messages`, `conversations`, and `user_locations` tables

## Navigation
- String-based nav args (UUID), `NavType.StringType` everywhere
- Detail screens trigger `viewModel.loadXxxDetail(id)` via `LaunchedEffect(id)`

## Design inspiration
- Polished, premium look; Material Design principles; family-centered workflow

## Delivery and governance notes
- Branch workflow: `task → test → master` (never commit directly to master or test)
- Build command: `./gradlew assembleDebug`
- All features verified compiling after each major migration step

## iOS architecture (`ios/`)
Native SwiftUI port with 1:1 feature/design parity, same Supabase backend. Toolchain: SwiftUI
(iOS 26), XcodeGen (`ios/project.yml`, no checked-in `.xcodeproj`), supabase-swift, Nuke images.

- **State:** every ViewModel is `@Observable @MainActor` (the StateFlow → `@Observable` translation).
- **Repository:** `FamilyRepository.shared` singleton, split into per-domain extensions behind a
  `FamilyRepositoryProtocol` (a test seam — mock the protocol in XCTest). Holds `cachedUser` +
  `invalidateUserCache()`, a `familyChanged` AsyncStream, notification-prefs server mirror, and a
  platform-aware push-token upsert (`platform='ios'`).
- **Session:** `SessionStore` (UserDefaults) — **identical keys to Android DataStore**
  (`current_user_id_v2`, `theme_mode`, `notifications_enabled`, `notify_days_before`,
  `location_visible`).
- **Client:** `SupabaseClientProvider.client` (PKCE, redirect `familyapp://auth`, heartbeat 25 s).
- **Realtime:** `RealtimeObserver` — same channel names as Android (`"table-familyId"`), any event
  → full reload, teardown on disappear.
- **Optimistic UI:** identical to Android (temp `UUID()` → call → reload, no rollback).
- **Storage:** `StorageService` enforces `avatars/{auth_uid}/…` internally — storage paths are
  never built at call sites (dual-ID rule).
- **Localization:** in-app `Localization` drives the root `\.locale`; the app can switch
  language (EN/NB) independent of device locale.
- **Maps:** MapKit (no API key). **Push:** FCM iOS + APNs, `UNUserNotificationCenter` categories
  with inline reply. **Reminders:** none client-side — server-side `daily-reminders`.

## Liquid Glass design system (iOS 26)
The iOS app defines the shared visual language the Android parity track mirrors.
- Built on iOS 26 native `.glassEffect`: translucent glass surfaces, an ambient radial-wash
  background, per-feature accent colours, accent glow. Gradients are reserved to **identity
  surfaces only** (hero headers, outgoing chat bubbles, primary CTA, family banner).
- **Colours:** brand indigo/violet — indigo500 `#6366F1`, violet600 `#7C3AED`; core accent
  `#4F55E6` / dark `#A5ABFF`; ambient bases `#EFF1F8` (light) / `#0B0D16` (dark); full
  ink/secondary/caption ramps.
- **Type:** SF Pro scale. **Spacing:** 4-pt grid. **Radii:** card 20 / field 16 / button 18 /
  sheet 28 / tabBar 33.
- Tokens live in `ios/…/DesignSystem/` — identical hex values / type sizes / radii / spacing to
  the Android `ui/theme/` tokens.

## Tab-bar information architecture (both platforms)
iOS introduced a new IA that Android is adopting in the parity track: **Home / Shopping / Chat /
Calendar / Profile**. Shopping + Calendar replaced the old Calendar + Family tabs; Family, Map,
Wishlists, Meals, Birthdays and Settings are now **pushed routes** reached from Home / Profile.

## Planned Android glass layer (parity track)
Android keeps Compose + Material 3, **upgraded to Material 3 Expressive**, and adds a custom glass
design layer via **Haze** (`dev.chrisbanes.haze`) that mirrors iOS `Glass.swift` — RenderEffect
blur on API 31+ with a graceful fallback below. No rewrite: existing ViewModels/screens are
reused. Android idioms are kept where they differ from iOS (native nav + predictive back,
Material pickers, Google Maps not MapKit, WorkManager reminders, Android share sheet, Coil not
Nuke). See [[../05_Implementation_Plan/Android Parity Track]].

## Dual-ID rule (both platforms)
There are two UUIDs per user and confusing them causes silent, hard-to-debug bugs:
- **`public.users.id`** — the app's internal user ID; all foreign keys. Android:
  `current_user_id_v2` in DataStore. iOS: `SessionStore.currentUserId`.
- **`auth.uid()` / `auth_id`** — the Supabase Auth UUID; used in RLS policies and Storage paths.
  Android: `client.auth.currentSessionOrNull()?.user?.id`. iOS:
  `client.auth.currentSession?.user.id`.

Storage paths (avatars) **must** use the auth UUID — the RLS policy checks
`(storage.foldername(name))[1] = auth.uid()::text`. Enforced inside `StorageService` on iOS.

## Related notes
- [[Backend Options and API Strategy]]
- [[../05_Implementation_Plan/Implementation Plan]]
- [[../05_Implementation_Plan/Android Parity Track]]

## Authenticated media reads (release work, 2026-09-30)

Database media URLs remain stable object locators. Android `FamilyMedia`/Coil and
iOS `FamilyMedia`/Nuke resolve the four project storage buckets to fresh five-minute
signed URLs at read time. Never persist signed URLs or fall back to the public
locator on authorization failure. Both resolvers validate the origin, bucket,
object path, signed route and token, preserving numeric upload cache nonces.

Protected image requests bypass memory and disk caches, including old public-URL
entries. Identity checks discard signing/download results after account changes;
iOS protected requests also bypass Nuke task coalescing and URLCache. External
public assets retain ordinary image caching. Voice notes, wishlist PDFs and gallery
saves share the resolver. Existing disk entries are bypassed, not erased.

The production buckets remain public. Restrictive SELECT policies, coordinated
private-bucket rollout and signed release-device validation are still required.

Staged `storage_reads.sql` adds restrictive anonymous/authenticated SELECT guards.
The private helper resolves ownership/membership explicitly, including cross-family
conversation avatars and exact images from shared wishlists; it does not trust
editable wish user IDs or JWT metadata. See `PRIVATE_MEDIA_ROLLOUT.md` for client
compatibility, URL constraints and the separate visibility rollout.

Real local Auth/Storage HTTP tests verify uploads/upserts, signed/authorized
downloads, anonymous/cross-family/public denial, shares/revocation and link expiry.
`chat_media_owner_update` supplies the missing permissive UPDATE authorization
for upsert while restrictive guards still protect both old and destination paths.
The HTTP smoke script refuses non-local or non-validation projects.

## iOS restored-session navigation

`RootViewModel` uses the injected repository's SessionStore rather than a second
global store. A persisted app ID is only a hint: bootstrap requires `auth.session`
and a profile lookup bound to its auth UUID and credential snapshot. A changed
credential/account or cancelled lookup cannot restore the old profile.

The auth event adapter ignores non-nil INITIAL_SESSION (possibly expired), handles
confirmed sign-in/refresh/recovery/user updates, and closes navigation on sign-out.
The root consumes events without waiting on profile network work, cancels stale
lookups and checks a generation plus current auth ID before writing app identity.
A previously verified same-account refresh keeps offline eligibility without
re-fetching the profile. Failed initial restoration never borrows cached eligibility.

MainTabView is keyed by app-user ID to recreate hoisted feature state/navigation
when accounts change. Push/preferences sync runs only for a verified signed-in
profile and is reset for a new sign-in. Native tests and release auth-flow smoke
are the verification routes; Linux cannot run the Swift build locally.
