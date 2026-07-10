# D1 — Design System Foundation

> **Prerequisite for everything.** No screen redesign starts until this lands. The single
> biggest "amateur" signal in the app is that every screen reinvents buttons, fields, FABs and
> spacing. D1 defines tokens + a component contract in code and migrates the *atoms* of every
> screen onto them — no layout redesign yet, just unify the building blocks.
> Audit refs: C1, C2, C3, C4, C10.

**Source files:** `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Shape.kt`,
`ui/theme/Theme.kt`, `ui/components/Components.kt`, `ui/components/Scaffolding.kt`.

This milestone is organized by **token group** and **component**, not by screen — but the last
section lists the per-screen atom migration so nothing is missed.

---

## 1. Tokens

### 1.1 Color (`ui/theme/Color.kt`, `Theme.kt`)
- Keep the indigo/violet brand. Expose **semantic roles only** at call sites via
  `MaterialTheme.colorScheme.*`: `primary`, `onPrimary`, `primaryContainer`,
  `surface`, `surfaceVariant`, `outline`, plus custom roles `success`, `warning`, `danger`
  (+ their `on*`).
- Add a **feature-accent palette** (8 roles) so each domain keeps its identity consistently:
  shopping (indigo), meals (amber), calendar (emerald), birthdays (pink), wishlists (violet),
  map (emerald-teal), chat (indigo), family (violet). Define once; reference everywhere.
- **Every** light+dark pair verified ≥ 4.5:1 (text) / 3:1 (large text & icons).
- Define one `brandGradient` token (see 1.6).
- **Acceptance:** grep shows no raw `Color(0xFF…)` at any call site outside `Color.kt`.

### 1.2 Typography (`ui/theme/Type.kt`)
- One Material 3 type scale: `displaySmall`, `headlineMedium` (screen titles), `titleMedium`
  (card titles), `bodyLarge`/`bodyMedium` (content), `labelMedium` (section headers/captions).
- All sizes in `sp`; verified legible at 200% font scale (no clipping).
- **Acceptance:** no ad-hoc `fontSize = ….sp` at call sites; everything maps to a scale role.

### 1.3 Spacing (new `Spacing` object or extension on theme)
- 4-pt grid tokens: `xs=4, sm=8, md=12, lg=16, xl=20, xxl=24, xxxl=32`.
- Canonical values: **screen edge inset = 20dp**, **card inner padding = 18dp**, **inter-card
  gap = 12dp**.
- **Acceptance:** screen/card paddings reference tokens, not literals.

### 1.4 Radius (`ui/theme/Shape.kt`)
- Tokens: `card = 20dp`, `field = 16dp`, `chip = full`, `sheet = 28dp (top)`.
- Aligns with the values M17 already converged toward.

### 1.5 Elevation
- Two levels only: `resting = 2dp` (cards), `raised = 6dp` (FAB / sheets). Kill per-screen drift.

### 1.6 Motion & gradient
- Motion tokens: standard `300ms` slide/fade (matches M18), `spring` for micro-interactions.
- **Gradient job (C10):** `brandGradient` is allowed **only** on identity surfaces — hero
  headers (Profile), outgoing chat bubbles, the brand primary CTA, and the Home family banner.
  Forbidden as generic card backgrounds. Document this rule in `Color.kt`.

---

## 2. Components (`ui/components/Components.kt`, `Scaffolding.kt`)

The **only** building blocks screens may use. Each takes tokens only — zero literal
colors/sizes at call sites.

