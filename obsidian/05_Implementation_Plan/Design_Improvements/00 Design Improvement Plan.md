# Design Improvement Plan (D1–D8)

> The dedicated implementation plan for the UI/UX overhaul project. This is a **separate
> milestone track** from the main [[05_Implementation_Plan/Implementation Plan]] and ships on
> its own branch. Discovery, audit, and rationale live in
> [[05_Implementation_Plan/Project - UI - UX - improvements]]; this folder holds the
> screen-by-screen build specs.

## Goal
Take The Family App from "polished prototype" to "looks like a multi-billion-dollar company
made it." Reviewer lens: senior Apple UX (clarity, deference, depth, accessibility) + Linear
UI (systematized, fast, zero visual noise).

## Branch & review workflow (project-specific — overrides the default chain)
- **All work for this project ships to a single branch: `upgrade/design_improvements`.**
- **Do NOT merge to `test` or `master`** — the user reviews on `upgrade/design_improvements`
  and will say explicitly when to merge.
- Each milestone is a set of commits on that branch (one logical commit per screen/unit so the
  diff is easy to review). Push after each milestone.
- Every milestone builds clean: `./gradlew assembleDebug` and `./gradlew lint` (Spotless
  enforced — run `./gradlew spotlessApply`).
- Note: builds are run by the user manually (token saving) — produce compiling code and call
  out when a build/verification is needed rather than running it automatically.

## How to read each spec
Every D-file breaks its milestone down **per screen**, each with the same shape:
1. **Source file(s)** — the actual Kotlin file(s) to touch.
2. **Current state** — what the screenshot shows today + the problems (cross-refs the audit P-IDs).
3. **Target design** — the redesign.
4. **Layout & components** — which design-system components/tokens to use (all from D1).
5. **States** — default / empty / loading / error.
6. **Accessibility** — contrast, labels, targets, font-scaling notes.
7. **Acceptance criteria** — checklist to call the screen done.

## Milestones
| ID | Milestone | Gates | Spec |
|----|-----------|-------|------|
| D1 | Design system foundation | **prerequisite for all** | [[Design_Improvements/D1 — Design System Foundation]] |
| D2 | Accessibility & content correctness | D1 | [[Design_Improvements/D2 — Accessibility & Content Correctness]] |
| D3 | Navigation & information architecture | D1 | [[Design_Improvements/D3 — Navigation & Information Architecture]] |
| D4 | Home as a glanceable live feed | D1, D3 | [[Design_Improvements/D4 — Home Glanceable Feed]] |
| D5 | Empty states, skeletons & micro-interactions | D1 | [[Design_Improvements/D5 — Empty States, Skeletons & Micro-interactions]] |
| D6 | Feature deep-dives (family/wishlist/map/meals/shopping) | D1, D5 | [[Design_Improvements/D6 — Feature Deep-Dives]] |
| D7 | Chat & auth modern table-stakes | D1 | [[Design_Improvements/D7 — Chat & Auth Table-Stakes]] |
| D8 | Polish, motion & platform extras | D1–D7 | [[Design_Improvements/D8 — Polish, Motion & Platform Extras]] |

## Global definition of done (applies to every screen in every milestone)
- Uses **only** design-system tokens & components from D1 — zero hardcoded colors/dp/sizes.
- Light **and** dark mode verified.
- Empty / loading / error states present (where applicable).
- Contrast ≥ 4.5:1 text / 3:1 large text & icons; every icon-only control has a
  `contentDescription`; touch targets ≥ 48dp; legible at 200% font scale.
- No Norwegian/placeholder strings; copy is final English.
- Builds clean; Spotless/lint clean.

## Status
**✅ TRACK COMPLETE — all D1–D8 delivered and merged to `master`** (2026-06-27, PR #11,
`upgrade/design_improvements` → `test` → `master`).

**Post-track work (same PR + follow-ups):**
- **Lint** brought to 0 (removed ~230 unused legacy resources; advisory-only checks scoped).
- **detekt** added alongside Spotless (0 findings, no baseline; `config/detekt/`).
- **Unit suite revived**: 106/382 failing → **382/382** (stale infra: Supabase client in JUnit,
  uncaught realtime subscriptions, `Log` not mocked, temp-ID collisions, DataStore isolation).
- **Maestro UI flows** added (`maestro/`, one per page) + full on-device test pass.
- **Bug fixes:** wish reserve was broken (missing `wish_reservations` table GRANT →
  `supabase/grant_wish_reservations_privileges.sql`); profile/family photo EXIF rotation
  (shared `compressImageWithOrientation()` util); extended-FAB a11y label; doubled FAB padding;
  compact top bars (`AppTopBar`).

