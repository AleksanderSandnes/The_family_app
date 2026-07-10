# D5 — Empty States, Skeletons & Micro-interactions

> Kill every blank screen and bare spinner, and make the app *feel* responsive through
> micro-interactions. Depends on D1 (`EmptyState`, `SkeletonLoader`). Audit refs: C5, C9.

---

## 1. Empty states (C5) — one per feature
Use D1's `EmptyState` (icon + title + body + `PrimaryButton`). Each list/detail screen renders
it when there's no content.

| Screen file | When empty | Copy (title / CTA) |
|---|---|---|
| `ui/shopping/ShoppingScreens.kt` (list) | no lists | "No shopping lists yet" / "New list" |
| `ui/shopping/ShoppingScreens.kt` (detail) | no items | "This list is empty" / focus add-item field |
| `ui/meal/MealScreens.kt` (list) | no plans | "Plan your first week" / "Create a meal plan" |
| `ui/calendar/CalendarScreen.kt` | no events on day | "Nothing on {date}" / "Add event" |
| `ui/birthday/BirthdayScreen.kt` | no birthdays | "No birthdays yet" / "Add birthday" |
| `ui/wishlist/WishlistScreens.kt` (list) | no wishlists | "No wishlists yet" / "New wishlist" |
| `ui/wishlist/WishlistScreens.kt` (detail) | no wishes | "No wishes yet" / focus add-wish field |
| `ui/chat/ChatScreens.kt` (list) | no conversations | "No conversations yet" / "Start a chat" |
| `ui/chat/ChatScreens.kt` (conversation) | no messages | "Say hi 👋" / focus input |
| `ui/family/FamilyScreen.kt` | no family | "Create or join a family" / two CTAs |
| `ui/map/FamilyMapScreen.kt` | no one sharing | "No one's sharing location" / explainer |

## 2. Skeleton loaders (C9)
Replace `CircularProgressIndicator` / `LoadingState` with `SkeletonLoader` shimmer placeholders
shaped like the eventual content (cards/rows). Apply on first-load of every list screen and the
Home feed (D4). Keep the M20 stale-while-revalidate cache so skeletons mainly show on cold start.

- Files: all list screens above + `HomeScreen.kt`.
- **Acceptance:** no bare spinner anywhere; first load shows content-shaped shimmer.

## 3. Micro-interactions & haptics (C9)
- **Haptics** (`HapticFeedback`): on check-off (shopping item / wish), send (chat / add-item),
  long-press (reactions, swipe-to-delete commit), toggle (settings). Light/medium per action.
- **Spring animations:** item add/remove (animateItemPlacement), check-off cross-out, FAB press
  scale, reaction pop. Use D1 motion tokens.
- **Pull-to-refresh** on list screens (Material `PullToRefreshBox`) wired to existing
  `refresh()` — replaces the implicit refresh-on-resume as a *visible* affordance.

## Accessibility
- Empty-state illustrations marked decorative; the title+body convey meaning to TalkBack.
- Haptics never the *only* feedback — pair with a visual change.
- Skeletons announce "Loading" to screen readers.

## Acceptance criteria (D5)
- [ ] Every list/detail/Home screen has an empty state with a working CTA.
- [ ] No `CircularProgressIndicator` remains as a primary loader; skeletons everywhere.
- [ ] Haptics on the key actions; add/remove/check animate; pull-to-refresh works.
- [ ] Builds clean; Spotless/lint clean; verified light+dark + TalkBack.
