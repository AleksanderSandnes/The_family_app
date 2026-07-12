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

## Recommended follow-ups (not done)

1. `/impeccable harden` — forgot-password reset flow (P1, currently a dead-end dialog); errorEvent→snackbar for optimistic writes outside chat; snackbar-undo for row deletes.
2. `/impeccable optimize` — profile per-row Haze blur on a mid-range device; iOS `GlassEffectContainer` on heavy screens.
3. `/impeccable adapt` — tablet max-width wrappers (Android); iPad/orientation scope decision (iOS is iPhone-portrait-only, deploymentTarget 26.0).
4. Auth + permissions onboarding visually abandon the Glass House (full-bleed gradients) — a deliberate-or-drift decision worth making.
