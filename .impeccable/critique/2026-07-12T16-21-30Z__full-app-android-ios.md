---
target: entire ios and android project (run 3)
total_score: 29
p0_count: 0
p1_count: 3
timestamp: 2026-07-12T16-21-30Z
slug: full-app-android-ios
---
Method: dual-agent (A: design review · B: deterministic evidence), fresh isolated instances; A instructed to verify claims against ViewModels before reporting. Source-based; detector N/A (native codebase).

# Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Skeletons/typing/seen/upload states strong; but the map never shows your own sharing state, and a cancelled Google sign-in leaves `loading=true` forever (form disabled) |
| 2 | Match System / Real World | 3 | Warm language; event times hardcode 24-h HH:mm for all locales; auth errors English-only |
| 3 | User Control and Freedom | 2 | Undo (items/wishes) + confirms (all containers) now consistent — but no per-message delete/edit in chat, "Clear completed (N)" bulk-deletes with no confirm/undo, voice release always sends, sign-out is one unconfirmed tap |
| 4 | Consistency and Standards | 3 | Strong shared vocabulary; chat is the drift zone (stock M3 composer/button, literal paddings); rename uses two different idioms |
| 5 | Error Prevention | 3 | Confirm gating + date clamps good; `startRecording` shows a live recording UI even when MediaRecorder.start() threw |
| 6 | Recognition Rather Than Recall | 3 | Icon+label, QR invite; 18-option flat relation dropdown; long-press-only reactions |
| 7 | Flexibility and Efficiency | 3 | Deep links, QR, PDF, 3 calendar views, pull-to-refresh (except Calendar); no search |
| 8 | Aesthetic and Minimalist Design | 4 | Restrained palette, radius ladder, Badge/Ghost rules — coherent, non-template, verified across ~15 screens |
| 9 | Error Recovery | 3 | Home keeps nav grid + real pull-to-refresh; friendly auth-error mapping; failure snackbars lack retry actions |
| 10 | Help and Documentation | 2 | Empty states teach; forgot-password dead end with fictional support address remains (owner-deferred) |
| **Total** | | **29/40** | **Good — first score movement; remaining issues are flow-level** |

# Anti-Patterns Verdict

**No — and stronger than before.** Run 3's mechanical scan is the cleanest yet: destructive-action protection is now a consistent, fully-traced system (shared/coarse data → confirmation, own fine-grained rows → undo; 0 unprotected sites), 0 ungated animations, 0 hardcoded Text literals, 0 dp font sizes, EN/NB at 509/508. The reviewer's verdict: "designed by someone with a system." Remaining seams: **chat is the least-glass room** (stock composer, raw M3 button, literal paddings — the one screen that reads generic-Material); the fallback avatar color `0xFF6366F1` repeated ~8× as a magic number; iOS's shadow ink `0x141A3C` repeated 5× untokenized; 138 inline fixed iOS fonts still bypass the scaled tokens.

# What Changed Since Run 2 (verified)

- Score moved 28 → 29; Aesthetic reached 4/4.
- Destruction model: 9/9 sites protected (was 5/10) — B traced every site through screen AND ViewModel.
- Home error state: nav grid preserved + real pull-to-refresh affordance (was the top P1).
- Profile save gate fixed; day-cell a11y locale-aware; chat fragments localized.
- Run-2 strengths held: TalkBack custom actions, reduce-motion gating, no-yank scroll, EN/NB near-total.

# Priority Issues (current)

**[P1] Forgot password dead end with fictional support address** (`AuthScreens.kt:92-106`) — owner-deferred; flagged every run; the address `support@familyapp.com` is not controlled by the author.

**[P1] The map never shows your own sharing state, and visibility defaults off with the control three screens away.** `location_visible` defaults false; the legend deliberately excludes self; the toggle is in Settings → Privacy. A new family sees an empty map and "Not sharing" everywhere — the flagship feature reads broken, and an enabled user can't verify they're still broadcasting. Fix: own-status row at the top of the map legend with an inline toggle. (`FamilyMapScreen.kt:528-629`, `SessionManager.kt:61-63`)

**[P1] Voice recording: release always sends, cancel is physically unreachable one-handed, and a failed recorder still shows a live recording UI.** `onPress { start; tryAwaitRelease(); stop(send=true) }` — the cancel X needs a second finger; `isRecording=true` is set even when `MediaRecorder.start()` threw. Fix: slide-to-cancel, discard <1s recordings, set state only on successful start. (`ChatScreens.kt:529-568, 846-877`)

**[P2] Cancelled Google sign-in disables the whole login form forever** — `loading=true` is never reset if the user backs out of the browser. (`AuthViewModel.kt:57-64`)

**[P2] The in-app language switch leaves stale surfaces**: Calendar's formatters/weekday labels are top-level vals captured once per process; ~25 English strings live in ViewModels/utils (auth errors ~13, photo errors 6, image-viewer toasts 3, chat time labels "Yesterday"/"Active now", "You" fallbacks). (`CalendarScreen.kt:114-126`, `AuthViewModel.kt:153-171`, B section D)

**[P2] Sign-out and "Clear completed (N)" are the last unconfirmed destructive actions.** (`ProfileScreens.kt:227-232`, `ShoppingScreens.kt:368-388`)

**[P2] MealViewModel writes are mostly silent** despite having errorRes (11 bare runCatching: rename/icon/color/delete/day-edit); Shopping/Wishlist icon+color changes likewise; FamilyMapViewModel exposes no error state at all.

**[P3]** Avatar fallback `0xFF6366F1` ×8 → token; password-visibility toggle unlabeled; sub-48dp targets (Pickers 46dp tile, shopping send 46dp, meal 40dp IconButton; iOS 24-36pt toggles); Calendar lacks pull-to-refresh; `StepIndicator(totalSteps=3)` default vs 2-step flow; iOS `0x141A3C` shadow ×5 untokenized.

# Persona Red Flags (current)

- **Casey:** the mic hold-trap (P1); Calendar's create is top-right-only while other features use FABs; full-swipe on a list card is one dialog from deleting the family's list.
- **Jordan:** invite deep link inert without the app (deferred, needs domain); nothing in signup acknowledges the pending invite context; the six live-but-empty tiles compete with the "Get started" banner.
- **Emma, 10:** can delete any conversation for everyone (no roles in chat); "Clear completed" is instant; no unsend for her own messages. Shopping itself is genuinely kid-proof.
- **Kari, 68:** fixed-aspect Home tiles truncate NB titles at large font scale; day numbers clip in fixed 34dp circles; sheet confirm capsule is the smallest primary action in the app; sign-out sits unconfirmed below Settings.

# Questions to Consider

1. Who is the map for, if everyone is hidden by default? A first-open consent moment on the map itself would convert the privacy stance into a trust moment instead of an empty screen.
2. Does "one home, many rooms" survive contact with chat? The most-used room is the least glass — is chat the next design milestone?
3. What does a 10-year-old get to break? Admin gates only the family photo and member removal; everything else is trust-as-permission-model. Deliberate?
