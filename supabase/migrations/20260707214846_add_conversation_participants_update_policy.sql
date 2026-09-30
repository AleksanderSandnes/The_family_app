drop policy if exists conv_participants_update on public.conversation_participants;
create policy conv_participants_update on public.conversation_participants
  for update
  using (
    user_id = (select id from public.users where auth_id = auth.uid() limit 1)
  )
  with check (
    user_id = (select id from public.users where auth_id = auth.uid() limit 1)
  );;
