-- conversation_participants was missing an UPDATE policy, so with RLS enabled every
-- UPDATE (notably markConversationRead setting last_read_at) was silently denied. That
-- left last_read_at permanently NULL, so the unread badge counted every message from
-- others forever and reading a chat never cleared it. Allow a user to update their own
-- participant row (their last_read_at) — same app-user resolution as the other policies.
drop policy if exists conv_participants_update on public.conversation_participants;
create policy conv_participants_update on public.conversation_participants
  for update
  using (
    user_id = (select id from public.users where auth_id = auth.uid() limit 1)
  )
  with check (
    user_id = (select id from public.users where auth_id = auth.uid() limit 1)
  );
