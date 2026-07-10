# D7 — Chat & Auth Modern Table-Stakes

> The "modern big-tech features" the brief asks for, in the two areas users judge most: the
> messenger and the front door. Depends on D1. Audit refs: Chat P2, Auth P1/P2; README roadmap
> (read receipts).

---

## A. Chat — `ui/chat/ChatScreens.kt`, `ui/chat/ChatViewModel.kt`, `data/Entities.kt`
Chat is already the best-built area (gradient bubbles, images, voice, reactions, swipe-to-reply,
grouping). Add the three signals that make it feel like a 2025 messenger:

### A1. Header presence & avatar (Chat P2)
- Conversation `AppTopBar.detail` shows the other person's **avatar** + name + "active now" /
  "last seen {time}" (1:1), or member count (group).
- Requires a lightweight presence signal: a `last_active_at` on `users` updated on app
  foreground, or Supabase Realtime presence on the conversation channel.

### A2. Typing indicator
- Broadcast "typing" over the conversation's Realtime channel (ephemeral, not persisted); show
  the animated three-dot bubble while another participant types.

### A3. Read receipts (README roadmap)
- Track last-read per participant. Schema:
```sql
alter table conversation_participants add column if not exists last_read_at timestamptz;
```
- On opening/scrolling to bottom, update `last_read_at`; show a small "Seen" / read avatar under
  the last outgoing message (Messenger style). Start with 1:1; groups show "Seen by N".

### Chat acceptance
- [ ] Header shows avatar + presence; typing indicator works across two devices; read receipts
      update and render on the last outgoing message.

---

## B. Auth — `ui/auth/AuthScreens.kt`, `ui/auth/AuthViewModel.kt`,
`data/FamilyRepository.kt`, `data/remote/SupabaseManager.kt`

### B1. Social / passkey sign-in (Auth P1)
- Add **Google sign-in** (and Apple if feasible) via Supabase Auth OAuth, and/or **passkeys**.
  Layout: provider buttons above an "or continue with email" divider, then the existing
  email/password form.
- Reduces password friction — a clear big-tech table-stake.

### B2. Brand surface continuity (Auth P2)
- The gradient auth background is currently disconnected from the rest of the app. Either carry a
  subtle brand surface into onboarding/home, or codify the gradient per D1's `brandGradient` rule
  so auth's gradient reads as intentional brand identity, not a one-off.

### B3. Apply D1 atoms
- "Sign in"/"Create account" → `PrimaryButton` (fixes the dead-gray disabled CTA, Auth P0 — also
  covered in D2 but verify here); fields → `AppTextField`. Keep the existing multi-step register,
  password strength, and step indicator (from old M23 PR #7) but on the new components.

### Auth acceptance
- [ ] At least one provider sign-in (Google) works end-to-end via Supabase Auth.
- [ ] Disabled CTA uses the D1 pattern; brand gradient usage is intentional/consistent.

## Acceptance criteria (D7)
- [ ] Chat A1–A3 delivered and verified on two devices.
- [ ] Auth B1–B3 delivered; migrations applied; build + lint clean; light+dark verified.
