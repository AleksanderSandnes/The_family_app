# D3 — Navigation & Information Architecture

> Make navigation feel like one app, and make the tab set match real usage. Depends on D1
> (`AppTopBar` variants). Audit refs: C6, Home P2, IA pain point #4.

**Source files:** `ui/navigation/AppNavHost.kt`, `ui/navigation/Routes.kt`, and the top bar of
every screen.

---

## 1. Tab set decision (frequency × value)
**Current bottom nav:** Home · Calendar · Chat · Family · Profile. High-frequency Shopping,
Meals, and Map are buried one level under the Home grid; Calendar (lower frequency than
Shopping) gets a tab.

**Target:** keep 5 tabs (Material guidance), chosen by frequency×value:
- **Home** (hub), **Chat** (high frequency), **Calendar** (shared schedule), **Family** (people
  + map entry), **Profile** (self/settings).
- Shopping, Meals, Birthdays, Wishlists, Map stay reachable from the **Home feed** (D4) and via
  quick-access chips — but Home becomes content-rich (D4), so they're one tap from glanceable
  context, not a dead grid.
- **Decision to confirm with user:** whether to swap Calendar→Shopping in the tab bar, or keep
  Calendar and rely on the Home feed for Shopping. Default: keep current 5 tabs, strengthen Home.

## 2. Top-bar standardization (C6)
Apply D1's three `AppTopBar` variants consistently:
- **`largeTitle`** (top-level: Home, Calendar, Chat list, Family, Profile): large title +
  trailing slot (avatar on Home/Profile; notifications bell with unread dot; optional search).
- **`detail`** (Shopping detail, Meal detail, Birthday, Wishlist detail, Conversation, Profile
  edit, Settings, Family Map): back arrow + title + optional overflow.
- **`modal`** (full-screen dialogs/sheets): title + close.

Per-screen application:

| Screen | Variant | Trailing |
|---|---|---|
| `HomeScreen` | largeTitle | avatar + bell |
| `CalendarScreen` | largeTitle | "Today" + bell |
| `ChatScreen` (list) | largeTitle | new-chat + bell |
| `FamilyScreen` | largeTitle | overflow (admin) |
| `ProfileScreen` | largeTitle | — |
| `Shopping/Meal/Wishlist/Birthday/Map/ConversationScreen` | detail | overflow where needed |
| `ProfileEditScreen`, `SettingsScreen` | detail | — |

## 3. Notifications affordance
- Add a bell icon (with unread dot) to top-level `largeTitle` bars. Wire to a notifications
  destination or sheet (can be a stub list in D3, populated later). Improves the "modern app"
  feel and gives push notifications (already built) an in-app home.

## 4. Transitions (confirm M18 still holds)
- Keep iOS-style horizontal slide for detail screens, fade between bottom tabs. Verify after the
  top-bar refactor that no screen regressed.

## Acceptance criteria (D3)
- [ ] Every top-level screen uses `largeTitle`; every detail screen uses `detail`.
- [ ] Tab set finalized (user-confirmed); buried features reachable from Home feed + chips.
- [ ] Bell affordance present on top-level bars.
- [ ] Slide/fade transitions verified intact.
- [ ] Builds clean; Spotless/lint clean.
