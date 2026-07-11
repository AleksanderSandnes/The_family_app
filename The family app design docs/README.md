# Design reference — current state

**Source of truth for the Android ⇄ iOS parity work.** The `ios/` folder holds 70
screenshots captured from the **live iOS app** (2026-07-11), in both **light and dark**
mode. These supersede the older loose `*.jpeg` reference images (deleted 2026-07-11) — the
iOS app moved on and those were stale.

Read the iOS **source** (`ios/FamilyApp/Features/…`) alongside these shots when matching a
screen; screenshots show the look, the code shows the exact strings/behaviour. EN/NB copy
lives in the localization files, not in these images.

## Folders
- `ios/` — iOS reference screenshots (`.HEIC`), named `<feature>-<screen>[-variant]-<light|dark>`.
- `android/` — emulator screenshots of the **fixed** Android screens (both light and dark),
  captured 2026-07-11 after the post-parity design pass; same naming scheme as `ios/`.

## Inventory (iOS, current)

### Home
- `home-dashboard-{light,dark}` — greeting, family card, Today card, Quick Access grid

### Shopping
- `shopping-lists-{light,dark}`, `shopping-lists-with-progress-light`
- `shopping-new-list-dialog-{light,dark}`
- `shopping-list-detail-empty-light`, `shopping-list-detail-with-items-light`, `shopping-list-detail-completed-dark`
- `shopping-list-detail-overflow-menu-light` (Rename list / Change icon)

### Meal planner
- `meal-new-plan-sheet-{light,dark}` (name / icon / colour / Starts / Ends)
- `meal-plan-week-detail-{light,dark}`, `meal-plan-inline-edit-light`
- `meal-plan-overflow-menu-light` (Rename plan / Change icon)

### Calendar
- `calendar-month-{light,dark}`, `calendar-month-with-event-{light,dark}`, `calendar-week-with-event-dark`
- `calendar-new-event-sheet-{light,dark}`, `calendar-edit-event-sheet-light`
- `calendar-going-with-picker-{light,dark}` (attendees)

### Birthdays
- `birthdays-list-{light,dark}` (+ `birthdays-list-dimmed-light`)
- `birthdays-add-birthday-sheet-{light,dark}`, `birthdays-edit-birthday-sheet-light`

### Wishlists
- `wishlists-list-{light,dark}`, `wishlists-new-wishlist-dialog-light`
- `wishlist-detail-{light,dark}`, `wishlist-detail-with-wish-light`, `wishlist-detail-empty-dark`
- `wishlist-add-wish-sheet-{light,dark}`, `wishlist-edit-wish-sheet-{light,dark}`
- `wishlist-detail-overflow-menu-light` (Export PDF / Share link / Rename / Change icon)

### Family
- `family-overview-{light,dark}` (invite code, Share invite, QR code, members)
- `family-member-popup-light`, `family-relation-picker-light` (directional relations)
- `family-qr-code-light`, `family-share-invite-sheet-light`, `family-invite-code-copied-light`
- `family-change-photo-menu-light`

### Family Map
- `family-map-{light,dark}` (avatar pins)

### Chat
- `chats-list-{light,dark}`, `chats-new-conversation-{light,dark}`
- `conversation-media-{light,dark}`, `conversation-voice-light`, `conversation-voice-and-media-light`
- `conversation-overflow-menu-light` (Change image / Delete conversation)

### Profile / Settings
- `profile-{light,dark}`, `edit-profile-{light,dark}`
- `settings-{light,dark}` (Appearance / Notifications / Privacy / Language)

_Not re-screenshotted (read the code): Auth/login, Permissions onboarding._
