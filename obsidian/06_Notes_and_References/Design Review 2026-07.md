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

## Still open

1. Forgot-password reset flow — deferred by owner ("not yet"); the coming-soon dialog stays.
2. iOS `GlassEffectContainer` — skipped deliberately: it visually merges nearby glass shapes, so it needs on-device verification on a Mac before shipping.
3. Per-row Haze blur profiling on a mid-range Android device.
4. iOS iPad/orientation + deploymentTarget-26 scope decision.
