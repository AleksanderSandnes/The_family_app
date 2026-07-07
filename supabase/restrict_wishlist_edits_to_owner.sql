-- Only the wishlist's creator (owner) may change its name/icon or delete it. The single
-- FOR ALL policy previously let any family member update/delete the wishlist row. Split it:
-- family members keep SELECT (to view) — wishes/reservations are governed by their own
-- tables — but INSERT/UPDATE/DELETE of the wishlist row are owner-only.
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
  using (owner_user_id = public.my_app_user_id());
