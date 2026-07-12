-- Per-message edit + delete (own messages only). Adds an edited marker and splits the
-- participant-scoped FOR ALL policy so members can read all messages and insert their own,
-- but update/delete only what they sent. Safe to re-run.

alter table public.messages
  add column if not exists edited_at timestamptz;

-- Realtime DELETE/UPDATE events need the full old record so per-conversation filters match
-- and clients can remove the right row (default replica identity ships only the PK).
alter table public.messages replica identity full;

drop policy if exists "messages_access" on public.messages;
drop policy if exists messages_select on public.messages;
drop policy if exists messages_insert on public.messages;
drop policy if exists messages_update on public.messages;
drop policy if exists messages_delete on public.messages;

create policy messages_select on public.messages
  for select using (
    public.is_conversation_member(conversation_id, public.my_app_user_id())
  );

create policy messages_insert on public.messages
  for insert with check (
    user_from = public.my_app_user_id()
    and public.is_conversation_member(conversation_id, public.my_app_user_id())
  );

create policy messages_update on public.messages
  for update
  using (user_from = public.my_app_user_id())
  with check (user_from = public.my_app_user_id());

create policy messages_delete on public.messages
  for delete using (user_from = public.my_app_user_id());
