alter table public.messages
  add column if not exists edited_at timestamptz;

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
  for delete using (user_from = public.my_app_user_id());;
