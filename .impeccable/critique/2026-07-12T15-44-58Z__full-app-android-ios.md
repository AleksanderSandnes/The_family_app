---
target: entire ios and android project (re-run after polish+followups)
total_score: 28
p0_count: 0
p1_count: 3
timestamp: 2026-07-12T15-44-58Z
slug: full-app-android-ios
---
Method: dual-agent (A: design review sub-agent · B: deterministic evidence sub-agent), fresh instances with no knowledge of the previous run. Source-based (no emulator); detector ran, exit 0, no scannable files (HTML/CSS engine, native codebase). Synthesis corrected two assessment false positives against the code (wish-delete undo exists at `WishlistScreens.kt:292-303`; B's Text-regex missed interpolated literals A found manually).

# Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Skeletons/typing/seen/"Settings saved" strong; write failures now surface via snackbars; still no offline indicator |
| 2 | Match System / Real World | 3 | Warm family language; stored-English relation values leak into chat member rows |
| 3 | User Control and Freedom | 3 | Undo for item+wish deletes; confirms on lists/events/plans/member-removal — but `deleteWishlist` (container) and birthdays still swipe-destroy unprotected (`WishlistScreens.kt:178`, `BirthdayScreen.kt:153`) |
| 4 | Consistency and Standards | 3 | Token discipline excellent; creation flows split across 3 idioms (CreationSheet / AlertDialog / ModalBottomSheet); chat rows use alpha-based text colors |
| 5 | Error Prevention | 2 | ProfileEditScreen still requires ALL four fields to save (`ProfileScreens.kt:400`) — a user without a birthday can never fix their name |
| 6 | Recognition Rather Than Recall | 3 | Icons+labels, copyable code+QR — solid |
| 7 | Flexibility and Efficiency | 3 | Deep links, QR, PDF export, swipe + TalkBack custom actions, 3 calendar views |
| 8 | Aesthetic and Minimalist Design | 3 | Restrained; 8-item chat overflow menu and ~9-control EventDialog are the dense spots |
| 9 | Error Recovery | 3 | errorRes→snackbar now in 8/13 ViewModels (write failures no longer silent); but Home's error state hides the nav grid and instructs "pull to refresh" on a screen with no pull-to-refresh (`HomeScreen.kt:168-173`) |
| 10 | Help and Documentation | 2 | Onboarding explains itself; forgot-password remains a dead end pointing at a placeholder support address (owner-deferred) |
| **Total** | | **28/40** | **Good — substance improved; fresh review surfaced new flow breaks** |

# Anti-Patterns Verdict

**Still no.** The re-run independently confirms the system discipline: Badge Rule and Ghost Rule enforced, dual-HazeState glass, designed API<31 fallback. The mechanical scan now comes back dramatically cleaner than run 1: **0** hardcoded `Text("…")` literals in Android ui (was 22), **0** ungated infinite animations on either platform (was 4), iOS typography scales via UIFontMetrics (was fully fixed-size), EN/NB at 497/498 + 383/383, 0 console noise. Remaining seams: `PrimaryButton` defaults every primary to the brand gradient (`Components.kt:132`) — stretching the One Gradient Rule's "brand primary CTA" allowance to all primaries; the un-tokenized shadow color `0x141A3C` repeated 5× across iOS features; `support@familyapp.com` placeholder.

# What Changed Since Run 1 (verified fixed)

- i18n: 22 bare literals → 0; permissions onboarding, chat dialogs, map rationale, service notification all resourced; NB parity intact. TalkBack now hears Norwegian.
- Reduce Motion: 0 gates → all 4 infinite animations gated (Android helper + iOS environment).
- iOS Dynamic Type: fixed-size ramp → UIFontMetrics-scaled tokens.
- Destruction: 0 → 5 of 10 swipe sites protected (lists, events ×2, plans, member-removal) + undo on item and wish deletes.
- Silent failures: 1 → 8 of 13 ViewModels surface write errors as snackbars.
- Contrast tokens (CaptionDark, LiveGreenText, WeekAmberText) now clear AA; off-token reds/greens gone (16→1 Android hardcoded colors, and that 1 is data-driven).
- Glass House front door: auth + permissions now on the ambient wash with glass cards; gradient confined to badge + CTA.

# Priority Issues (current)

**[P1] Home's error state deletes navigation and lies about recovery.** On any load failure the `when` branch renders only the ErrorBanner — all six feature tiles (compile-time constants needing no data) vanish, and the banner says "Pull to refresh" but Android Home has no PullRefresh (`HomeScreen.kt:168-190`; iOS has `.refreshable`). Fix: always render the grid, banner above it, wrap in the existing `PullRefresh` component.

**[P1] Invite links are dead for the people they're for.** `familyapp://join?code=…` (`Routes.kt:35`) is inert for someone without the app installed — and that's exactly who receives it. Fix: HTTPS App Link / Universal Link with a store-redirect page; the code-as-fallback is already in the share message.

**[P1] Profile edit is un-savable without optional data.** `saveEnabled` requires name AND email AND mobile AND birthday (`ProfileScreens.kt:400`), though mobile/birthday are optional at registration and absent for Google sign-ups. Fix: gate on name + email only.

**[P2] The destruction model has two leftover gaps.** `deleteWishlist` — an entire shared wishlist — and birthday entries still swipe-delete with no confirmation (`WishlistScreens.kt:178`, `BirthdayScreen.kt:153`), while their sibling containers all confirm. (Wish rows DO have undo — both assessments initially missed it.)

**[P2] ~12 hardcoded English fragments remain**, hiding in interpolations and named args the regex scans miss: chat ("Chat" fallback ×2, "📷 Photo"/"🎤 Voice message", "Rename" confirm, "(You)"/"Leave"/"Remove", "Members (N)": `ChatScreens.kt:429,435,731-732,1045,1295,1301,1850`), a11y builders (`CalendarScreen.kt:617,1020`, `FamilyScreen.kt:682`, `SettingsScreen.kt:393`), and two ChatViewModel error events (`ChatViewModel.kt:820,945`).

# Persona Red Flags (current)

- **Casey:** Calendar "+" and Chat compose live top-right while other features use bottom FABs — the two most frequent creates are the least thumb-reachable, inconsistently.
- **Jordan:** the dead deep link is red flag #1; after install, the "No family yet" banner's quiet TextButton competes with six glossy empty tiles — the one required action has the least visual weight (`HomeScreen.kt:511-543`).
- **Emma, 10:** "Delete conversation" (for everyone) sits in an always-present menu with adult-register confirm copy; hold-to-record has no hint — a tap does nothing and reads as broken.
- **Kari, 68:** calendar day numbers in fixed 34dp circles clip at large font scale (`CalendarScreen.kt:653-676`); chat timestamps at 50-55% alpha undercut the app's own AA doctrine; FeatureTile titles ellipsize at maxLines=1.

# Minor Observations

Chat list uses a spinner where others use ListSkeleton · NewConversationSheet's stock M3 Button is off-vocabulary · iOS SheetHeader hardcodes "Cancel"/"Create" defaults · "Settings saved" snackbar fires on every chip tap (iOS has no equivalent) · wishlist share redemption lands on the list screen on Android but deep-pushes the detail on iOS · avatar-overlap z-ordering differs between EventPeopleRow and Family stack · BirthdayPickerField's hand-rolled "30 years ago" math drifts ~7 days.

# Questions to Consider

1. Who is allowed to destroy shared things? Admin currently gates only the family photo and member removal — is "everyone can delete everything, behind a dialog" a designed family trust model or a default?
2. Is the Home summary the product, or is the grid? The glanceable answer renders below a 2-row hero and vanishes when empty.
3. The four flow breaks above are all plumbing, not paint. If the next design week goes to glass refinement again, who is the Glass House for?