| ID | Status | Branch commits | Notes |
|----|--------|----------------|-------|
| D1 | ✅ Complete | `e2c2ca6`, `9700aac` | **Foundation:** tokens (Spacing/Elevation/Radius/Motion), semantic + feature-accent colors, PrimaryButton disabled-gray fix (C2), field/button radius standardization (C3), new DestructiveButton/AppFab/AppFabSmall/ListCard/SectionHeader/AppLargeTopBar. **Migration:** Shopping/Wishlist/Meal/Birthday → AppFab + ListCard; Calendar → AppFabSmall; Profile/Family → DestructiveButton; dead imports removed. Build green after each batch. **Deferred by design** (avoid rework — these screens get structural redesigns later): AppLargeTopBar application → D3; Home grid→feed → D4; Family/Map card layouts → D6. |
| D2 | ✅ Complete | `7d4a019` | Contrast: light onSurfaceVariant Slate500→Slate600 (was ~4.3:1, now clears AA). Content bugs fixed: B1 Profile email spacing/overflow, B6 Family name+email truncation, B3 birthday naming (code + `supabase/strip_birthday_suffix.sql` — **applied via Supabase MCP, 4 rows back-filled**), B4 meal "N days" (honest), B5 map fallback "Location shared". B2: no hardcoded non-English strings found. **Deferred:** full "N of M planned" progress → D6d; map reverse-geocoding → D6c. **Manual still needed:** TalkBack walkthrough + 200% font pass (can't automate). Build green. |
| D3 | ✅ Complete | `65cc275` | Calendar/Chat/Family/Profile → `AppLargeTopBar` (large-title), differentiated from detail screens' FeatureTopBar+back. Removed Family's vestigial no-op back arrow. Transitions verified intact (tabs fade, detail slides — M18). Tab set kept at 5; buried features surfaced via Home feed (D4) not a tab. **Deferred:** Home top bar → D4 (redesign); notifications bell → future (needs a notification center — a dead bell is worse than none). Build green. |
| D4 | ✅ Complete | `9fe4520` | Home now shows live glanceable summary cards (Tonight's meal, Next event, Shopping left, Next birthday) above a "Quick access" feature grid. HomeViewModel fetches a best-effort family-scoped summary (mirrors feature-VM queries; degrades gracefully). SummaryCard built on D1 ListCard; cards render only with content. Header/family banner unchanged. **Deferred:** "Who's home" map card → D6c (location is privacy-gated/only live on map screen). Build green. |
| D5 | ✅ Complete | `1554c0c` | Empty-state CTAs on Shopping/Wishlist/Meal/Birthday (open the create flow). New `ListSkeleton` shimmer replaces bare spinners on those 4 list screens + Home feed. Haptics on check-off (shopping items, wishes); `Modifier.animateItem()` on all list rows for smooth add/remove/reorder. **Deferred:** pull-to-refresh → D8 (RefreshOnResume already keeps lists fresh); detail-screen empty states (shopping/wishlist already had them). Build green (2 passes). |
| D6 | ✅ Complete | `826ffe4`, `fffeb36`, `d1ca1ad`, `b285c16` | **D6a** family invite via system share sheet. **D6b** wishlist reserve/claim — family members reserve gifts; owner can't see reservations (enforced by RLS via a `wish_reservations` table, applied via Supabase MCP). **D6c** map roster shows reverse-geocoded place ("Place · last seen"). **D6d** shopping collapsible "Completed (N)" group. **Follow-ups delivered:** QR code + `familyapp://join` deep-link invite; wish link/price/image (rich AddWishDialog + dedicated `wish-images` bucket); per-list "N of M bought" + per-plan "N of M dinners planned" progress counts. **Dropped per request:** meal-type slots (planner is dinner-only). All builds green; migrations applied via Supabase MCP. |
| D7 | ✅ Complete | chat + google-oauth commits | **Read receipts** ("Seen", reuses last_read_at + live participants subscription). **Typing indicator** (Realtime broadcast, animated dots, throttled + auto-clear). **Presence** ("Active now / last seen" in 1:1 header; users.last_active_at bumped on foreground). **Tap-to-timestamp** already existed (verified). **Google sign-in** (browser OAuth via familyapp://auth; "Continue with Google") — set up by user in Google Cloud + Supabase. All builds green; migrations via MCP. |
| D8 | ✅ Complete | pull-refresh + splash commits | **Pull-to-refresh** on Shopping/Birthday/Meal/Wishlist (new `PullRefresh` component). **Colored events** already present (calendar cards + month dots color-coded by category — verified). **Gradient cleanup (C10)** verified: gradients only on sanctioned identity surfaces. **Calendar Month/Week/Agenda toggle** ✅. **Themed splash + brand adaptive icon** ✅ (white logo on indigo via core-splashscreen). **Deferred (optional):** home-screen Glance widget; full TalkBack/200%-font accessibility sign-off. |
