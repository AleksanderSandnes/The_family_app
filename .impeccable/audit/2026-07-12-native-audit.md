# Native Technical Audit — The Family App (2026-07-12)

Code-level audit from source (no emulator). Scored against HIG (iOS) + Material 3 (Android) and the project's own PRODUCT.md/DESIGN.md contract.

## Audit Health Score

| # | Dimension | Score | Key Finding |
|---|-----------|-------|-------------|
| 1 | Accessibility | 2/4 | iOS has no Dynamic Type at all; Reduce Motion never consulted (both); swipe-delete invisible to TalkBack; English contentDescriptions for NB users |
| 2 | Performance | 3/4 | Clean launch paths, incremental chat realtime, good lazy-list hygiene; but a real-time blur node per list row (both platforms) is the standing perf tax |
| 3 | Appearance & Theming | 3/4 | Token discipline genuinely good, both palettes first-class; off-palette reds/greens (#E53935, #22C55E) and non-adaptive iOS grays drift |
| 4 | Platform Conformance | 3/4 | Android: Material primitives underneath the brand identity (pill tab bar is a real NavigationBar); iOS strongly HIG-native; deploymentTarget 26.0 is a reach decision to confirm |
| 5 | Adaptivity | 2/4 | Android Home adapts (3-col ≥600dp) but other screens stretch; iOS is iPhone-portrait-only (TARGETED_DEVICE_FAMILY 1) |
| **Total** | | **13/20** | **Acceptable — significant work needed (concentrated in a11y + adaptivity)** |

Per-platform where they differ: Accessibility Android 2 / iOS 2 · Performance 3 / 3 · Theming 3 / 3 · Conformance Android 3 / iOS 3.5 · Adaptivity Android 2.5 / iOS 1.

## Platform Conformance Verdict

**Pass — this reads as a native app on both platforms, not a ported website.** Android's deliberate iOS-isms (floating pill tab bar, slide transitions, centered top bar) are implemented *with* Material primitives (`NavigationBar`/`NavigationBarItem` under the pill, `AppNavHost.kt:198-260`; stock M3 dialogs/FABs/snackbars/pickers), keeping semantics, ripples, and state restoration. iOS uses system `TabView` + `NavigationStack` with typed paths (edge-swipe back free), system alerts/sheets/confirmationDialogs, SF Symbols, native `.glassEffect`. Edge-to-edge and IME insets are release-grade on Android (`MainActivity.kt:30`, `imePadding` on all four text-entry surfaces, `adjustResize`); Info.plist has all six privacy usage strings with honest copy.

## Detailed Findings by Severity

### P1
1. **iOS: Dynamic Type entirely absent** — `DesignSystem/Typography.swift:5-32` is all fixed `Font.system(size:)`; zero `relativeTo:`/`ScaledMetric`/`dynamicTypeSize` in the codebase; violates DESIGN.md's own sp Rule. Fix: rebuild ramp on text styles. (Accessibility)
2. **Both: Reduce Motion never consulted** — 0 hits for `accessibilityReduceMotion` (iOS) or animator-scale checks (Android). Infinite animations run unconditionally: shimmer `Components.kt:784`, typing dots `ChatScreens.kt:1399`, recording pulse `ChatScreens.kt:889`. Fix: one gating helper per platform. (Accessibility)
3. **Android: swipe-to-delete has no accessibility action** — `Components.kt:658-770` is pure pointerInput; TalkBack users cannot delete lists/events/wishes where swipe is the only affordance. Fix: `semantics { customActions }` in the shared component. (Accessibility)
4. **Android: TalkBack speaks English to Norwegian users** — ~16 hardcoded English contentDescriptions (`FamilyScreen.kt:410-625`, `ImageViewerDialog.kt:102-160`, `Pickers.kt:66`, …) plus English a11y strings built in `ChatScreens.kt:959-968` and `ChatViewModel.kt:252,298`. (Accessibility/i18n)
5. **Android: a blur node per list row** — `Glass.kt:204-208` `hazeEffect` per glassCard/rowSurface inside LazyColumns (37 call sites) + tab-bar chrome re-sampling scrolling content per frame. Mitigations present (static wash source, cheap <31 fallback). Fix: profile mid-range; if janky, rows take the fallback fill, blur stays on containers/chrome. (Performance)

### P2
6. **Contrast**: `CaptionDark #6A7290` on `#0B0D16` ≈ 3.9:1 (below AA); `LiveGreenText #059669` ≈ 3.3:1 and `WeekAmberText #B45309` ≈ 4.4:1 on the light wash (`Color.kt:60,69,70`, mirrored `Colors.swift:106-112,142`). Fix: darken light variants (#047857, #92400E), lift caption-dark (≥ #7C84A3). (Theming/Accessibility)
7. **Android: fixed-height chrome clips at large font scale** — tab bar `height(64.dp)` `AppNavHost.kt:208`, add-item field 46dp `ShoppingScreens.kt:425`, sheet capsule 34dp `Sheets.kt:89`, DateTimeRow chips `CalendarScreen.kt:1008-1034`. Fix: `heightIn(min=…)`; test fontScale 2.0. (Accessibility)
8. **Off-token status colors** — `Color(0xFFE53935)` at `AppNavHost.kt:233`, `ChatScreens.kt:900,916` (token: Destructive/Danger); `Color(0xFF22C55E)` at `BirthdayScreen.kt:214,279` (token: LiveGreen); `GlassProgressBar` default hardcodes `0xFF4F55E6` (`GlassComponents.kt:64`, token: Accent); `FamilyScreen.kt:562` rebuilds the brand gradient from raw hex. (Theming)
9. **iOS: non-adaptive one-off grays** — `0x9CA2BC`/`0xA6ACC4`/`0x767E9C` for checked/reserved states (`ShoppingScreens.swift:337,355`, `WishlistScreens.swift:348,360,389`) render identically in dark. Fix: adaptive semantic token. (Theming)
10. **iOS: no `GlassEffectContainer`** — per-row `glassEffect` without render coalescing on the heavy screens (Calendar, Chat). (Performance)
11. **Android chat scaling**: one realtime channel per conversation + per-conversation getLastMessage on every list load (`ChatViewModel.kt:244-315`). Fine at family scale; the hotspot if conversations grow. (Performance)
12. **Android: 36dp touch target** — `VoiceNoteMessage.kt:139` IconButton sized 36dp (min 48dp). (Accessibility)
13. **Android tablets: stretched single-column forms** — no max content width outside Home. Fix: `widthIn(max=640.dp)` wrapper on settings/profile/detail forms. (Adaptivity)
14. **iOS: iPhone-portrait-only + deploymentTarget 26.0** (`project.yml:7,75,100-101`) — iPad support and pre-2025 devices are scope decisions to confirm before store release. (Adaptivity/Conformance)

### P3
15. Predictive back: works via targetSdk 37 default; add the manifest flag explicitly and verify preview animation on API 34-35. (Conformance)
16. `FeatureAccent.kt:12` KDoc claims `isSystemInDarkTheme` but code uses `appDarkTheme()` — stale comment. (Theming)
17. Android foreground-service notification text hardcoded English (`LocationForegroundService.kt:136`). (i18n)
18. No WindowSizeClass API usage (manual 600dp checks). (Adaptivity)

## Patterns & Systemic Issues

1. **Promised-but-missing a11y infrastructure**: PRODUCT.md commits to reduced motion + text scaling; neither platform gates motion, iOS doesn't scale text. The gap is shared policy code — one helper per platform fixes many screens.
2. **The localization bypass**: infrastructure is excellent (471-key parity, in-app switch, iOS `L()`), but ~24 `Text("…")` literals, ViewModel-built strings, and English contentDescriptions leak past it — concentrated in chat dialogs and family management.
3. **Per-row glass material** is the recurring perf tax on both platforms.
4. **Status colors drift off-token** precisely where the palette already has the right token.

## Positive Findings

- The shared design contract is real code: `Glass.kt` ↔ `Glass.swift`, `Color.kt` ↔ `Colors.swift` match hex-for-hex, including the API<31 fallback and the dark-hairline-in-light correction.
- Contrast was engineered, not guessed (`Theme.kt:35-37` documents the Slate600-over-Slate500 AA decision).
- Chat realtime is incremental (append/patch, throttled typing) — better than the repo's own documented pattern.
- Lazy-list hygiene: stable keys + `animateItem` everywhere it matters.
- Inset/IME handling and manifest/Info.plist hygiene are release-grade.
- The pill tab bar's Material-primitives-underneath approach is the right way to ship a cross-platform identity on Android.

## Recommended Actions (priority order)

1. **[P1] `/impeccable polish`**: swipe-delete a11y action, localized contentDescriptions + string extraction, off-token color corrections, font-scale-safe chrome, reduce-motion gating, contrast token fixes.
2. **[P1] `/impeccable typeset` (iOS)**: rebuild the iOS type ramp on Dynamic Type text styles.
3. **[P1] `/impeccable harden`**: forgot-password reset flow; errorEvent→snackbar for optimistic writes; delete confirmation/undo.
4. **[P2] `/impeccable optimize`**: profile per-row blur on a mid-range Android device; add `GlassEffectContainer` on iOS heavy screens; chat channel consolidation.
5. **[P2] `/impeccable adapt`**: tablet max-width wrappers (Android); iPad/orientation scope decision (iOS).
