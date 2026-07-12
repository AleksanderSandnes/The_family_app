# Design Review — Impeccable pass (2026-07-12)

First full `/impeccable` critique + audit + polish across both apps. Full reports live in-repo:

- Critique snapshot: `.impeccable/critique/2026-07-12T14-31-13Z__full-app-android-ios.md` — **28/40** (Good). No AI-slop verdict; weak areas: user control/undo (2), error prevention (2), error recovery (2), help (2).
- Native audit: `.impeccable/audit/2026-07-12-native-audit.md` — **13/20** (Acceptable). Weakest: accessibility (2/4), adaptivity (2/4).
- Strategic/visual context now captured in root `PRODUCT.md` + `DESIGN.md` ("The Glass House").

## Polish pass shipped (branch `fix/impeccable-polish`)

**Android**
- SwipeToRevealDelete now exposes a TalkBack custom delete action (was gesture-only).
- Confirmation dialogs before destroying shared containers: shopping lists, calendar events, meal plans.
- ~60 hardcoded English strings wired to existing EN/NB resources (chat dialogs, permissions onboarding, family dialogs, map rationale, foreground-service notification, a11y descriptions); +24 new keys added to both locales; `perm_location_desc` grammar fixed.
- Reduce-motion support: `reducedMotion()` helper gates shimmer skeleton, typing dots, recording pulse.
- Font-scale safety: tab bar / sheet confirm / add-item field now `heightIn(min=…)` instead of fixed heights.
- Shopping list no longer scroll-yanks to the bottom when checking off an item (only follows on add).
- Off-token colors → tokens (`#E53935`→Destructive, `#22C55E`→LiveGreen, GlassProgressBar→Accent, FamilyScreen inline gradient→BrandGradientSoft); voice-note play button 36→48dp; image-viewer buttons 44→48dp; received chat text onSurfaceVariant→onSurface; weekday labels locale-aware.
- Contrast tokens (AA): CaptionDark #6A7290→#7C84A3, LiveGreenText #059669→#047857, WeekAmberText #B45309→#92400E.

**iOS (mirrored, compile on Mac)**
- Typography.swift rebuilt on `UIFontMetrics` — every token now scales with Dynamic Type (same base sizes, same names, zero call-site changes).
- Colors.swift mirrors the three contrast-token changes; non-adaptive one-off grays in Shopping/Wishlist rows → `Color.appCaption`.
- RecordingPulse honors `accessibilityReduceMotion`.

Verified: `assembleDebug`, `testDebugUnitTest`, `detekt`, `spotlessCheck` all green. iOS changes are conservative and await Mac compile.

## Follow-up pass shipped (branch `feat/impeccable-followups`, same day)

Owner ruled the gradient front door **drift** → fixed; forgot-password email flow explicitly deferred.

- **Glass House front door** — Android `AuthScaffold` + `PermissionsOnboardingScreen` rebuilt on `AmbientBackground` with glass cards; the brand gradient survives only on the identity badge and the primary CTA (One Gradient Rule). iOS `AuthScaffold` mirrored (`.ambientBackground()` + `.glassCard`, gradient badge); iOS onboarding cards moved from flat surface to glass.
- **Failure signals** — meal's `errorRes`→snackbar pattern replicated in Shopping, Wishlist, Calendar, Birthday ViewModels: every create/update/delete/toggle/reserve now surfaces "Couldn't save/delete" instead of silently reverting.
- **Undo** — deleting a shopping item or a wish shows a snackbar with Undo (re-inserts the row, content preserved).
- **Tablets** — settings/profile/edit-profile forms capped at 640dp and centered.

Verified: `assembleDebug`, `testDebugUnitTest`, `detekt`, `spotlessCheck` green.

## Critique re-run (same day) — 28/40 → 28/40, substance up

Fresh dual-agent re-run verified every fixed item (0 hardcoded literals, 0 ungated animations, iOS Dynamic Type working, 5/10 delete sites protected, 8/13 VMs surfacing errors) but found new P1s the first pass missed. Snapshot: `.impeccable/critique/2026-07-12T15-44-58Z__full-app-android-ios.md`.

**Third fix pass shipped** (`feat/impeccable-followups`): Home error state keeps the static feature grid + gains PullRefresh (the error copy said "pull to refresh" on a screen without it); wishlist-container and birthday deletes now confirm; profile save gate relaxed to name+email (was un-savable for Google sign-ups without birthday); final ~12 English fragments localized (+11 keys EN/NB); ChatViewModelTest updated. All checks green.

## Critique run 3 — 29/40 (28 → 28 → 29)

Destruction model scans clean (9/9 sites protected), Home error recovery passes, Aesthetic 4/4. New findings fixed same day (`feat/impeccable-followups`): map legend now shows your own sharing state with an inline toggle; voice recording gained slide-to-cancel + <1s discard + failed-start guard; cancelled Google sign-in no longer bricks the login form; auth errors are @StringRes (localize in NB); calendar formatters resolve per call (in-app language switch works without restart) + locale 12/24-h times + pull-to-refresh; sign-out and Clear-completed confirm; ~15 more strings localized; avatar-fallback and iOS shadow colors tokenized; remaining sub-48dp targets bumped. Snapshot: `.impeccable/critique/2026-07-12T16-21-30Z__full-app-android-ios.md`.

## Owner decisions + feature track (branch `feat/roles-message-edit-chat-glass`)

Owner ruled: full chat design milestone; message edit + delete; admin-gate destructive acts. Shipped as three milestones, each build/test/lint-verified:

- **M1 — creator-or-admin destruction** (`add_destructive_action_gating.sql`, applied to production): deleting shopping lists / calendar events / meal plans / conversations now requires the creator or family admin, enforced in RLS and mirrored in both apps' UI (delete affordances hidden otherwise). `meal_plans` gained `created_by`; admins never see or delete DMs they're not in. Recorded in PRODUCT.md as "Trust & Permissions".
- **M2 — message edit/delete** (`add_message_edit_delete.sql`, applied): own messages editable (composer edit mode, "(edited)" marker) and deletable (Android: snackbar undo re-inserting with original sent_at; iOS: native confirm alert); realtime update/delete flows patch open threads live; replica identity full on messages.
- **M3 — chat joins the Glass House** (Android): glass composer bar + glass capsule text field, PrimaryButton in the new-conversation sheet, list skeleton, Spacing tokens and AA secondary ink in the chat list. (iOS chat was already the glass reference.)

## Still open

1. **Invite links** — `familyapp://join` is inert without the app installed; needs an HTTPS App Link domain + store-redirect page (owner decision pending).
2. Forgot-password reset flow — deferred by owner ("not yet"); the coming-soon dialog stays.
3. iOS `GlassEffectContainer` — skipped deliberately: it visually merges nearby glass shapes, so it needs on-device verification on a Mac before shipping.
4. Per-row Haze blur profiling on a mid-range Android device.
5. iOS iPad/orientation + deploymentTarget-26 scope decision.
6. `ChatTimeFormatters` English tokens ("Yesterday", "Active now", "m ago") — pure JVM-tested helpers; localizing needs a deliberate API shape (string params vs context).
7. Chat as the least-glass room (stock composer/button, literal paddings) — candidate next design milestone.
8. Softer observations: Calendar/Chat create buttons top-right vs FABs elsewhere; chat timestamps at 50% alpha; EventDialog density (~9 decisions); "No family yet" CTA weight vs empty tiles; no per-message delete/edit in chat; no role model beyond family admin (a 10-year-old can delete any conversation); iOS 138 inline fixed fonts bypass the scaled type tokens.
