drop policy if exists wishlists_access on public.wishlists;

create policy wishlists_select on public.wishlists
  for select
  using (
    owner_user_id = public.my_app_user_id()
    or family_id = public.my_family_id()
  );

create policy wishlists_insert on public.wishlists
  for insert
  with check (owner_user_id = public.my_app_user_id());

create policy wishlists_update on public.wishlists
  for update
  using (owner_user_id = public.my_app_user_id())
  with check (owner_user_id = public.my_app_user_id());

create policy wishlists_delete on public.wishlists
  for delete
  using (owner_user_id = public.my_app_user_id());;
