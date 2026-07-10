# Project — UI / UX Improvements

> Discovery, heuristic audit, and implementation plan to take The Family App from
> "polished prototype" to "looks like a multi-billion-dollar company made it."
> Reviewer lens: a senior UX designer from Apple (Human Interface Guidelines rigor,
> clarity, deference, depth) + a UI designer from Linear (systematized, fast,
> opinionated, zero visual noise).
>
> Source material reviewed: 50+ flow screenshots in `~/dev/The family app design docs`,
> repo `README.md`, and the Obsidian vault (Project Overview, Feature Inventory,
> Architecture and Design, Implementation Plan through Milestone 22).

---

## Part 0 — How we plan (the pre-requisite planning process)

Before drawing a single new screen, we run a tight, time-boxed discovery loop. This is
the "pre-requisite planning" the brief asks for. Doing it in order prevents us from
polishing screens that shouldn't exist and re-skinning flows that should be restructured.

**Step 1 — Frame the product (½ day).**
Answer the three questions below (Part 1). Write them down. Every later decision is judged
against "does this serve our users and their jobs-to-be-done?"

**Step 2 — Map the experience, not just the screens (½ day).**
We already have the full screen inventory. Convert it into:
- a **navigation/IA map** (what's a tab, what's nested, what's a modal),
- the **top 5 user journeys** ("add tonight's dinner", "check who's home", "buy a gift
  someone wished for", "see what's happening this week", "add a birthday"),
- a **frequency × value** matrix to decide what earns prime real estate.

**Step 3 — Heuristic audit (1 day).**
Walk every screen against Nielsen's 10 heuristics + Apple HIG + WCAG 2.2 AA. Record each
issue with: *what, why it matters, severity, fix*. That's Part 2. Rank by impact, not by
how easy it is to fix.

**Step 4 — Define the design system FIRST (the real prerequisite).**
The single biggest lever is not any one screen — it's that the app has no enforced design
system, so every screen reinvents buttons, fields, FABs, and spacing. We define tokens
(color, type, spacing, radius, elevation, motion) and a component contract *before*
redesigning screens. This is Part 3 and Milestone 23. Everything else depends on it.

**Step 5 — Redesign in priority order, ship behind the system.**
Rebuild screens highest-impact-first (Part 4 milestones), each one consuming only design
tokens and shared components. No screen ships with a one-off color or spacing value.

**Step 6 — Validate.**
Dogfood with the 2-person family already using it; run a 5-person hallway test on the 5
core journeys; check accessibility with TalkBack + the largest font-scale + dark mode.
Iterate. Re-audit before calling a milestone done.

**Deliverables of the planning phase (all in this vault):**
1. Product framing (Part 1). 2. Ranked audit (Part 2). 3. Design-token + component
spec (Part 3). 4. Milestone plan with acceptance criteria (Part 4).

---

## Part 1 — Product framing

### Who are the users?
**Primary: members of a household/family who already live coordinated lives** — typically
2–6 people, mixed ages, mixed tech comfort. From the live data in the screenshots this is
a real two-person household ("Korallveien 26", Aleksander + Lotte). Concretely:

- **The organizer / admin** (power user). Creates the family, invites members, sets up
  shopping lists, meal plans, the calendar. Wants speed and control. Opens the app many
  times a day. Our "Linear power user."
- **The participant member.** Joins by code, checks the shared calendar, adds to the
  shopping list, replies in chat, glances at the map. Wants zero friction — the app must
  be obvious without a tutorial. Our "Apple first-time user."
- **Lower-frequency / lower-literacy members** (e.g. a teen, an older relative). Will use
  one or two features (chat, map, birthdays). Accessibility and clarity matter most here.

They are **not** enterprise users. They are not paying for productivity. They will abandon
the app the moment it feels like a chore or looks unfinished. Trust matters because the app
holds location, family relationships, and private messages.

### What does the app do?
**The Family App is one shared home for everyday household coordination.** A single
Android app (Kotlin + Compose + Material 3, Supabase backend) that bundles what families
otherwise spread across Notes, a group chat, a paper calendar, and Find My:

- **Shopping** — shared, collaborative checklists.
- **Meals** — plan the week's dinners.
- **Calendar** — shared family events with all-day + time ranges.
- **Birthdays** — never miss one; auto-added from member profiles, with countdown + age.
- **Wishlists** — gift ideas per family member.
- **Chat** — group + direct messages, images, voice notes, reactions, replies.
- **Family Map** — real-time location sharing with foreground/background tracking.
- **Profile / Settings / Family management** — invite codes, roles, appearance,
  notifications, location privacy.

Backed by live cloud sync (Supabase Postgres + Realtime + Storage + Auth) so everything is
multi-device and up to date in real time. The job-to-be-done: *"keep my family in sync
without nagging each other."*

### What are the biggest pain points?
Ranked by how much they hold the product back from feeling "big tech":

1. **No enforced design system.** Buttons, text fields, FABs, and spacing are reinvented
   per screen (gradient full-width vs text-only vs disabled-gray; outlined vs filled vs
   underlined fields; extended-pill vs circular FABs). This is the #1 reason it reads as
   "indie prototype" rather than "shipped by a design org." *Everything visual traces back
   to this.*
2. **The Home screen wastes prime real estate.** It's a pure navigation grid with large
   empty tiles. A flagship family app surfaces *glanceable, live* content here (today's
   meal, next event, items left to buy, who's home, next birthday). Right now the most
   valuable screen does the least.
3. **Empty, lifeless screens.** Lists with one item leave 70% of the screen blank with no
   empty-state guidance, no onboarding, no "here's how to start." First impression = unfinished.
4. **Information architecture is off.** Calendar gets a bottom tab; high-frequency Shopping,
   Meals, Map are buried one level deep behind the Home grid. Tab choice doesn't match usage.
5. **Accessibility gaps.** Low-contrast muted text and disabled CTAs (the gray "Sign in"
   reads permanently dead; white-on-light-gray fails WCAG), icon-only controls without
   visible labels, unverified touch-target sizes and font-scaling.
6. **Unfinished/inconsistent content & polish bugs.** Norwegian leftovers ("Ukeshandel",
   "Uke 37"), the Profile "Email"+value running together with no space, the map's "Unknown"
   address, awkward auto-naming ("X birthday"), email text wrapping mid-address.
7. **Missing "modern table-stakes" features** big-tech users now expect: social/Apple/Google
   sign-in or passkeys, share-sheet + QR for invites, search, swipe actions everywhere,
   richer wishlists (reserve/claim, price, link, image), read receipts/typing indicators,
   home widgets, haptics, skeleton loaders.

---

## Part 2 — Heuristic UX/UI audit (every screen, ranked by impact)

Severity scale: **P0** = breaks credibility or usability for everyone; **P1** = significant
friction or inconsistency; **P2** = noticeable polish gap; **P3** = nice-to-have.

### 2.1 Cross-cutting issues (affect many/all screens)

| # | Issue | Why it matters | Sev | Concrete redesign |
|---|---|---|---|---|
| C1 | **No design-token system; components reinvented per screen.** | Inconsistency is the strongest "amateur" signal. Apple/Linear win on relentless consistency. | **P0** | Build a token layer (Part 3) and one shared component set; refactor every screen onto it. |
| C2 | **Inconsistent primary buttons.** Gradient full-width (Profile, Sign out), text-only (dialogs), and gray "disabled-looking" primaries (Sign in, Add, Create). | Users can't learn "what's the main action." Gray primaries look broken. | **P0** | One `PrimaryButton` (filled brand, full-width on forms, 56dp, bold). Disabled = reduced opacity of the *same* fill, never a different gray that looks dead. Destructive = tonal red. |
| C3 | **Inconsistent text fields.** Outlined-floating-label (Profile edit, Calendar) vs rounded-filled (Auth, dialogs). | Same input, three looks. Breaks the "one system" feel. | **P1** | Standardize on one field style (recommend Material 3 outlined with floating label) everywhere, including dialogs. |
| C4 | **Inconsistent FABs.** Extended pill ("New list", "Add birthday", "New wishlist", "Create a meal plan") vs circular "+" (Calendar). | Same intent ("create"), two shapes/placements. | **P1** | Pick one rule: extended FAB with icon+label for the primary create action on every list screen; reserve circular only where space is tight. Identical placement/inset. |
| C5 | **No empty states anywhere.** | Blank screens read as broken/unfinished and give no path forward. | **P1** | Each feature gets an illustrated empty state: icon + one-line value prop + primary CTA ("Plan your first week", "Add your first list"). |
| C6 | **Inconsistent top bars.** Large title + avatar (Home) vs small left title (Calendar/Family/Profile) vs back+title (features); 3-dot present on some, not others. | Navigation feels stitched from different apps. | **P1** | Define 3 top-bar variants: (a) top-level large title + trailing avatar/notifications, (b) detail back+title+overflow, (c) modal. Apply consistently. |
| C7 | **Accessibility: contrast + targets + labels.** Muted placeholders, gray disabled CTAs, icon-only buttons (send, mic, copy, recenter), unverified 48dp targets & font scaling. | Excludes users; the gray Sign-in white text fails WCAG AA. Legal/quality bar for big tech. | **P0** | Token text colors to ≥4.5:1; never pure-gray-on-light primaries; `contentDescription` on every icon button; min 48×48dp targets; test at 200% font + TalkBack. |
| C8 | **Localization leftovers & content bugs.** "Ukeshandel", "Uke 37", "X birthday" auto-naming, map "Unknown". | One stray foreign word destroys the "shipped" illusion. | **P1** | Full English string sweep; reverse-geocode map to a real place/address; rename auto-birthdays to just the person's name. |
| C9 | **No haptics / no skeleton loaders / minimal motion.** | Premium apps *feel* responsive through micro-interactions. | **P2** | Add haptic feedback on key actions (check off item, send, long-press), skeleton placeholders instead of spinners, spring transitions. |
| C10 | **Gradient usage is ad-hoc.** Auth bg, Profile hero, Sign-out, chat bubbles, family banner — gradients appear inconsistently. | Decoration without a rule looks random. | **P2** | Decide gradient's *job* (brand identity surfaces only: hero headers, outgoing chat, brand CTA) and codify it as a token. |

### 2.2 Screen-by-screen findings

**Auth — Sign in** *(`auth-sign-in`)*
- **P0** "Sign in" CTA is gray with white text (disabled-on-empty) → reads permanently dead
  and fails contrast. *Fix:* enabled = filled brand; disabled = same fill at reduced opacity.
- **P1** No Google / Apple / passkey sign-in. Big-tech table stakes; also reduces password
  friction. *Fix:* add provider buttons + "or continue with email."
- **P2** Gradient background here but nowhere else in the app → disconnected. *Fix:* carry a
  subtle brand surface into onboarding/home, or codify gradient's role (C10).
- **Good:** clean lockup, tagline, password reveal, "Forgot password?", "Create account."

**Home dashboard** *(`home-dashboard`)*
- **P1 (high-value)** Prime screen is a static nav grid; tiles are huge and empty. *Fix:*
  convert to a **glanceable feed**: greeting + small avatar, then live cards — "Tonight:
  [meal]", "Next: [event] · [time]", "Shopping: 2 left", "[Name] turns 30 in 7 days",
  "2 family members home." Keep quick-access chips to features below the fold.
- **P2** Family banner crops user photo awkwardly; no last-activity. *Fix:* fixed-ratio cover
  with gradient scrim for legible text; show "active now" / member avatars row.
- **P2** No notifications/bell, no search. *Fix:* add trailing bell (with unread dot) on the
  large-title bar.
- **Good:** time-aware greeting + personalization; colorful feature icons give identity.

**Shopping — list of lists** *(`shopping-lists-list`)*
- **P1** Single card, ~70% empty, no item counts, no empty state. *Fix:* show per-list
  subtitle ("3 of 8 bought", member avatars), and an empty state when none exist.
- **P2** "Ukeshandel" (Norwegian). *Fix:* localize (and it's user data — but seed/sample copy
  should be English).

**Shopping — detail** *(`shopping-list-detail-with-items`)*
- **P2** No "completed" section; checked items just vanish (or stay?) — unclear. *Fix:* Apple
  Reminders pattern: checked items animate to a collapsible "Completed" group.
- **P2** "2 left" pill sits awkwardly between title and overflow. *Fix:* move count to a
  subtitle under the list title, or a quiet trailing badge.
- **Good:** clean Reminders-style rows; bottom add-item input with send is the right pattern.

**Meal planner — list** *(`meal-planner-list`)*
- **P1** Subtitle says "7 days planned" but only 1 day has a meal → **misleading data**.
  *Fix:* "1 of 7 days planned" + progress. "Uke 37" Norwegian + the week label doesn't match
  the date range shown (week numbering bug). *Fix:* derive the label from the date range.
- **P1** Empty space + single card; no empty state.

**Meal — week detail** *(`meal-plan-week-detail`, `...inline-edit`)*
- **P2** Every row carries a pencil icon *and* is tappable → redundant, visually noisy. *Fix:*
  make the whole row tap-to-edit; drop the pencil (or show on hover/swipe).
- **P2** Only one meal slot per day. Families plan breakfast/lunch/dinner. *Fix:* optional
  meal-type slots (collapsed by default to stay simple).
- **Good:** inline edit ("What's for Monday?") with Cancel/Save is fast and tidy.

**Calendar — month + event** *(`calendar-month-view-with-event`, `calendar-edit-event-dialog-with-time`)*
- **P2** Only a month view; no week/agenda. *Fix:* segmented Month/Week/Agenda toggle.
- **P2** Multi-day events won't render as bars across the grid; single dot only. *Fix:* event
  bars/colored dots, color by category/member.
- **P2** Edit-event dialog is busy: chip + field + many dividers, text-only Cancel/Save.
  *Fix:* cleaner sheet, grouped sections, standardized buttons (C2), color/category picker.
- **Good:** month grid with today ring, selected fill, event dots, "Today" jump, day-filtered
  list, circular "+" FAB — genuinely solid.

**Birthdays — list + add** *(`birthdays-list`, `birthdays-add-birthday-dialog`)*
- **P1** Auto-naming "Lotte Helland birthday" / "Aleksander Pleym sandnes birthday" is
  redundant. *Fix:* show just the person's name; the cake icon already says "birthday."
- **P2** Identical generic cake icons; no personalization. *Fix:* use the person's avatar with
  a small cake badge.
- **P2** Add dialog: "Add" primary looks disabled-gray (C2); button hierarchy weak.
- **Good:** great info density — countdown chip (amber when near), "Turning N" chip, sorted by
  next occurrence. This is the strongest list in the app.

**Wishlists — list + detail** *(`wishlists-list`, `wishlist-detail-with-wish`)*
- **P1** Wishes are plain checkbox rows — the *core* gifting mechanic is missing. A wishlist's
  value is **reserve/claim** (so two relatives don't buy the same gift), price, link, image.
  *Fix:* wish item = title + optional link/price/image; "Reserve" action visible to *others*
  but hidden from the wishlist owner (surprise-preserving). This is the single biggest feature
  upgrade opportunity.
- **P2** "By Lotte Helland" subtitle good; otherwise mirrors shopping (consistent — fine).
- **Note:** sample data is inappropriate ("twat") — scrub demo/seed content.

**Chat — conversation** *(`chat-conversation-media-and-voice`)*
- **P2** Header is name + 3-dot only; no avatar, no presence/last-active. *Fix:* avatar +
  "active now"/last-seen in header.
- **P2** No typing indicator, no read receipts (read receipts are on the README roadmap).
  *Fix:* add both — strong "modern messenger" signal.
- **P3** Reaction heart overlaps the bubble corner; fine but tune placement/elevation.
- **Good:** genuinely modern — gradient outgoing bubbles, image + voice messages with a
  branded player, reactions, swipe-to-reply, grouped messages, tap-for-timestamp. Best-built
  area of the app.

**Family — overview** *(`family-overview-members-invite-code`)*
- **P1** Invite is a raw hex code ("75965D3C") with copy only. *Fix:* one-tap **Share** (system
  share sheet with a deep link) + **QR code**; keep code as fallback. Massive onboarding win.
- **P1** Member email wraps mid-address ("…hotmail / .com") → looks broken. *Fix:* truncate
  with ellipsis or hide email (show role only); avatars + name + role is enough.
- **P2** No admin actions (remove member, transfer admin, rename family). *Fix:* admin overflow
  per member.
- **Good:** clean family card, overlapping avatars, role chips (Admin star), "Leave family"
  with confirm (per plan).

**Family Map** *(`family-map-member-location`)*
- **P1** Bottom "On the map" card shows location as **"Unknown"** and is partially clipped over
  Google controls. *Fix:* reverse-geocode to place/address + relative time ("updated 2 min
  ago"); lift the card above map controls (proper insets).
- **P2** Single huge member marker; no roster of all members; recenter FAB overlaps zoom
  controls. *Fix:* compact avatar pins; a bottom sheet listing every member with
  distance/last-update; reposition controls.
- **P2** No "last updated" / staleness indication on the pin. *Fix:* dim stale pins, show age.
- **Good:** real Google map, custom avatar/initial markers, my-location FAB, live updates.

**Profile** *(`profile-screen`)*
- **P0 (bug)** "Email" label and the address render with **no space** ("Emailaleksander…")
  and the long email overflows. *Fix:* label/value spacing + truncation/wrapping rules.
- **P2** Two nav rows (Edit profile, Settings) as cards is fine; gradient Sign-out is heavy
  for a destructive-ish action. *Fix:* make Sign out a quieter tonal/text style; reserve the
  big gradient for positive primary actions (C2/C10).
- **Good:** gradient hero with tappable avatar (camera badge), clean info card.

**Profile — edit** *(`profile-edit-personal-information`)*
- **P1** Uses outlined-floating-label fields — *different* from the rounded-filled fields used
  in Auth/dialogs (C3). *Fix:* unify field style app-wide.
- **Good:** required-field markers, birthday picker field, full-width gradient "Save changes."

**Settings — appearance / notifications / privacy** *(`settings-appearance`, `settings-notifications-privacy`)*
- **P2** Section labels in indigo caps are a touch loud; theme selector clipped at top when
  scrolled. *Fix:* calmer section headers (token `labelMedium`, muted); verify scroll insets.
- **Good:** the most "designed" screen — segmented theme picker, clean toggles with
  icon+title+subtitle, "Remind me" chip group, About card. Use this as the visual benchmark
  for the rest of the app.

**Dialogs & pickers** *(`*-new-list-dialog`, `*-icon-picker`, date/time pickers)*
- **P1** Dialog primaries ("Add"/"Create") render gray/disabled-looking (C2); buttons are
  text-only and right-aligned, inconsistent with full-screen forms.
- **P2** Icon pickers use small generic monochrome Material icons; selected = indigo bg. *Fix:*
  larger, friendlier icon set with color; consistent grid sizing.
- **Good:** date/time pickers use native Material 3 components (correct choice).

### 2.3 Top 10 fixes by impact (the order we'd actually work)
1. **Design-token + component system** (C1, C2, C3, C4) — unlocks everything. **P0**
2. **Accessibility pass** (C7) — contrast, labels, targets, font scaling. **P0**
3. **Profile email-spacing bug + content/localization sweep** (Profile P0, C8). **P0/P1**
4. **Home → glanceable live feed** (Home P1). **P1**
5. **Empty states + skeleton loaders everywhere** (C5, C9). **P1**
6. **Invite via share sheet + QR** (Family P1). **P1**
7. **IA / navigation rework** (C6 + tab choice). **P1**
8. **Wishlist reserve/claim mechanic** (Wishlist P1). **P1**
9. **Map: geocoding, member roster, freshness** (Map P1). **P1**
10. **Chat read receipts + typing + presence** (Chat P2). **P2**

---

## Part 3 — The prerequisite: design system foundation

This is the work that must land *before* screen redesigns. Defined once, in code, in
`ui/theme/` and `ui/components/`, and enforced.

**Design tokens (single source of truth):**
- **Color** — keep the indigo/violet brand. Define semantic roles, not raw hex, at call
  sites: `primary`, `onPrimary`, `surface`, `surfaceVariant`, `outline`, `success`,
  `warning`, `danger`, plus 8 feature accent colors (shopping/meals/calendar/…). Verify every
  pair ≥ 4.5:1 (text) / 3:1 (large text & icons) in light *and* dark.
- **Typography** — one type scale (display / title / body / label) mapped to Material 3
  `Typography`. No ad-hoc font sizes. Support Dynamic Type (sp + scaling tested to 200%).
- **Spacing** — 4-pt grid: 4/8/12/16/20/24/32. One screen-edge inset (20dp), one card padding
  (18–20dp). Codify so nothing is hand-tuned.
- **Radius** — settle on the values M17 already converged toward (cards 20dp, chips/full,
  fields 16dp). One token each.
- **Elevation/shadow** — 2 levels max (resting card, raised/FAB). Kill the per-screen drift.
- **Motion** — standard durations/easings (the M18 slide/fade) + spring for micro-interactions.
- **Gradient** — one brand gradient token with a defined job (C10).

**Component contract (the only building blocks screens may use):**
`PrimaryButton`, `SecondaryButton`, `DestructiveButton`, `AppTextField` (one style),
`AppFab` (extended + small variants), `AppTopBar` (3 variants), `ListCard`, `EmptyState`,
`SkeletonLoader`, `SectionHeader`, `Chip`, `AvatarCircle`, `Dialog`/`BottomSheet` shells.
Each takes tokens only — **zero literal colors/sizes at call sites.** Add a lint rule / review
checklist: a PR that hardcodes a color or dp is rejected.

**Reference benchmark:** the Settings screen is already at the target quality bar — every
other screen should match its calm, systematic feel.

---

## Part 4 — Implementation plan (milestones)

This design project runs on its **own milestone track (D1–D8)**, separate from the main
product milestones, and ships on a single branch: **`upgrade/design_improvements`** (it is
**not** merged to `test`/`master` until the user explicitly asks). Every milestone builds
clean (`./gradlew assembleDebug`) and ends with a re-audit + accessibility check. Sequencing
is dependency-ordered: the design system (D1) gates everything visual; do it first.

> **Detailed per-screen specs:** [[Design_Improvements/00 Design Improvement Plan]] — every
> milestone below is broken down screen-by-screen (current state → target → components →
> states → accessibility → acceptance) in the linked spec files.

### D1 — Design system foundation **[prerequisite, do first]** → [[Design_Improvements/D1 — Design System Foundation]]
- Implement tokens (color/type/spacing/radius/elevation/motion/gradient) in `ui/theme/`.
- Build/normalize the component set in `ui/components/` (Part 3 contract).
- Migrate **all** primary buttons, text fields, and FABs onto the shared components (kills
  C2/C3/C4 globally) — no visual redesign of layouts yet, just unify the atoms.
- **Acceptance:** no screen contains a hardcoded color or one-off button/field; light+dark
  verified; visual diff reviewed screen-by-screen.

### D2 — Accessibility & content correctness pass → [[Design_Improvements/D2 — Accessibility & Content Correctness]]
- Contrast tokens fixed; disabled-CTA pattern fixed (C2/C7).
- `contentDescription` on every icon-only control; 48dp min targets; 200% font + TalkBack pass.
- Profile email-spacing/overflow bug fixed.
- English string sweep (C8): "Ukeshandel"/"Uke 37"/auto-birthday naming; scrub demo data.
- **Acceptance:** automated contrast check passes; TalkBack walkthrough of 5 core journeys OK.

### D3 — Navigation & information architecture → [[Design_Improvements/D3 — Navigation & Information Architecture]]
- Re-decide tabs by frequency×value; standardize the 3 top-bar variants (C6).
- Add bell/notifications affordance + (optional) search on top-level large-title bars.
- **Acceptance:** every top-level and detail screen uses the correct top-bar variant; tab set
  reflects real usage; back/transition behavior consistent (builds on M18).

### D4 — Home as a glanceable live feed → [[Design_Improvements/D4 — Home Glanceable Feed]]
- Replace the static grid with live cards (tonight's meal, next event, shopping-left, next
  birthday, who's home) + quick-access chips; fixed-ratio family cover with scrim.
- **Acceptance:** Home shows real data from each feature with empty/loading states; opens in
  one glance without navigating.

### D5 — Empty states, skeletons & micro-interactions → [[Design_Improvements/D5 — Empty States, Skeletons & Micro-interactions]]
- `EmptyState` for every feature (illustration + value prop + CTA); `SkeletonLoader` replaces
  spinners; haptics on check/send/long-press; spring micro-animations (C5/C9).
- **Acceptance:** no blank screen and no bare spinner anywhere; haptics on key actions.

### D6 — Feature deep-dives (highest-value redesigns) → [[Design_Improvements/D6 — Feature Deep-Dives]]
Sub-tracked so each can ship independently:
- **D6a Family onboarding** — invite via system share sheet + QR; admin member management
  (remove/transfer/rename) (Family P1).
- **D6b Wishlist gifting** — reserve/claim (hidden from owner), link/price/image on wishes
  (Wishlist P1). *Requires a small schema add: `wishes.reserved_by`, `link`, `price`, `image_url`.*
- **D6c Map** — reverse-geocoding, member roster bottom sheet, last-updated freshness, control
  insets (Map P1).
- **D6d Meals & Shopping** — meal-type slots + accurate "x of 7 planned"; shopping "Completed"
  group + per-list progress/avatars (Meal/Shopping P1/P2).
- **Acceptance:** each sub-feature redesigned on the design system with empty/loading/error
  states and accessibility verified.

### D7 — Chat & auth modern table-stakes → [[Design_Improvements/D7 — Chat & Auth Table-Stakes]]
- Chat: read receipts + typing indicator + header presence/avatar (Chat P2; on README roadmap).
- Auth: Google/Apple sign-in or passkeys; carry brand surface into onboarding (Auth P1).
- **Acceptance:** receipts/typing live across two devices; at least one provider sign-in works.

### D8 — Polish, motion & platform extras → [[Design_Improvements/D8 — Polish, Motion & Platform Extras]]
- Calendar week/agenda views + event bars/colors; gradient-usage cleanup (C10).
- Optional: home-screen widget(s) (next event / shopping list), app-icon polish, splash.
- **Acceptance:** final cross-app visual QA against the Settings benchmark; full re-audit shows
  no open P0/P1.

---

## Appendix — original brief
> Create a plan on how to perform the pre-requisite planning to improve the UI/UX of our app.
> Look at the screenshots in `home/dev/The family app design docs`. Read the Obsidian vault and
> README. Goal: make the app look like a multi-billion-dollar company made it, with modern
> big-tech features — good, not overly complex solutions. Answer: who are the users / what does
> the app do / biggest pain points. Then, as a senior Apple UX + Linear UI designer, review
> every screen, identify every usability, consistency, and accessibility issue, explain why each
> matters, rank by impact, suggest concrete redesigns — and turn it all into an implementation plan.
