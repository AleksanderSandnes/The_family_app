# D6 — Feature Deep-Dives (highest-value redesigns)

> The screen-specific redesigns that add real product value, not just polish. Depends on D1 +
> D5. Sub-tracked (D6a–D6d) so each can ship as its own commit set on
> `upgrade/design_improvements`. Audit refs: Family P1, Wishlist P1, Map P1, Meal/Shopping P1/P2.

---

## D6a — Family onboarding & admin management
**Source:** `ui/family/FamilyScreen.kt`, `ui/family/FamilyViewModel.kt`,
`data/FamilyRepository.kt`.

### Current state
Family card with name, overlapping avatars, "2 members", raw invite code "75965D3C" + copy
only. Member list with role badges. "Leave family". No share, no QR, no admin actions beyond
rename/remove (PR #5 added remove).

### Target
- **Invite via system share sheet + QR (P1):** primary "Invite" button → share sheet with a
  deep link (`familyapp://join?code=XXXX`) + a generated **QR code** (use a QR lib or
  ZXing) shown in a sheet. Keep the copyable code as fallback.
- **Admin management:** per-member overflow → "Remove member" (exists), "Make admin"
  (transfer), and family "Rename" (exists via EditFamilyDialog). Non-admins see read-only.
- Member rows use `ListCard`; email truncates (D2 B6); role via `Chip`.

### States
- Empty: not-in-a-family → `EmptyState` with "Create family" / "Join with code" CTAs.
- Loading: skeleton member rows.

### Acceptance
- [ ] Invite produces a working deep link + scannable QR; code copy still works.
- [ ] Admin can transfer admin and remove members; non-admin is read-only.
- [ ] Deep link opens the join flow pre-filled.

---

## D6b — Wishlist gifting mechanic
**Source:** `ui/wishlist/WishlistScreens.kt`, `ui/wishlist/WishlistViewModel.kt`,
`data/Entities.kt`, Supabase migration.

### Current state
Wishes are plain checkbox rows (todo-style). The core gifting value — **reserve/claim so two
relatives don't buy the same gift** — is missing. No price, link, or image.

### Target (the single biggest feature upgrade)
- **Wish item =** title + optional **link**, **price**, **image**. Render as a richer `ListCard`
  with thumbnail.
- **Reserve/claim:** other family members see a "Reserve" action; once reserved it shows
  "Reserved by {name}" to everyone **except the wishlist owner** (surprise-preserving — the
  owner never sees reservation state on their own list).
- Drop the todo-style checkbox semantics; "got it" is replaced by reservation.

### Schema migration (run in Supabase SQL Editor)
```sql
alter table wishes add column if not exists reserved_by uuid references public.users(id);
alter table wishes add column if not exists link text;
alter table wishes add column if not exists price text;
alter table wishes add column if not exists image_url text;
```
RLS: owner can read/write own wishes but **cannot** read `reserved_by`; family members can read
wishes and set/clear `reserved_by` (their own reservation).

### States
- Empty: "No wishes yet" → focus add-wish field. Loading: skeleton rows.
- Owner view vs member view differ (reservation hidden from owner).

### Acceptance
- [ ] Wishes support link/price/image; reservation hidden from the owner, visible to others.
- [ ] Migration applied; RLS enforces owner-can't-see-reservation.

---

## D6c — Family Map
**Source:** `ui/map/FamilyMapScreen.kt`, `ui/map/FamilyMapViewModel.kt`.

### Current state
Google map; one huge member marker + name; my-location FAB; bottom "On the map" card showing
"Unknown" and clipped over Google controls; recenter FAB overlaps zoom controls.

### Target
- **Reverse-geocode** lat/lng → place/address (D2 B5 kills "Unknown"); show "updated N min ago".
- **Compact avatar pins** (already custom bitmaps per M20) — reduce size, add white border +
  staleness dimming for old fixes.
- **Member roster bottom sheet** (`AppBottomSheet`): every family member with avatar, distance
  from me, last-updated, and a tap-to-center. Replaces the single clipped card.
- **Control insets:** lift the sheet above Google logo/zoom; reposition recenter FAB so it
  doesn't overlap zoom controls.

### States
- Empty: no one sharing → `EmptyState` overlay explaining how to enable sharing (links Settings
  "Visible on family map").
- Loading: map loads with a skeleton roster sheet.

### Acceptance
- [ ] Real place/address + freshness per member; roster sheet lists everyone with distance.
- [ ] No control overlap/clipping; stale pins visually distinct.

---

## D6d — Meals & Shopping refinements
**Source:** `ui/meal/MealScreens.kt`, `ui/meal/MealViewModel.kt`,
`ui/shopping/ShoppingScreens.kt`, `ui/shopping/ShoppingViewModel.kt`.

### Meals
- Accurate subtitle "N of 7 days planned" + small progress indicator (D2 B4).
- Make the whole day-row tap-to-edit; **drop the redundant pencil** icon (Meal P2).
- Optional **meal-type slots** (breakfast/lunch/dinner) — collapsed to a single "dinner" slot by
  default to keep it simple; expandable per day.

### Shopping
- **"Completed" group:** checked items animate into a collapsible "Completed (N)" section
  (Apple Reminders pattern) instead of staying inline; per-list progress.
- List rows show subtitle "{bought} of {total}" + member avatars (Shopping P1).
- Move the "N left" count from the cramped top-bar position to the list subtitle.

### States / Acceptance
- [ ] Meal subtitle/label accurate; row tap edits; no redundant pencil.
- [ ] Shopping checked items collapse into Completed; per-list progress shows.
- [ ] Both use `ListCard`, empty states (D5), skeletons; build + lint clean.
