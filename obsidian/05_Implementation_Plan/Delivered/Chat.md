# Delivered — Chat

All chat delivery: Messenger-style UI, participant model, and the shared-ViewModel fixes. Part of
[[../Implementation Plan]].

## Milestone 20 — Messenger-style layout & UX polish ✅
- **Grouping:** consecutive same-sender messages grouped; incoming show a 36dp avatar aligned to
  the last message in a group + sender name above the first; outgoing right-aligned, no avatar,
  gradient bubble with a tail only on the last in a group.
- **Auto-scroll:** jump to bottom on open, follow new messages only when already at bottom;
  "scroll to bottom" FAB when scrolled up.
- **Timestamps:** tap a bubble to toggle the send time (10-min separators between groups).
- **Swipe-to-reply:** swipe right → reply strip + quoted bubble inside the reply. `MessageModel`
  gains `replyToId`; SQL `add_message_reply.sql`.
- **IME:** `Modifier.imePadding()` + auto-scroll on keyboard open.
- **Cache busting:** `?t=<millis>` on group/profile image URLs (Coil caches by URL).
- `userProfiles` map in `ChatViewModel` (batch `getFamilyMembers`) drives avatars/names.

## Milestone 21 — participant-based conversations ✅
Replaced `conversations.user_from/user_to` scoping with an explicit `conversation_participants`
junction table (`add_conversation_participants.sql`; `is_conversation_member()` SECURITY DEFINER to
avoid RLS recursion; backfills existing convos; RLS select/insert/delete; added to realtime
publication). `ChatViewModel` redesign: `currentParticipants`, `familyMembers`,
`conversationParticipants`, `navigateToConversation`. Create with member multi-select
(`NewConversationSheet`); `addMember` upgrades a 1:1 to a new group; `removeMember`/leave. List rows
derive name + avatar from participants (1:1 = other person; group = name or joined first names).
`leaveFamily` cleanup: solo conversations hard-deleted, participant rows removed from groups
(history preserved).

## Bug — deleting a conversation didn't update the list ✅
List (`ChatScreen`) and detail (`ConversationScreen`) each called `viewModel()`, so Navigation
scoped **two** `ChatViewModel` instances — the delete mutated the detail's state, the list kept the
stale row until an app restart. Fix: hoist one `chatVm` at `MainFlow()` scope and pass it to both
screens (matches every other feature's shared-VM pattern). Also reordered `deleteConversation` so
`loadConversations` completes before the pop-back navigation emit.

## Smaller chat fixes ✅
- **Message ordering:** `.order("sent_at", ASCENDING)` on load + send.
- **Double back-press:** hide the keyboard inside the send FAB `onClick` so one back navigates.
- **Chat list cards:** each row wrapped in a rounded `Surface` (16dp, 2dp elevation), `onClick`
  moved to the card.
