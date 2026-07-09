-- Fix: conversation_participants was missing GRANT UPDATE for the authenticated role.
-- fix_conversation_participants_grants.sql granted select/insert/delete but not update,
-- and add_conversation_participants_update_policy.sql added the RLS UPDATE *policy* — but
-- Postgres checks table-level privileges BEFORE RLS, so markConversationRead's UPDATE of
-- last_read_at was rejected with "permission denied" and swallowed by the app's try?.
-- Result: last_read_at stayed NULL for every participant, so the unread count fell back to
-- the epoch and re-counted every message on every launch (chat badge resurrected forever).
-- Safe to re-run.

-- 1. The actual fix: allow authenticated users to UPDATE (the RLS policy scopes it to
--    their own participant row).
grant update on public.conversation_participants to authenticated;

-- 2. One-time reset: every row currently has NULL last_read_at because the write never
--    worked. Backfill to now() so already-read chats stop showing as unread immediately;
--    genuinely new messages after this run will mark unread and clear correctly on open.
update public.conversation_participants
set last_read_at = now()
where last_read_at is null;
