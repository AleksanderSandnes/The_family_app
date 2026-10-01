alter table public.birthdays
  add column if not exists icon text not null default 'cake',
  add column if not exists color integer;

drop policy if exists birthdays_access on public.birthdays;

create policy birthdays_select on public.birthdays
  for select using (
    made_by_user_id = public.my_app_user_id()
    or family_id = public.my_family_id()
  );

create policy birthdays_insert on public.birthdays
  for insert with check (made_by_user_id = public.my_app_user_id());

create policy birthdays_update on public.birthdays
  for update
  using (made_by_user_id = public.my_app_user_id())
  with check (made_by_user_id = public.my_app_user_id());

create policy birthdays_delete on public.birthdays
  for delete using (made_by_user_id = public.my_app_user_id());;
