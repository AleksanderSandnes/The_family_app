# Delivered — Feature Milestones

Feature delivery for map, notifications, calendar, birthdays, profile, family. Part of
[[../Implementation Plan]]. Chat has its own note ([[Chat]]); design/polish is in
[[Design & UI Polish]].

## Milestone 10 — Family Map (Familiekart) ✅
`add_user_locations.sql`: `user_locations` (PK `user_id`; `family_id`, `lat`, `lng`,
`display_name`, `updated_at`, `visible`), RLS (own row full access + family reads visible rows),
realtime enabled. Two-tier publishing: `FamilyMapViewModel` (`FusedLocationProviderClient`, 30 s
while the screen is open) + `LocationForegroundService` (`START_STICKY`, 5 min, persistent
notification, sets `visible=false` on `onDestroy`, exposes `isRunning`). Two-stage permission flow
(foreground on open → background offered inline). `GoogleMap` with custom circular avatar/initials
bitmap markers. The `locationVisible` DataStore toggle drives the `visible` column on every upsert.
*(Play-Store v1 later shipped foreground-only.)*

## Milestone 12 — notifications ✅
`WorkManager` `PeriodicWorkRequest` (`NotificationWorker`) runs daily; queries upcoming birthdays +
calendar events, filters by user lead time, posts on `CHANNEL_BIRTHDAYS` / `CHANNEL_CALENDAR` with
deterministic IDs. ISO-first date parsing with `d MMM` fallback; recurring birthday roll-forward.
`BootReceiver` re-enqueues after reboot. Settings toggle + `POST_NOTIFICATIONS` runtime request +
"Remind me" chip (same day / 1 / 2 / 7 days). FCM push deferred to backend work.

## Milestone 13 — calendar enhancements ✅
Phase 1: all-day toggle + start/end date + time range on events. Phase 2 (Apple-inspired): events
store ISO `yyyy-MM-dd` (proper year, chronological sort); full Monday-first month grid with today
ring, selected-day fill, animated month transitions, event dots; day-filtered list; Material3
DatePicker + TimePicker dialogs; `NotificationWorker` ISO-first parsing.

## Calendar event edit + icon picker ✅
Tap an event body → pre-filled edit dialog (unified `EventDialog` for add/edit). 4×4 grid of 16
Material icons; stored key → `ImageVector` with `Schedule` fallback. Scrollable dialog content.

## Milestone 15 — profile picture ✅
Avatar via camera or gallery. `FileProvider` for captures, Coil `AsyncImage` in `InitialAvatar`
(initials-gradient fallback), unique `avatar_<ts>.jpg` filenames to bust cache. Camera runtime
permission. Photos surface everywhere the avatar appears. *(Remote upload to Supabase Storage
landed in the backend work — see [[Backend & Data Sync]].)*

## Group chat settings ✅
Rename conversation, change/remove group image (camera/gallery), via a 3-dot menu in the
conversation top bar. Group photo shows in the chat list.

## Family share code + header edit ✅
Auto-generated 8-char join code on family create (`CreateFamilyDialog` with `CopyableCodeField`).
Tappable family header → `EditFamilyDialog`: admin can rename + copy code; non-admin sees a
grayed-out name + copy/close. `FamilyRepository.renameFamily`, `FamilyViewModel.generateJoinCode` /
`renameFamily`. New reusable `CopyableCodeField` + `FamilyTextField(enabled)` in `Components.kt`.

## Birthday editing + wishlist icons + permissions UX ✅
- **Birthday editing:** tap a card → `EditBirthdayDialog`; `BirthdayViewModel.update`;
  `FamilyRepository.updateUserBirthdayEntry` keeps the auto-synced birthday row in sync (insert/
  update/delete).
- **Wishlist icons + rename:** `WishlistModel.icon` (DB migration `wishlists.icon`), 12-icon picker
  in `NewWishlistDialog`, 3-dot rename/change-icon in detail, inline add-a-wish field.
- **Permissions onboarding:** per-card individual permission requests + a combined "Continue"
  launcher (single batched `launch()`).
