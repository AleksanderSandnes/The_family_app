---
target: entire ios and android project
total_score: 28
p0_count: 0
p1_count: 4
timestamp: 2026-07-12T14-31-13Z
slug: full-app-android-ios
---
Method: dual-agent (A: design review sub-agent · B: deterministic evidence sub-agent). Source-based: no emulator/simulator available, so no on-device screenshots; detector (`detect.mjs`) ran with exit 0 / no scannable files (HTML/CSS engine, native Kotlin/Swift codebase) — recorded, not skipped. No browser overlay applies.

# Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Skeletons/typing/Seen/presence are real; but most writes are optimistic with no failure signal outside chat (`ChatScreens.kt:407-409` is the only errorEvent snackbar) |
| 2 | Match System / Real World | 3 | Warm plain copy; but NB users hit raw English mid-flow in chat, onboarding, dialogs |
| 3 | User Control and Freedom | 2 | No undo anywhere; whole shopping lists/events/wishes delete on a swipe with zero confirmation (`Components.kt:672-687`) |
| 4 | Consistency and Standards | 3 | Strong system; minor drift (off-token reds `AppNavHost.kt:233`, solid `NoFamilyBanner` amid glass) |
| 5 | Error Prevention | 2 | Container deletes unconfirmed; ProfileEdit blocks save unless all 4 fields filled (`ProfileScreens.kt:394`) |
| 6 | Recognition Rather Than Recall | 4 | Invite code copy+QR+share, badges as wayfinding, colors carried picker→dot→card — solid |
| 7 | Flexibility and Efficiency | 3 | Swipe-reply, hold-to-record, inline rename, deep links; no search anywhere |
| 8 | Aesthetic and Minimalist Design | 4 | Genuinely restrained; summary-first Home meets "calm over clamor" |
| 9 | Error Recovery | 2 | `runCatching` swallows failures repo-wide (219 uses, only 28 handled); optimistic deletes never roll back; no retry affordances |
| 10 | Help and Documentation | 2 | Empty states teach (good); "Forgot password" is a "coming soon" dead end (`AuthScreens.kt:93-107`) |
| **Total** | | **28/40** | **Good — solid foundation, address weak areas** |

# Anti-Patterns Verdict

**Does this look AI-generated? No.** Both assessments agree this reads as a designed system executed with unusual discipline. The DESIGN.md named rules are enforced in code, not just claimed: feature hues genuinely live only in badges and dots (Badge Rule, `GlassComponents.kt:23-54`), the brand gradient appears only on sanctioned identity surfaces (`Color.kt:121-126` documents this in the token itself), and the Ghost Rule is a load-bearing state language (`Glass.kt:228-261`).

