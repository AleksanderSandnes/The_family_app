# D8 — Polish, Motion & Platform Extras

> Final layer: the extras that push the app from "great" to "flagship," plus a full closing QA.
> Depends on D1–D7. Audit refs: Calendar P2, C10, plus modern table-stakes (widgets).

---

## 1. Calendar views — `ui/calendar/CalendarScreen.kt`, `ui/calendar/CalendarViewModel.kt`
- Add a segmented **Month / Week / Agenda** toggle (Apple/Google Calendar standard). Month grid
  already solid (M13); add Week and a scrollable Agenda list.
- Render **multi-day events as bars** across the grid, and **color events by category/member**
  (use the D1 feature-accent palette). Add a category/color picker to the event dialog.
- Acceptance: three views switch smoothly; multi-day events span correctly; events are colored.

## 2. Gradient & visual cleanup (C10)
- Final sweep: confirm `brandGradient` appears only on the sanctioned identity surfaces (hero
  headers, outgoing chat bubbles, brand CTA, Home family banner). Remove any stray gradients.
- Calmer Settings section headers (already the benchmark — align everything else to it).

## 3. Platform extras (modern table-stakes)
- **Home-screen widget(s)** (Glance): "Next event" and/or "Shopping list" widget. High-visibility
  big-tech signal; pulls from the same Supabase data.
- **App icon + splash polish:** adaptive icon, themed splash screen (Android 12+ splash API).
- **Optional:** share-to-app target (share a link/text → "Add to wishlist/shopping").

## 4. Closing QA & re-audit (gates project completion)
- Full re-run of the heuristic audit in
  [[05_Implementation_Plan/Project - UI - UX - improvements]] Part 2 — confirm **no open
  P0/P1**.
- Cross-app visual QA against the **Settings screen benchmark** (the agreed quality bar).
- Accessibility sign-off: TalkBack on all 5 core journeys, 200% font, light + dark, contrast.
- Performance: cold-start, scroll jank, image loading on the map/chat.
- `./gradlew assembleDebug` + `./gradlew lint` + `./gradlew spotlessCheck` all clean.

## Acceptance criteria (D8)
- [ ] Calendar Month/Week/Agenda + colored/multi-day events.
- [ ] Gradient usage final and consistent; no stray gradients.
- [ ] At least one home-screen widget; polished app icon + splash.
- [ ] Re-audit shows zero open P0/P1; accessibility + visual QA signed off.
- [ ] All builds/lint/spotless clean on `upgrade/design_improvements`.