| Component | Spec | Replaces (audit) |
|---|---|---|
| `PrimaryButton` | Filled `primary`, 56dp tall, `titleMedium` bold, full-width on forms. **Disabled = same fill at 38% opacity** (never a different gray). | C2 — gray "Sign in/Add/Create" that look dead |
| `SecondaryButton` | Tonal `secondaryContainer`, 56dp. | dialog text-buttons |
| `DestructiveButton` | Tonal/red text variant for Leave/Sign out/Delete. | gradient Sign-out, red deletes |
| `AppTextField` | **One** style — Material 3 outlined + floating label, `field` radius. Used in forms **and** dialogs. | C3 — outlined vs filled vs underlined |
| `AppFab` | `extended(icon, label)` + `small(icon)` variants; fixed 16dp inset placement. | C4 — pill vs circular FABs |
| `AppTopBar` | 3 variants: `largeTitle(title, trailing)`, `detail(title, onBack, overflow)`, `modal(title, onClose)`. | C6 — inconsistent top bars |
| `ListCard` | `card` radius, `resting` elevation, 18dp padding, optional leading icon chip + title + subtitle + trailing. | per-screen card drift |
| `EmptyState` | icon + title + body + optional `PrimaryButton`. | C5 — no empty states (built here, used in D5) |
| `SkeletonLoader` | shimmer placeholder rows/cards. | C9 — bare spinners (used in D5) |
| `SectionHeader` | `labelMedium`, muted, uppercase optional. | settings caps headers (C-settings) |
| `Chip` / `FilterChip` | one chip style (countdown, theme, reminder, role badges). | birthday/settings chip drift |
| `AvatarCircle` / `InitialAvatar` | already exist — fold into system, ensure `contentDescription`. | — |
| `AppDialog` / `AppBottomSheet` | shell with standardized title + content + action row using `PrimaryButton`/`SecondaryButton`. | dialog button drift (C2) |

> Several of these already exist (`PremiumButton`, `AppTextField`, `AvatarCircle`,
> `SwipeToReveal*`, `SkeletonLoader`, `ConfirmationDialog`, `EmptyState` from the old M23 PR
> #8/#14). **Audit what exists, consolidate, and delete duplicates** rather than re-create.

---

## 3. Per-screen atom migration (do not skip any)
After tokens + components exist, sweep **every** screen and replace one-off atoms with the
shared components. No layout redesign in D1 — just swap the building blocks so the whole app is
visually consistent before the deeper redesigns.

| Screen file | Migrate |
|---|---|
| `ui/auth/AuthScreens.kt` | buttons → `PrimaryButton`; fields → `AppTextField` |
| `ui/home/HomeScreen.kt` | top bar → `AppTopBar.largeTitle`; cards → `ListCard` |
| `ui/shopping/ShoppingScreens.kt` | FAB → `AppFab.extended`; cards → `ListCard`; add-item field → `AppTextField` |
| `ui/meal/MealScreens.kt` | FAB; cards; inline-edit field |
| `ui/calendar/CalendarScreen.kt` | FAB → `AppFab.small`; event card → `ListCard`; dialog → `AppDialog` |
| `ui/birthday/BirthdayScreen.kt` | FAB; cards; add/edit dialog → `AppDialog`; chips → `Chip` |
| `ui/wishlist/WishlistScreens.kt` | FAB; cards; add-wish field; dialogs |
| `ui/chat/ChatScreens.kt` | top bar; input row buttons; keep bubbles (identity gradient ok) |
| `ui/family/FamilyScreen.kt` | top bar → `largeTitle`; cards → `ListCard`; Leave → `DestructiveButton`; dialogs |
| `ui/profile/ProfileScreens.kt` | buttons; fields → `AppTextField`; Sign out → `DestructiveButton` |
| `ui/settings/SettingsScreen.kt` | already the benchmark — align headers to `SectionHeader`, chips to `Chip` |
| `ui/map/FamilyMapScreen.kt` | FAB → `AppFab.small`; bottom card → `ListCard` |
| `ui/onboarding/PermissionsOnboardingScreen.kt` | buttons → `PrimaryButton`; cards → `ListCard` |

## Acceptance criteria (D1)
- [ ] Token files define color/type/spacing/radius/elevation/motion/gradient roles.
- [ ] Component set exists and is the only set used by screens.
- [ ] `grep` finds no raw hex, ad-hoc sp, or one-off dp paddings at call sites.
- [ ] All primary buttons identical; all text fields identical; all FABs follow one rule.
- [ ] Light + dark verified; nothing visually regressed (screen-by-screen diff review).
- [ ] Builds clean; Spotless/lint clean.
