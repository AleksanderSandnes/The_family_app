# Feature Inventory

Both apps run on one shared Supabase backend. **iOS** currently leads (the iOS build-out added
features + a redesign on top of the initial 1:1 port); the **Android ⇄ iOS parity track**
([[../05_Implementation_Plan/Android Parity Track]]) is bringing Android level.

Legend: ✅ shipped · 🚧 parity track (bringing to Android) · — n/a

## Core features (shipped on both platforms)
| Feature | Android | iOS |
|---|---|---|
| Email/password auth + email confirmation, Google OAuth | ✅ | ✅ |
| Family create / join (invite code, QR) | ✅ | ✅ |
| Shopping lists + items (realtime, optimistic) | ✅ | ✅ |
| Meal planning | ✅ | ✅ |
| Calendar (month grid, all-day, time ranges, icons) | ✅ | ✅ |
| Birthdays (next-occurrence sort, age chip, reminders) | ✅ | ✅ |
| Wishlists + secret gift reservations | ✅ | ✅ |
| Family chat — text/image/voice, reactions, replies, receipts, typing, groups | ✅ | ✅ |
| Profile + avatar upload, settings, permissions onboarding | ✅ | ✅ |
| Family map + location sharing (foreground) | ✅ | ✅ |
| Realtime live sync across all feature tables | ✅ | ✅ |
| Push notifications | ✅ (data-only) | ✅ (APNs alert) |

## iOS-era features (iOS ✅ today; Android via parity track 🚧)
| Feature | Android | iOS | Notes |
|---|---|---|---|
| In-app language switch + full EN/NB localization (~334 keys), independent of device locale | 🚧 | ✅ | parity M4 |
| Directional family relations + member profile popup (each viewer sets their own label) | 🚧 | ✅ | table `family_relations` |
| Wishlist shareable link (unguessable; one external user gains cross-family read + reserve) | 🚧 | ✅ | `wishlist_shares`, `wishlists.share_token`, RPCs `ensure_wishlist_share_token` / `accept_wishlist_share` |
| Wishlist PDF export (A4, never shows reservation status) | 🚧 | ✅ | |
| Calendar private events (`is_private`) + per-event colour + "Going with" attendees + creator name | 🚧 | ✅ | `is_private`, `color`, `attendee_ids text[]` |
| Colour picker for meal plans / shopping lists / wishlists; meal plans always active | 🚧 | ✅ | `color` int on each |
| Birthday custom icon + colour; creator-only edit | 🚧 | ✅ | `icon`, `color`; RLS split |
| Google profile-completion prompt | 🚧 | ✅ | |
| Background location sharing (dedicated service) | 🚧 | ✅ | iOS `LocationSharingService`; Android v1 shipped foreground-only |
| Chat unread badge made idempotent via persisted `last_read_at` | 🚧 | ✅ | new UPDATE RLS on `conversation_participants` |
| Platform-aware push (`device_push_tokens.platform`) | ✅ | ✅ | iOS APNs payload, Android data-only |

## Design & IA (iOS ✅; Android via parity track 🚧)
- **Liquid Glass design language** (iOS 26): translucent glass surfaces, ambient radial-wash
  background, per-feature accent colours, gradients reserved to identity surfaces. Android mirrors
  it with Material 3 Expressive + Haze blur. See [[../03_Architecture_and_Design/Architecture and Design]].
- **Tab-bar IA:** Home / Shopping / Chat / Calendar / Profile (Family / Map / Wishlists / Meals /
  Birthdays / Settings are pushed routes). iOS ✅; Android adopting in parity track.

## Backend (shared)
- Supabase Postgres + Auth + Realtime + Storage; RLS on all tables (resolves app user id via
  `(select id from public.users where auth_id = auth.uid() limit 1)`).
- 11 iOS-era migrations, **all already applied to production**: `add_device_push_token_platform`,
  `add_conversation_participants_update_policy`, `grant_conversation_participants_update`,
  `add_family_relations`, `restrict_wishlist_edits_to_owner`,
  `add_color_to_meal_shopping_wishlist`, `add_calendar_event_attendees`,
  `add_birthday_icon_color_and_owner_rls`, `add_calendar_private_and_color`,
  `harden_family_and_message_rls`, `add_wishlist_share_links`.

## Backlog / not started
- Offline caching layer (both apps are online-only against Supabase today).
- Any feature that is iOS-only above remains 🚧 until the parity track lands it on Android.

## Related notes
- [[../05_Implementation_Plan/Implementation Plan]]
- [[../05_Implementation_Plan/Android Parity Track]]
- [[../03_Architecture_and_Design/Architecture and Design]]
