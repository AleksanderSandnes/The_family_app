# Delivered — Design & UI Polish

Visual polish, navigation transitions, and the design-overhaul history. Part of
[[../Implementation Plan]]. The current design language is the Liquid Glass system — see
[[../../03_Architecture_and_Design/Architecture and Design]].

## Milestone 17 — frontend polish pass ✅
Delivered in 8 batches (all `feat/m17-* → test → master`):
1. Chat message ordering; birthday date required; profile birthday display formatting.
2. Chat auto-scroll + timestamps + swipe-to-reply (Messenger-style — see [[Chat]]); swipe-to-delete
   for shopping + wishlist rows (`SwipeToRevealDelete`); leave-family confirmation; "Visible on
   family map" toggle persisted.
3. Cross-family data isolation on Chat/Calendar/Shopping/Birthday (two-layer filter — see
   [[Backend & Data Sync]]).
4. `LoadingState` + `isLoading` on all 6 list VMs (kills the flash-of-empty-state).
5. Button validation (login, register, profile edit, calendar dialog, chat send FAB).
6. Dead-code removal (`SectionHeader`, `rowContentColor`, unused imports).
7. Stuck-spinner fix — a non-local `return` inside an inline `runCatching` bypassed
   `_isLoading = false` when `getUser()` was null; replaced with an `if (user != null)` guard.
8. Visual consistency audit (card radii 20dp, shadows 2dp, padding 18/20dp, settings scroll).

## Milestone 18 — navigation transitions ✅
`NavHost` default = iOS-style horizontal slide + fade (300 ms) for detail/feature screens; the 5
bottom-tab destinations override with a plain fade (200 ms) so siblings don't slide against each
other.

## Milestone 23 — design overhaul ⛔ SUPERSEDED
Marked "delivered" but most design-screen units (home, calendar, chat, shopping, meal, birthday,
wishlist, map) never landed — they sat as partial work in abandoned worktrees. Only PRs #5–#10 and
#14 merged (family, auth, settings, components, DB migrations + test users). All remaining design
work was re-planned screen-by-screen in the **D1–D8 design track**, which then **completed and
merged to master** (2026-06-27, PR #11). See
[[../Design_Improvements/00 Design Improvement Plan]] and [[../Project - UI - UX - improvements]].

## Design track D1–D8 — complete ✅
The successor to Milestone 23. All 8 milestones delivered; per-screen specs + commit log in
[[../Design_Improvements/00 Design Improvement Plan]]. The same effort brought lint + detekt clean,
revived the unit suite (see [[Testing & Quality]]), added Maestro UI flows, and fixed several
on-device bugs (wish-reservation GRANT, photo EXIF rotation, FAB a11y/padding).
