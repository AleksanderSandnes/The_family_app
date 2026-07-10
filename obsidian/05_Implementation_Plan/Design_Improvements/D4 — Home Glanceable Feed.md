# D4 — Home as a Glanceable Live Feed

> The home screen is the app's most valuable real estate and today it does the least — a static
> grid of large empty navigation tiles. A flagship family app surfaces glanceable, live content
> here. Depends on D1 (components) and D3 (top bar). Audit refs: Home P1/P2, pain point #2.

**Source files:** `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`,
`ui/home/HomeGreetingUtils.kt`.

---

## Current state
- `largeTitle` greeting ("Good evening, Aleksander") + avatar — good, keep.
- Family banner (photo, name, member count) — keep but improve.
- 2×3 grid of feature tiles, each large with lots of empty white, text pushed to the bottom.
  Pure navigation; no live data. ~Half the screen is wasted.

## Target design — a vertical feed of live cards
Greeting header → family banner → **live summary cards** → quick-access chips. Each card pulls
real data from the existing feature ViewModels (already Activity-scoped and pre-loaded per M22).

### Card inventory (render only cards with data; otherwise show a single combined CTA)
| Card | Data source | Content | Tap target |
|---|---|---|---|
| **Tonight's meal** | `MealViewModel` (today in current week plan) | "Tonight: {meal}" or "No dinner planned" | Meal detail |
| **Next event** | `CalendarViewModel` (next upcoming) | "{event} · {relative time}" | Calendar (that day) |
| **Shopping** | `ShoppingViewModel` | "{N} left to buy" across lists | Shopping |
| **Next birthday** | `BirthdayViewModel` | "{Name} turns {age} in {N} days" + avatar | Birthdays |
| **Who's home** | `FamilyMapViewModel` | member avatars + "{N} home / sharing" | Family Map |

### Family banner improvements (Home P2)
- Fixed-ratio cover (e.g. 16:9) with a gradient scrim (`brandGradient` token) so name/count are
  always legible regardless of the user photo.
- Show a small avatar row of members + "active now" indicator if available.

### Quick-access chips (below the feed)
- A horizontal row / wrap of chips for every feature (Shopping, Meals, Calendar, Birthdays,
  Wishlists, Map, Chat) so nothing is more than one tap away even when its card is empty.

## States
- **Loading:** `SkeletonLoader` cards (from D5) — not a full-screen spinner.
- **Empty (new user / no family):** a single onboarding card — "Create or join a family to get
  started" → Family screen. Hide feature cards until in a family.
- **Per-card empty:** card shows its empty line + CTA (e.g. "Plan tonight's dinner").
- **Error:** card silently falls back to its empty line (don't block the whole feed).

## Accessibility
- Each card is one focusable element with a combined `contentDescription` ("Next event, Test,
  in 2 hours"). Cover scrim keeps text ≥ 4.5:1. Targets ≥ 48dp.

## Implementation notes
- `HomeViewModel` aggregates from the already-hoisted feature ViewModels (inject via Hilt or
  read their `StateFlow`s) — avoid new network calls; reuse the M22 prefetch/cache.
- Keep the M22 `RefreshOnResume` so the feed updates when returning to Home.

## Acceptance criteria (D4)
- [ ] Home shows live data from meals/calendar/shopping/birthdays/map.
- [ ] Family banner legible over any photo; quick-access chips present.
- [ ] Loading uses skeletons; new-user empty state shown; per-card empties have CTAs.
- [ ] One-glance comprehension without navigating; each card opens the right screen/day.
- [ ] Builds clean; Spotless/lint clean; light+dark + 200% font verified.