**Deterministic scan**: detector N/A on native code (exit 0, no scannable files). Mechanical greps found the residue the LLM review corroborates: 16 hardcoded `Color(0xFF…)` in Android ui/** outside theme + 20 hex inits in iOS Features/** (mostly duplicating theme tokens by value — drift in mechanism, not palette); 5 Android + 2 iOS gradients built inline instead of referencing the theme brush (`FamilyScreen.kt:562` rebuilds the brand gradient with raw hex); 31 literal `RoundedCornerShape(N.dp)` outside theme; token-discipline outliers `ChatScreens.kt` (1 token ref vs 132 raw dp literals) and `CalendarScreen.kt` (17 vs 50). Two dead components (`ListCard`, `WaveformPlaceholder`) are the classic "generated and never wired" fingerprint.

**Where the detector-style pass caught what the design review missed**: the entire iOS type ramp (`DesignSystem/Typography.swift:5-14`) is fixed `Font.system(size:)` with no `relativeTo:` — iOS text does not scale with Dynamic Type at all, plus 138 inline fixed sizes at call sites vs 45 Dynamic Type styles. Zero Reduce Motion checks on either platform. Zero console noise (clean), ~95% string resourcing on Android, exact 383/383 EN↔NB parity on iOS (strong).

# Overall Impression

This is an authored product, not a template: the Glass House system is real, cross-platform, and disciplined, and the peak moments (wishlist secrecy, ghost completion states) are genuinely designed. What holds it at 28/40 is not vision but protection and recovery: the app is calm until something goes wrong, and then it is silent. The single biggest opportunity: make destruction and failure as designed as success — undo/confirm on shared-data deletes and a failure signal for optimistic writes.

# What's Working

1. **A real cross-platform design system.** `Glass.kt` ↔ `Glass.swift` implement identical recipes (same 0.36/0.04 ghost fills, same [4,3] dash). The dual-HazeState architecture (`Glass.kt:58-64`) solving blur self-sampling is expert Compose work.
2. **State design as brand.** The Ghost Rule makes doneness a material property — completed lists, checked items, and others' reserved wishes all recede identically (`ShoppingScreens.kt:199`, `WishlistScreens.kt:562`).
3. **Crafted non-happy-paths.** Content-shaped skeletons (`Components.kt:829-844`), scrollable empty states, avatar fallback to initials, real a11y semantics on tiles/day cells/switches.

# Priority Issues

**[P1] Hardcoded English defeats the NB localization already paid for.**
Why: a Norwegian family sees English at their first screen (the entire `PermissionsOnboardingScreen.kt:154-311`) and throughout chat (`ChatScreens.kt:929-1817`: rename/delete dialogs, member sheets, "Seen", "Release to send"), family dialogs (`FamilyScreen.kt:354,514,941-962`), calendar internals (`CalendarScreen.kt:114` "Mo Tu We…", `:450` "Upcoming", `:744` "All day"), and the location rationale (`FamilyMapScreen.kt:158-187`). B counts 22 bare `Text("…")` literals on Android against 386 stringResource uses; resource files have full key parity, so this is pure extraction work. iOS mixes `L(...)` with raw literals (`MessageRow.swift:66` "Seen", interpolated strings in `WishlistScreens.swift:99`).
Fix: mechanical extraction to existing resource files on both platforms.
Suggested command: /impeccable clarify (as part of /impeccable polish).

**[P1] Unconfirmed, un-undoable destruction of shared family data.**
Why: `SwipeToRevealDelete` commits instantly on tap or full swipe (`Components.kt:672-687`) and wraps whole shopping lists (`ShoppingScreens.kt:150`), calendar events (`CalendarScreen.kt:371,458`), wishes, and meal plans — one member's fling destroys everyone's data with no rollback. The risk model is inverted: deleting a conversation IS confirmed, a 40-item list is not. `ConfirmationDialog` already exists (`Components.kt:847-869`) — it's just not called here.
Fix: confirmation for container deletes; snackbar-undo (soft window) for row-level deletes.
Suggested command: /impeccable polish (+ /impeccable harden for the rollback path).

**[P1] Text does not survive large font sizes — on either platform, differently.**
Why: PRODUCT.md commits to WCAG AA. Android: sp-scaled text inside dp-fixed boxes clips at 1.5–2× — tab bar `height(64.dp)` (`AppNavHost.kt:208`), add-item field `46.dp` (`ShoppingScreens.kt:425`), sheet confirm capsule `34.dp` (`Sheets.kt:89`), `DateTimeRow` chips (`CalendarScreen.kt:1008-1034`). iOS: the whole type system is fixed-size (`Typography.swift:5-14`, no `relativeTo:`) so Dynamic Type does nothing, plus 138 inline `.font(.system(size:))`.
Fix: Android — `heightIn(min=…)` for text-bearing chrome; iOS — add `relativeTo:` to the token ramp and migrate call sites to tokens.
Suggested command: /impeccable adapt (Android quick wins fit in /impeccable polish; the iOS type migration is its own pass).

**[P1] "Forgot password" is a dead end at the highest-anxiety moment.**
Why: `AuthScreens.kt:93-107` ships "coming soon, contact support" for the #1 account-recovery path of a public store release. Supabase supports `resetPasswordForEmail` and the app already handles `familyapp://auth` deep links.
Fix: implement the email reset flow; until then remove the link rather than advertise a dead feature.
Suggested command: /impeccable harden.

**[P2] Optimistic UI with silent failure.**
Why: writes update local state, fire `runCatching`-wrapped calls (219 occurrences, only 28 results handled; iOS 48 `try?` in ViewModels), and only chat surfaces errors. An item added in a dead spot at the store appears, then silently vanishes — the exact mid-errand moment PRODUCT.md optimizes for.
Fix: shared errorEvent→snackbar channel in ViewModels, matching chat's existing pattern.
Suggested command: /impeccable harden.

# Persona Red Flags

**Casey (distracted, one-handed):** checking off a mid-list item yanks the list to the bottom every time the active count changes (`ShoppingScreens.kt:263-265` — a scroll effect meant for adds that also fires on removals); a distracted fling past 55% row width deletes with only a haptic tick; voice notes send on finger-lift with no slide-to-cancel (`ChatScreens.kt:857-859`).

**Jordan (first-timer with an invite link):** the deep link routes correctly and post-join relations setup is warm — but between registering and joining stands a permissions wall demanding notifications + location + camera + mic before he's seen one screen of value (`PermissionsOnboardingScreen.kt:150-195`), in English on a Norwegian phone, with grammatically broken copy ("so everyone knows where each other are"). Unverified: whether a pending invite code survives the email-confirmation app switch (`AppNavHost.kt:126-142` doesn't re-surface it).

**Emma, 10 (shopping + chat):** shopping is genuinely kid-usable (huge tap circle, haptic, strike-through), but her swipe-play can permanently erase the family's list — the user class most likely to fling rows is least able to recover. Reactions/reply hide behind long-press/swipe with nothing teaching them.

**Kari, 68 (large fonts):** the fixed 64dp tab bar clips its five labels first, then the 34dp sheet confirm, the 46dp add-item bar, and calendar date chips. The 18-option relation dropdown (`FamilyScreen.kt:126-146,843-863`) is a flat unsorted scroll of small rows. `PillTag` counters clip in fixed-padding capsules.

# Minor Observations

- Reduce Motion: zero checks on both platforms (Android animator scale, iOS `accessibilityReduceMotion`).
- iOS Home shows a bare spinner where Android shows a skeleton (`HomeScreen.swift:83`) — reverse parity gap.
- Calendar is Monday-first hardcoded (`CalendarScreen.kt:658`); dates format as "MMMM d, yyyy" under NB locale (`ProfileScreens.kt:315`, `Components.kt:582`).
- Received-message text uses `onSurfaceVariant` for primary content (`ChatScreens.kt:1688`) — should be `onSurface`.
- Chat unread badge / recording UI use `Color(0xFFE53935)`; the token is Destructive `#E11D48` (`AppNavHost.kt:233`, `ChatScreens.kt:900,916`).
- Duplicate "Members" surfaces in chat (menu item vs sheet, `ChatScreens.kt:668-677` vs `1259-1303`).
- Home greeting `remember { timeBasedGreeting() }` never recomputes across a time boundary (`HomeScreen.kt:388`).
- Image message `contentDescription = "Image"` — untranslated and uninformative (`ChatScreens.kt:1630`).
- `EventDialog` silently resets dates if parse fails on edit (`CalendarScreen.kt:768-773`).
- Dead code: `ListCard` (`Components.kt:264`), `WaveformPlaceholder` (`ChatScreens.kt:1360`).
- `VoiceNoteMessage` play button is 36dp — the one confirmed sub-48dp interactive target (`VoiceNoteMessage.kt:139`).
- EventDialog packs 7 decision clusters into one sheet (`CalendarScreen.kt:750-929`); 18-option relation list and 12-15 icon grids exceed the ≤4-choices guidance.

# Questions to Consider

1. Why do the two screens that form first impressions — auth and permissions onboarding — abandon the Glass House entirely for full-bleed gradients and solid cards, precisely the "flooding large surfaces with hue" the One Gradient Rule prohibits?
2. Is Home a dashboard or a launcher? Shopping and Calendar exist as bottom tabs AND Home grid tiles routing to the same places. What would Home look like if the grid held only things not on the tab bar?
3. Whose mistakes does the app protect? It confirms removing a group photo but lets a 10-year-old's fling erase the family's shopping list. If "the app absorbs stress rather than adding it," the protection budget belongs exactly where shared, unrecoverable data meets the least careful fingers.
