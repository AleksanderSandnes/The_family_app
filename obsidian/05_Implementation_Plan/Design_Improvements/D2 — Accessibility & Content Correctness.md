# D2 — Accessibility & Content Correctness

> Make the app usable by everyone and free of the small content bugs that destroy the "shipped
> by a design org" illusion. Depends on D1 tokens (contrast-correct colors live there).
> Audit refs: C7, C8, Auth P0, Profile P0.

---

## A. Accessibility pass (C7) — applies to every screen

### Source files: all `ui/**/*Screen*.kt`, `ui/components/Components.kt`
- **Contrast:** every text/background pair ≥ 4.5:1 (≥ 3:1 for large text & icons), light + dark.
  Muted placeholder/secondary text ("Tap to add meal", "Add item…", "Unknown") raised to a
  token that passes. No pure-gray-on-light.
- **Disabled CTAs:** the gray-with-white-text pattern (Sign in, Add, Create) is replaced by
  D1's `PrimaryButton` disabled style (same fill, 38% opacity). Verified on Auth, all
  add/create dialogs.
- **Icon-only controls:** add `contentDescription` to every one — chat send/mic/camera/gallery,
  list add-item send arrow, family copy-code, map recenter & zoom, profile avatar camera badge,
  calendar month nav arrows, meal edit pencils, overflow (3-dot) menus.
- **Touch targets:** min 48×48dp for checkboxes, pencils, copy icon, chips, nav-bar items.
- **Font scaling:** verify every screen at 200% — no clipped titles, no overlapping rows
  (Birthday cards, Family member email, Settings).
- **TalkBack:** logical focus order; decorative images marked null; toggles announce state.

### Acceptance (A)
- [ ] Automated contrast check (or manual sampling) passes on all screens, both themes.
- [ ] TalkBack walkthrough of the 5 core journeys (add meal, check who's home, add to shopping,
      add birthday, send chat) completes without traps.
- [ ] Every icon button has a description; every target ≥ 48dp; 200% font has no clipping.

---

## B. Content & localization correctness (C8)

### B1. Profile email bug — `ui/profile/ProfileScreens.kt` **(P0)**
- **Current:** "Email" label and value render with no space → "Emailaleksander.sandnes@…"; long
  email overflows.
- **Fix:** label/value as two elements with proper spacing; value `maxLines = 1`,
  `overflow = Ellipsis` (or wrap cleanly). Apply the same row pattern to Mobile/Birthday.
- **Acceptance:** label and value visually separated; long emails truncate gracefully.

### B2. English string sweep — all screens
- "Ukeshandel" (Shopping), "Uke 37" (Meal) → these are partly user data, but **seed/sample/demo
  copy and any hardcoded strings must be English**. Audit `strings` and Kotlin literals.
- Scrub inappropriate demo content (e.g. wishlist "twat", chat test images) from any seed/test
  data and the 4 test accounts.
- **Acceptance:** no non-English hardcoded UI string; demo data is presentable.

### B3. Birthday auto-naming — `ui/birthday/BirthdayScreen.kt`, `data/FamilyRepository.kt`
- **Current:** auto-synced birthdays are named "Lotte Helland birthday" / "X birthday"
  (redundant — the cake icon already says birthday).
- **Fix:** store/display just the person's name; drop the " birthday" suffix in
  `syncUserBirthday`. Migration note: update existing rows (one-off SQL) to strip the suffix.
- **Acceptance:** birthday cards show the person's name only.

### B4. Meal plan label accuracy — `ui/meal/MealScreens.kt`, `MealViewModel.kt`
- **Current:** "7 days planned" when only 1 day has a meal; "Uke 37" doesn't match the shown
  date range (week-numbering bug).
- **Fix:** subtitle = "N of 7 days planned" (count days with a meal); derive the week label
  from the actual date range or localize it to English.
- **Acceptance:** subtitle count matches reality; label matches the date range.

### B5. Map "Unknown" location — `ui/map/FamilyMapScreen.kt`, `FamilyMapViewModel.kt`
- **Current:** bottom panel shows location as "Unknown".
- **Fix:** reverse-geocode (Android `Geocoder`) lat/lng → place/address; show "updated N min ago"
  from `updated_at`. (Deeper map work is in D6c — this entry just kills the "Unknown" string.)
- **Acceptance:** a real place name or address shows instead of "Unknown".

### B6. Family member email wrap — `ui/family/FamilyScreen.kt`
- **Current:** "aleksander.sandnes@hotmail / .com" wraps mid-address → looks broken.
- **Fix:** `maxLines = 1` + ellipsis, or hide email and show role only (avatar + name + role
  badge is sufficient).
- **Acceptance:** no mid-address line break.

## Acceptance criteria (D2)
- [ ] All section A accessibility checks pass.
- [ ] B1–B6 fixed and verified on device.
- [ ] Builds clean; Spotless/lint clean.
